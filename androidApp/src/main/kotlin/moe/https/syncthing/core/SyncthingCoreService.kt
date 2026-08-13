package moe.https.syncthing.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.https.syncthing.MainActivity
import moe.https.syncthing.R
import moe.https.syncthing.SyncthingApplication
import kotlin.math.min
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds

class SyncthingCoreService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
    }
    private val runtime: CoreRuntime
        get() = (application as SyncthingApplication).coreRuntime

    private var supervisorJob: Job? = null
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            runtime.snapshot.collectLatest { snapshot ->
                if (foregroundStarted) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(snapshot))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestStop()
            ACTION_START -> requestStart()
            else -> {
                if (preferences.getBoolean(KEY_DESIRED_RUNNING, false)) {
                    requestStart()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun requestStart() {
        preferences.edit { putBoolean(KEY_DESIRED_RUNNING, true) }
        if (!foregroundStarted) {
            foregroundStarted = true
            startForeground(NOTIFICATION_ID, buildNotification(runtime.snapshot.value))
        }
        acquireWakeLock()

        if (supervisorJob?.isActive == true) return
        supervisorJob = serviceScope.launch {
            var failures = 0
            while (preferences.getBoolean(KEY_DESIRED_RUNNING, false)) {
                val result = try {
                    runtime.runSession()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    runtime.fail(
                        message = error.message ?: error.javaClass.simpleName,
                        error = error,
                        includeCoreLogs = true,
                    )
                    CoreRuntime.SessionResult(
                        started = false,
                        runtimeMillis = 0,
                        exitCode = null,
                    )
                }

                if (!preferences.getBoolean(KEY_DESIRED_RUNNING, false)) break

                failures = if (result.runtimeMillis >= STABLE_SESSION_MILLIS) 0 else failures + 1
                if (!result.started || failures >= MAX_RESTART_ATTEMPTS) {
                    preferences.edit { putBoolean(KEY_DESIRED_RUNNING, false) }
                    runtime.fail(
                        message = "核心连续启动失败，已停止自动重试",
                        includeCoreLogs = true,
                    )
                    break
                }

                val backoff = min(
                    INITIAL_BACKOFF_MILLIS * (1L shl (failures - 1).coerceAtLeast(0)),
                    MAX_BACKOFF_MILLIS,
                )
                delay(backoff.milliseconds)
            }

            releaseWakeLock()
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            stopSelf()
        }
    }

    private fun requestStop() {
        preferences.edit { putBoolean(KEY_DESIRED_RUNNING, false) }
        serviceScope.launch {
            val runningSupervisor = supervisorJob
            supervisorJob = null
            runningSupervisor?.cancelAndJoin()
            runtime.stop()
            releaseWakeLock()
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            stopSelf()
        }
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:SyncthingCore",
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_core),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持 Syncthing 核心在后台运行"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(snapshot: CoreSnapshot): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SyncthingCoreService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncthing 核心")
            .setContentText(snapshot.notificationText())
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "停止",
                    stopIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_START = "moe.https.syncthing.action.START_CORE"
        const val ACTION_STOP = "moe.https.syncthing.action.STOP_CORE"

        private const val PREFERENCES = "core_service"
        private const val KEY_DESIRED_RUNNING = "desired_running"
        private const val NOTIFICATION_CHANNEL_ID = "syncthing_core"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_RESTART_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MILLIS = 1_000L
        private const val MAX_BACKOFF_MILLIS = 30_000L
        private const val STABLE_SESSION_MILLIS = 60_000L
    }
}

private fun CoreSnapshot.notificationText(): String = when (state) {
    CoreState.RUNNING -> rssBytes?.let { "运行中 · ${it / 1024 / 1024} MiB" } ?: "运行中"
    CoreState.STARTING -> "正在启动"
    CoreState.STOPPING -> "正在停止"
    CoreState.FAILED -> "运行异常"
    else -> "等待启动"
}
