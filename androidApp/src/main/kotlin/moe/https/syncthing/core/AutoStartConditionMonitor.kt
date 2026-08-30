package moe.https.syncthing.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.util.AutoStartCondition
import moe.https.syncthing.ui.util.CronExpression
import moe.https.syncthing.ui.util.ExecuteSchedule
import moe.https.syncthing.ui.util.ExecuteScheduleType
import moe.https.syncthing.ui.util.loadAutoStartCondition
import java.util.Calendar

internal class AutoStartConditionMonitor(
    context: Context,
    private val storage: AppSettingPrivateStorage,
    private val onConditionChanged: (Boolean) -> Unit,
    private val onStartTriggered: () -> Unit,
    private val onStopTriggered: () -> Unit,
) {
    private val applicationContext = context.applicationContext
    private val connectivityManager =
        applicationContext.getSystemService(ConnectivityManager::class.java)
    private val powerManager = applicationContext.getSystemService(PowerManager::class.java)
    private val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val availableNetworks = mutableMapOf<Network, NetworkCapabilities>()
    private var batteryState: Intent? = null
    private var started = false
    private var lastResult: Boolean? = null
    private var lastCronMinute: Long? = null
    private val handledCronTriggers = mutableSetOf<String>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluate()
        override fun onLost(network: Network) = evaluate()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            evaluate()
    }

    private val availableNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                availableNetworks[network] = capabilities
            }
            evaluate()
        }

        override fun onLost(network: Network) {
            availableNetworks.remove(network)
            evaluate()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            availableNetworks[network] = capabilities
            evaluate()
        }
    }

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                batteryState = intent
            }
            evaluate()
        }
    }

    fun start() {
        if (started) {
            refresh()
            return
        }
        started = true
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback, mainHandler)
        }
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                availableNetworkCallback,
                mainHandler,
            )
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        batteryState = applicationContext.registerReceiver(systemStateReceiver, filter)
        evaluate()
    }

    fun refresh() {
        lastResult = null
        evaluate()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(availableNetworkCallback) }
        runCatching { applicationContext.unregisterReceiver(systemStateReceiver) }
        availableNetworks.clear()
        lastResult = null
        lastCronMinute = null
        handledCronTriggers.clear()
    }

    private fun evaluate() {
        if (!started) return
        val condition = loadAutoStartCondition(storage)
        val calendar = Calendar.getInstance()
        val networkSatisfied = networkMatches(condition)
        val batterySatisfied = batteryMatches(condition)
        val result = networkSatisfied &&
            batterySatisfied &&
            scheduleMatches(condition, calendar)
        if (result != lastResult) {
            lastResult = result
            onConditionChanged(result)
        }
        dispatchCronTriggers(
            condition = condition,
            calendar = calendar,
            networkAndBatterySatisfied = networkSatisfied && batterySatisfied,
        )
    }

    private fun networkMatches(condition: AutoStartCondition): Boolean {
        val networkCondition = condition.network
        val capabilities = activeTransportCapabilities()
            ?: return networkCondition.runWithoutNetwork

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                if (!networkCondition.runOnWifi) return false
                if (connectivityManager.isActiveNetworkMetered && !networkCondition.runOnMeteredWifi) {
                    return false
                }
                if (!networkCondition.restrictWifiNames) return true
                val wifiName = currentWifiName(capabilities) ?: return false
                networkCondition.wifiNames.any { it.equals(wifiName, ignoreCase = true) }
            }

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (!networkCondition.runOnMobileData) return false
                val roaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                !roaming || networkCondition.runOnRoaming
            }

            else -> false
        }
    }

    private fun activeTransportCapabilities(): NetworkCapabilities? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val activeCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (!activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return activeCapabilities
        }
        if (activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        ) {
            return activeCapabilities
        }
        return availableNetworks
            .asSequence()
            .filter { (network, _) -> network != activeNetwork }
            .map { (_, capabilities) -> capabilities }
            .firstOrNull { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            }
            ?: activeCapabilities
    }

    @Suppress("DEPRECATION")
    private fun currentWifiName(capabilities: NetworkCapabilities): String? {
        val wifiInfo = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            null
        })
            ?: runCatching { wifiManager.connectionInfo }.getOrNull()
        return wifiInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }
    }

    private fun batteryMatches(condition: AutoStartCondition): Boolean {
        val batteryIntent = batteryState ?: applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return false
        batteryState = batteryIntent
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return false
        val percent = level * 100 / scale
        if (percent !in condition.battery.minimumPercent..condition.battery.maximumPercent) {
            return false
        }
        val powered = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val poweredByMatches = when (condition.battery.poweredBy) {
            SettingConfiguration.RunningOnPoweredBy.CHARGED -> powered
            SettingConfiguration.RunningOnPoweredBy.BATTERY -> !powered
            SettingConfiguration.RunningOnPoweredBy.BOTH -> true
        }
        if (!poweredByMatches) return false
        return !condition.battery.respectPowerSaveMode || !powerManager.isPowerSaveMode
    }

    private fun scheduleMatches(
        condition: AutoStartCondition,
        calendar: Calendar,
    ): Boolean {
        if (!condition.scheduleEnabled) return true
        return condition.schedules.any { schedule ->
            when (schedule.type) {
                ExecuteScheduleType.INTERVAL -> intervalScheduleMatches(schedule, calendar)
                ExecuteScheduleType.TIME_RANGE -> timeRangeScheduleMatches(schedule, calendar)
            }
        }
    }

    private fun intervalScheduleMatches(
        schedule: ExecuteSchedule,
        calendar: Calendar,
    ): Boolean {
        val totalMinutes = schedule.runMinutes.toLong() + schedule.pauseMinutes.toLong()
        if (totalMinutes <= 0L) return false
        val elapsedMinutes = calendar.timeInMillis / 60_000L
        return elapsedMinutes % totalMinutes < schedule.runMinutes.toLong()
    }

    private fun timeRangeScheduleMatches(
        schedule: ExecuteSchedule,
        calendar: Calendar,
    ): Boolean {
        if (schedule.weekDays.isEmpty()) return false
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK).toIsoWeekDay()
        if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) {
            return currentDay in schedule.weekDays
        }
        return if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
            currentDay in schedule.weekDays &&
                minuteOfDay in schedule.startMinuteOfDay until schedule.endMinuteOfDay
        } else {
            if (minuteOfDay >= schedule.startMinuteOfDay) {
                currentDay in schedule.weekDays
            } else {
                val previousDay = if (currentDay == 1) 7 else currentDay - 1
                previousDay in schedule.weekDays && minuteOfDay < schedule.endMinuteOfDay
            }
        }
    }

    private fun dispatchCronTriggers(
        condition: AutoStartCondition,
        calendar: Calendar,
        networkAndBatterySatisfied: Boolean,
    ) {
        val currentMinute = calendar.timeInMillis / 60_000L
        if (lastCronMinute != currentMinute) {
            lastCronMinute = currentMinute
            handledCronTriggers.clear()
        }

        val matchedStartKeys = condition.startCronTriggers.mapNotNull { trigger ->
            val key = "start:${trigger.id}"
            if (key !in handledCronTriggers &&
                CronExpression.parse(trigger.expression)?.matches(calendar) == true
            ) {
                key
            } else {
                null
            }
        }
        val matchedStopKeys = condition.stopCronTriggers.mapNotNull { trigger ->
            val key = "stop:${trigger.id}"
            if (key !in handledCronTriggers &&
                CronExpression.parse(trigger.expression)?.matches(calendar) == true
            ) {
                key
            } else {
                null
            }
        }

        handledCronTriggers += matchedStartKeys
        handledCronTriggers += matchedStopKeys
        if (!networkAndBatterySatisfied) return
        when {
            matchedStopKeys.isNotEmpty() -> onStopTriggered()
            matchedStartKeys.isNotEmpty() -> onStartTriggered()
        }
    }
}

private fun Int.toIsoWeekDay(): Int = ((this + 5) % 7) + 1

private fun CronExpression.matches(calendar: Calendar): Boolean = matches(
    minute = calendar.get(Calendar.MINUTE),
    hour = calendar.get(Calendar.HOUR_OF_DAY),
    dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
    month = calendar.get(Calendar.MONTH) + 1,
    dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1,
)
