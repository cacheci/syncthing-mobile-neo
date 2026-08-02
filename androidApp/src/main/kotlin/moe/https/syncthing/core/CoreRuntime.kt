package moe.https.syncthing.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class CoreRuntime(
    context: Context,
    private val installer: CoreBinaryInstaller,
)
{
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val processMutex = Mutex()
    private val restClient = SyncthingRestClient(loadOrCreateApiKey())

    private val mutableSnapshot = MutableStateFlow(
        CoreSnapshot(
            state = if (installer.binaryFile.isFile) CoreState.STOPPED else CoreState.NOT_INSTALLED,
            version = installer.installedVersion,
        ),
    )
    val snapshot: StateFlow<CoreSnapshot> = mutableSnapshot.asStateFlow()

    @Volatile
    private var process: Process? = null

    suspend fun refreshInstallation() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true || restClient.ping()) {
            val status = runCatching { restClient.status() }
                .onFailure { error ->
                    logConnectionFailure(
                        context = "刷新核心运行状态失败",
                        error = error,
                        terminal = false,
                    )
                }
                .getOrNull()
            mutableSnapshot.update {
                it.copy(
                    state = CoreState.RUNNING,
                    version = installer.installedVersion,
                    uptimeSeconds = status?.uptimeSeconds,
                    rssBytes = readRssBytes(currentPid()),
                    allocatedBytes = status?.allocatedBytes,
                    systemBytes = status?.systemBytes,
                    goroutines = status?.goroutines,
                    lastError = null,
                )
            }
            return@withContext
        }
        mutableSnapshot.update {
            it.copy(
                state = if (installer.binaryFile.isFile) CoreState.STOPPED else CoreState.NOT_INSTALLED,
                version = installer.installedVersion,
                uptimeSeconds = null,
                rssBytes = null,
                allocatedBytes = null,
                systemBytes = null,
                goroutines = null,
            )
        }
    }

    suspend fun importCore(uri: Uri) {
        if (process?.isAlive == true || restClient.ping()) {
            fail("请先停止核心，再进行更新")
            return
        }
        if (!supportsArm64()) {
            fail("当前设备不是 arm64-v8a，无法使用此核心")
            return
        }

        mutableSnapshot.update { it.copy(state = CoreState.INSTALLING, lastError = null) }
        runCatching { installer.install(uri) }
            .onSuccess { version ->
                mutableSnapshot.value = CoreSnapshot(
                    state = CoreState.STOPPED,
                    version = version,
                )
                if (version.contains("linux-arm64", ignoreCase = true)) {
                    logWarning(
                        "已导入 linux-arm64 核心；该构建可能无法在 Android 上正确使用 DNS、网络接口、发现和中继功能，请优先使用 android-arm64 核心",
                    )
                } else {
                    logInfo("核心导入成功：${redact(version)}")
                }
            }
            .onFailure { error ->
                logError("核心导入失败", error)
                mutableSnapshot.update {
                    it.copy(
                        state = if (installer.binaryFile.isFile) CoreState.STOPPED else CoreState.NOT_INSTALLED,
                        lastError = error.userMessage(),
                    )
                }
            }
    }

    suspend fun runSession(): SessionResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (!installer.binaryFile.isFile) {
            fail("尚未导入 Syncthing 核心")
            return@withContext SessionResult(started = false, runtimeMillis = 0, exitCode = null)
        }
        if (!supportsArm64()) {
            fail("当前设备不是 arm64-v8a")
            return@withContext SessionResult(started = false, runtimeMillis = 0, exitCode = null)
        }
        installer.installedVersion
            ?.takeIf { it.contains("linux-arm64", ignoreCase = true) }
            ?.let {
                logWarning(
                    "当前核心为 linux-arm64 构建，Android 下 DNS、网络接口、发现和中继连接可能不可用",
                )
            }

        if (restClient.ping()) {
            mutableSnapshot.update {
                it.copy(state = CoreState.RUNNING, lastError = null)
            }
            monitorSession(process = null)
            return@withContext SessionResult(
                started = true,
                runtimeMillis = System.currentTimeMillis() - startedAt,
                exitCode = null,
            )
        }

        val launchedProcess = processMutex.withLock {
            process?.takeIf { it.isAlive } ?: launchProcess().also { process = it }
        }
        logInfo("核心进程已创建，开始连接 REST API：$REST_API_ADDRESS")

        mutableSnapshot.update {
            it.copy(
                state = CoreState.STARTING,
                lastError = null,
                uptimeSeconds = null,
                rssBytes = null,
                allocatedBytes = null,
                systemBytes = null,
                goroutines = null,
            )
        }

        val apiWaitResult = waitForApi(launchedProcess)
        if (!apiWaitResult.ready) {
            val exitCode = launchedProcess.exitCodeOrNull()
            val message = if (exitCode == null) {
                    "核心已启动，但 REST 接口未在规定时间内就绪" +
                        apiWaitResult.lastError?.let { "；最后错误：${connectionErrorSummary(it)}" }.orEmpty()
                } else {
                    "核心启动失败，退出码 $exitCode"
                }
            fail(
                message = message,
                includeCoreLogs = true,
            )
            launchedProcess.destroyForcibly()
            process = null
            return@withContext SessionResult(
                started = false,
                runtimeMillis = System.currentTimeMillis() - startedAt,
                exitCode = exitCode,
            )
        }

        mutableSnapshot.update { it.copy(state = CoreState.RUNNING, lastError = null) }
        logInfo("核心 REST 接口已就绪：$REST_API_ADDRESS")
        currentPid()
        monitorSession(launchedProcess)
        val exitCode = launchedProcess.exitCodeOrNull()
        process = null
        preferences.edit().remove(KEY_PID).apply()

        if (mutableSnapshot.value.state != CoreState.STOPPING) {
            fail(
                message = "核心意外退出${exitCode?.let { code -> "，退出码 $code" } ?: ""}",
                includeCoreLogs = true,
            )
        }

        SessionResult(
            started = true,
            runtimeMillis = System.currentTimeMillis() - startedAt,
            exitCode = exitCode,
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        mutableSnapshot.update { it.copy(state = CoreState.STOPPING, lastError = null) }
        logInfo("正在请求核心停止")
        runCatching { restClient.shutdown() }
            .onFailure { error ->
                logConnectionFailure(
                    context = "REST 正常关闭请求失败，将尝试终止进程",
                    error = error,
                    terminal = false,
                )
            }

        val currentProcess = process
        if (currentProcess != null) {
            if (!currentProcess.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                currentProcess.destroy()
            }
            if (currentProcess.isAlive && !currentProcess.waitFor(FORCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                currentProcess.destroyForcibly()
            }
        } else {
            for (attempt in 0 until STOP_POLL_COUNT) {
                if (!restClient.ping()) break
                delay(STOP_POLL_INTERVAL_MILLIS)
            }
            if (restClient.ping()) {
                killRememberedProcessIfOwned()
            }
        }

        process = null
        preferences.edit().remove(KEY_PID).apply()
        mutableSnapshot.value = CoreSnapshot(
            state = if (installer.binaryFile.isFile) CoreState.STOPPED else CoreState.NOT_INSTALLED,
            version = installer.installedVersion,
        )
        logInfo("核心已停止")
    }

    fun fail(
        message: String,
        error: Throwable? = null,
        includeCoreLogs: Boolean = false,
    ) {
        logError(message, error)
        if (includeCoreLogs) {
            logCoreLogTail()
        }
        mutableSnapshot.update {
            it.copy(
                state = CoreState.FAILED,
                uptimeSeconds = null,
                rssBytes = null,
                allocatedBytes = null,
                systemBytes = null,
                goroutines = null,
                lastError = message,
            )
        }
    }

    private fun launchProcess(): Process {
        val home = File(applicationContext.filesDir, "syncthing-home").apply { mkdirs() }
        val logs = File(applicationContext.filesDir, "logs").apply { mkdirs() }
        val apiKey = preferences.getString(KEY_API_KEY, null)
            ?: throw IOException("REST API 密钥不存在")

        return ProcessBuilder(
            installer.binaryFile.absolutePath,
            "serve",
            "--home=${home.absolutePath}",
            "--gui-address=127.0.0.1:8384",
            "--gui-apikey=$apiKey",
            "--no-browser",
            "--no-port-probing",
            "--no-restart",
            "--no-upgrade",
            "--log-file=${File(logs, "syncthing.log").absolutePath}",
            "--log-max-size=1048576",
            "--log-max-old-files=1",
        ).apply {
            environment()["HOME"] = applicationContext.filesDir.absolutePath
            environment()["STNOUPGRADE"] = "1"
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.to(File(logs, "launcher.log")))
        }.start()
    }

    private suspend fun waitForApi(currentProcess: Process): ApiWaitResult {
        val startedAt = SystemClock.elapsedRealtime()
        var lastError: Throwable? = null
        var lastSignature: String? = null
        repeat(API_READY_POLL_COUNT) { index ->
            val attempt = index + 1
            if (!currentProcess.isAlive) {
                logError("REST API 就绪前核心进程已退出，已尝试 $attempt 次")
                return ApiWaitResult(ready = false, lastError = lastError)
            }

            val result = runCatching { restClient.pingChecked() }
            if (result.isSuccess) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                logInfo("REST API 连接成功，尝试 $attempt 次，耗时 ${elapsed}ms")
                return ApiWaitResult(ready = true, lastError = null)
            }

            val error = result.exceptionOrNull() ?: IOException("未知 REST 连接错误")
            lastError = error
            val signature = connectionErrorSignature(error)
            if (attempt == 1 || attempt % CONNECTION_RETRY_LOG_INTERVAL == 0 || signature != lastSignature) {
                logConnectionFailure(
                    context = "REST API 尚未就绪（第 $attempt/$API_READY_POLL_COUNT 次）",
                    error = error,
                    terminal = false,
                )
            }
            lastSignature = signature
            delay(API_READY_POLL_INTERVAL_MILLIS)
        }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        logConnectionFailure(
            context = "REST API 连接超时，尝试 $API_READY_POLL_COUNT 次，耗时 ${elapsed}ms",
            error = lastError ?: IOException("未获得 REST 响应"),
            terminal = true,
        )
        return ApiWaitResult(ready = false, lastError = lastError)
    }

    private suspend fun monitorSession(process: Process?) {
        var consecutiveFailures = 0
        var lastSignature: String? = null
        while (currentCoroutineContext().isActive) {
            if (process != null && !process.isAlive) break

            runCatching { restClient.status() }
                .onSuccess { status ->
                    if (consecutiveFailures > 0) {
                        logInfo("REST 状态连接已恢复，此前连续失败 $consecutiveFailures 次")
                    }
                    consecutiveFailures = 0
                    lastSignature = null
                    mutableSnapshot.update {
                        it.copy(
                            state = CoreState.RUNNING,
                            uptimeSeconds = status.uptimeSeconds,
                            rssBytes = readRssBytes(currentPid()),
                            allocatedBytes = status.allocatedBytes,
                            systemBytes = status.systemBytes,
                            goroutines = status.goroutines,
                        )
                    }
                }
                .onFailure { error ->
                    consecutiveFailures += 1
                    val signature = connectionErrorSignature(error)
                    if (
                        consecutiveFailures == 1 ||
                        consecutiveFailures % STATUS_FAILURE_LOG_INTERVAL == 0 ||
                        signature != lastSignature
                    ) {
                        logConnectionFailure(
                            context = "REST 状态轮询失败（连续 $consecutiveFailures 次）",
                            error = error,
                            terminal = false,
                        )
                    }
                    lastSignature = signature
                }
            if (process == null && consecutiveFailures > 0) break
            delay(STATUS_POLL_INTERVAL_MILLIS)
        }
    }

    private fun currentPid(): Long? {
        val rememberedPid = preferences.getLong(KEY_PID, -1L).takeIf { it > 0 }
        if (rememberedPid != null && isCoreProcess(rememberedPid)) {
            return rememberedPid
        }
        return findCorePid()?.also { pid ->
            preferences.edit().putLong(KEY_PID, pid).apply()
        }
    }

    private fun findCorePid(): Long? = runCatching {
        File("/proc").listFiles()
            ?.asSequence()
            ?.filter { entry -> entry.isDirectory && entry.name.all(Char::isDigit) }
            ?.mapNotNull { entry -> entry.name.toLongOrNull() }
            ?.firstOrNull(::isCoreProcess)
    }.getOrNull()

    private fun isCoreProcess(pid: Long): Boolean = runCatching {
        File("/proc/$pid/cmdline")
            .readText()
            .substringBefore('\u0000') == installer.binaryFile.absolutePath
    }.getOrDefault(false)

    private fun readRssBytes(pid: Long?): Long? {
        if (pid == null) return null
        return runCatching {
            FileReader("/proc/$pid/status").buffered().useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?.times(1024)
            }
        }.getOrNull()
    }

    private fun killRememberedProcessIfOwned() {
        val pid = currentPid() ?: return
        if (isCoreProcess(pid)) {
            android.os.Process.killProcess(pid.toInt())
        }
    }

    private fun loadOrCreateApiKey(): String {
        preferences.getString(KEY_API_KEY, null)?.let { return it }
        val key = UUID.randomUUID().toString().replace("-", "")
        preferences.edit().putString(KEY_API_KEY, key).commit()
        return key
    }

    private fun supportsArm64(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    private fun logError(message: String, error: Throwable? = null) {
        writeControllerLog("ERROR", message, error)
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
    }

    private fun logInfo(message: String) {
        writeControllerLog("INFO", message)
        Log.i(TAG, message)
    }

    private fun logWarning(message: String, error: Throwable? = null) {
        writeControllerLog("WARN", message, error)
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    private fun logConnectionFailure(
        context: String,
        error: Throwable,
        terminal: Boolean,
    ) {
        val summary = "$context；${connectionErrorSummary(error)}"
        if (terminal || isCriticalConnectionError(error)) {
            logError(summary, error)
        } else {
            logWarning(summary, error)
        }
    }

    private fun connectionErrorSummary(error: Throwable): String {
        val category = when (error) {
            is UnknownServiceException, is SecurityException -> "Android 网络安全策略拒绝连接"
            is SyncthingRestException -> when (error.responseCode) {
                401, 403 -> "REST API 鉴权失败（HTTP ${error.responseCode}）"
                else -> "REST API 返回异常状态（HTTP ${error.responseCode}）"
            }
            is SocketTimeoutException -> "REST API 连接或读取超时"
            is ConnectException -> "REST API 拒绝连接"
            is NoRouteToHostException -> "REST API 地址不可达"
            is UnknownHostException -> "REST API 地址解析失败"
            is SocketException -> "REST API 套接字连接失败"
            is IOException -> "REST API 通信失败"
            else -> "REST API 请求失败"
        }
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$category，异常=${error.javaClass.simpleName}，详情=${redact(detail)}，地址=$REST_API_ADDRESS"
    }

    private fun connectionErrorSignature(error: Throwable): String = when (error) {
        is SyncthingRestException -> "${error.javaClass.name}:${error.responseCode}"
        else -> "${error.javaClass.name}:${error.message}"
    }

    private fun isCriticalConnectionError(error: Throwable): Boolean =
        error is UnknownServiceException ||
            error is SecurityException ||
            error is SyncthingRestException ||
            (
                error is IOException &&
                    error !is SocketException &&
                    error !is SocketTimeoutException &&
                    error !is UnknownHostException
            )

    private fun writeControllerLog(level: String, message: String, error: Throwable? = null) {
        val detail = error?.message
            ?.takeIf { it.isNotBlank() && !message.contains(it) }
            ?.let { " | ${error.javaClass.simpleName}: ${redact(it)}" }
            .orEmpty()
        val line = "${controllerTimestamp()} [$level] ${redact(message)}$detail\n"
        runCatching {
            synchronized(CONTROLLER_LOG_LOCK) {
                val directory = File(applicationContext.filesDir, "logs").apply { mkdirs() }
                val file = File(directory, CONTROLLER_LOG_FILE)
                if (file.length() >= MAX_CONTROLLER_LOG_BYTES) {
                    val oldFile = File(directory, "$CONTROLLER_LOG_FILE.1")
                    oldFile.delete()
                    file.renameTo(oldFile)
                }
                file.appendText(line)
            }
        }.onFailure { logError ->
            Log.w(TAG, "写入控制器日志失败", logError)
        }
    }

    private fun controllerTimestamp(): String =
        SimpleDateFormat(CONTROLLER_TIME_FORMAT, Locale.US).format(Date())

    private fun redact(value: String): String {
        val apiKey = preferences.getString(KEY_API_KEY, null)
        return (if (apiKey.isNullOrEmpty()) value else value.replace(apiKey, REDACTED_VALUE))
            .replace(Regex("X-API-Key[=:]\\s*\\S+", RegexOption.IGNORE_CASE), "X-API-Key=$REDACTED_VALUE")
            .replace(Regex("--gui-apikey=\\S+"), "--gui-apikey=$REDACTED_VALUE")
    }

    private fun logCoreLogTail() {
        val logDirectory = File(applicationContext.filesDir, "logs")
        val apiKey = preferences.getString(KEY_API_KEY, null)
        val rawReport = listOf(
            "launcher.log" to File(logDirectory, "launcher.log"),
            "syncthing.log" to File(logDirectory, "syncthing.log"),
        ).mapNotNull { (name, file) ->
            readLogTail(file)?.let { tail -> "===== $name =====\n$tail" }
        }.joinToString("\n")
        val report = (if (apiKey.isNullOrEmpty()) {
            rawReport
        } else {
            rawReport.replace(apiKey, REDACTED_VALUE)
        })
            .replace(Regex("--gui-apikey=\\S+"), "--gui-apikey=$REDACTED_VALUE")

        if (report.isBlank()) {
            Log.e(TAG, "核心日志文件不存在或为空")
            return
        }
        logCoreConnectionDiagnostics(report)
        report.takeLast(MAX_CORE_LOG_CHARS)
            .chunked(LOGCAT_CHUNK_CHARS)
            .forEachIndexed { index, chunk ->
                Log.e(TAG, "核心日志尾部 ${index + 1}:\n$chunk")
            }
    }

    private fun logCoreConnectionDiagnostics(report: String) {
        val warnings = report.lineSequence()
            .filter { line -> " WRN " in line || " ERR " in line }
            .mapNotNull { line ->
                when {
                    "lookup " in line && ":53" in line ->
                        "核心 DNS 解析失败：当前核心尝试通过本机回环 DNS（[::1]:53）解析，发现或中继服务可能不可用"
                    "Failed to list network interfaces" in line && "permission denied" in line ->
                        "核心无权读取网络接口，UPnP/NAT 探测可能不可用"
                    "relays.syncthing.net" in line && "Service failed" in line ->
                        "核心中继服务连接失败，请检查 DNS 和外网连接"
                    "discover" in line && ("failed" in line.lowercase(Locale.US) || "error=" in line) ->
                        "核心发现服务连接失败，请检查 DNS、网络权限和外网连接"
                    "api" in line && ("failed" in line.lowercase(Locale.US) || "error=" in line) ->
                        "核心 REST/GUI 监听异常：${line.substringAfter("error=", line)}"
                    else -> null
                }
            }
            .map(::redact)
            .distinct()
            .take(MAX_CORE_DIAGNOSTICS)
            .toList()

        warnings.forEach { diagnostic ->
            if (diagnostic.startsWith("核心 DNS") || diagnostic.startsWith("核心 REST")) {
                logError(diagnostic)
            } else {
                logWarning(diagnostic)
            }
        }
    }

    private fun readLogTail(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        file.useLines { lines ->
            lines.toList()
                .takeLast(MAX_CORE_LOG_LINES)
                .joinToString("\n")
                .ifBlank { null }
        }
    }.onFailure { error ->
        Log.w(TAG, "读取核心日志失败：${file.name}", error)
    }.getOrNull()

    data class SessionResult(
        val started: Boolean,
        val runtimeMillis: Long,
        val exitCode: Int?,
    )

    private data class ApiWaitResult(
        val ready: Boolean,
        val lastError: Throwable?,
    )

    companion object {
        private const val TAG = "SyncthingCore"
        private const val REST_API_ADDRESS = "http://127.0.0.1:8384"
        private const val PREFERENCES = "core_runtime"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PID = "pid"
        private const val API_READY_POLL_COUNT = 30
        private const val API_READY_POLL_INTERVAL_MILLIS = 500L
        private const val CONNECTION_RETRY_LOG_INTERVAL = 10
        private const val STATUS_POLL_INTERVAL_MILLIS = 2_000L
        private const val STATUS_FAILURE_LOG_INTERVAL = 15
        private const val STOP_TIMEOUT_SECONDS = 5L
        private const val FORCE_TIMEOUT_SECONDS = 2L
        private const val STOP_POLL_COUNT = 10
        private const val STOP_POLL_INTERVAL_MILLIS = 500L
        private const val MAX_CORE_LOG_LINES = 40
        private const val MAX_CORE_LOG_CHARS = 8_000
        private const val LOGCAT_CHUNK_CHARS = 3_000
        private const val REDACTED_VALUE = "[REDACTED]"
        private const val CONTROLLER_LOG_FILE = "controller.log"
        private const val CONTROLLER_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val MAX_CONTROLLER_LOG_BYTES = 1024L * 1024L
        private const val MAX_CORE_DIAGNOSTICS = 12
        private val CONTROLLER_LOG_LOCK = Any()
    }
}

private fun Process.exitCodeOrNull(): Int? =
    if (isAlive) null else runCatching { exitValue() }.getOrNull()

private fun Throwable.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
