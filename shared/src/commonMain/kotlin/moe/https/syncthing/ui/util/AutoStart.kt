package moe.https.syncthing.ui.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.storage.AppSettingPrivateStorage

@Serializable
enum class AutoStartModeType(
    val displayName: String,
) {
    DISABLED("不自动运行"),
    WITH_CONDITION("满足条件时运行"),
    ENABLED("总是自动运行"),
}

@Serializable
data class AutoStartCondition(
    val network: NetworkRunCondition = NetworkRunCondition(),
    val battery: BatteryRunCondition = BatteryRunCondition(),
    val scheduleEnabled: Boolean = false,
    val schedules: List<ExecuteSchedule> = emptyList(),
    val startCronTriggers: List<CronTrigger> = emptyList(),
    val stopCronTriggers: List<CronTrigger> = emptyList(),
)

@Serializable
data class NetworkRunCondition(
    val runOnWifi: Boolean = true,
    val runOnMeteredWifi: Boolean = false,
    val restrictWifiNames: Boolean = false,
    val wifiNames: Set<String> = emptySet(),
    val runOnMobileData: Boolean = false,
    val runOnRoaming: Boolean = false,
    val runWithoutNetwork: Boolean = false,
)

@Serializable
data class BatteryRunCondition(
    val poweredBy: SettingConfiguration.RunningOnPoweredBy =
        SettingConfiguration.RunningOnPoweredBy.BOTH,
    val minimumPercent: Int = 20,
    val maximumPercent: Int = 100,
    val respectPowerSaveMode: Boolean = true,
)

@Serializable
enum class ExecuteScheduleType(
    val displayName: String,
) {
    INTERVAL("间歇式运行"),
    TIME_RANGE("按时间段运行"),
}

@Serializable
data class CronTrigger(
    val id: Long,
    val expression: String = "0 5 * * *",
)

@Serializable
data class ExecuteSchedule(
    val id: Long,
    val type: ExecuteScheduleType = ExecuteScheduleType.TIME_RANGE,
    val runMinutes: Int = 5,
    val pauseMinutes: Int = 60,
    val startMinuteOfDay: Int = 5 * 60 + 30,
    val endMinuteOfDay: Int = 16 * 60,
    val weekDays: Set<Int> = (1..7).toSet(),
)

fun loadAutoStartCondition(storage: AppSettingPrivateStorage): AutoStartCondition =
    runCatching {
        autoStartJson.decodeFromString<AutoStartCondition>(
            storage.getString(AppSettingPrivateStorage.KEY_AUTO_START_CONDITION)!!,
        )
    }.getOrDefault(AutoStartCondition()).normalized()

fun saveAutoStartCondition(
    storage: AppSettingPrivateStorage,
    condition: AutoStartCondition,
) {
    storage.putString(
        AppSettingPrivateStorage.KEY_AUTO_START_CONDITION,
        autoStartJson.encodeToString(condition.normalized()),
    )
}

fun AutoStartCondition.normalized(): AutoStartCondition = copy(
    network = network.copy(
        wifiNames = network.wifiNames
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet(),
    ),
    battery = battery.copy(
        minimumPercent = battery.minimumPercent.coerceIn(0, 100),
        maximumPercent = battery.maximumPercent.coerceIn(0, 100),
    ).let { batteryCondition ->
        if (batteryCondition.minimumPercent <= batteryCondition.maximumPercent) {
            batteryCondition
        } else {
            batteryCondition.copy(
                minimumPercent = batteryCondition.maximumPercent,
                maximumPercent = batteryCondition.minimumPercent,
            )
        }
    },
    schedules = schedules
        .distinctBy(ExecuteSchedule::id)
        .map { schedule ->
            schedule.copy(
                runMinutes = schedule.runMinutes.coerceAtLeast(1),
                pauseMinutes = schedule.pauseMinutes.coerceAtLeast(1),
                startMinuteOfDay = schedule.startMinuteOfDay.coerceIn(0, 1439),
                endMinuteOfDay = schedule.endMinuteOfDay.coerceIn(0, 1439),
                weekDays = schedule.weekDays.filter { it in 1..7 }.toSet(),
            )
        },
    startCronTriggers = startCronTriggers
        .distinctBy(CronTrigger::id)
        .map { it.copy(expression = it.expression.trim()) },
    stopCronTriggers = stopCronTriggers
        .distinctBy(CronTrigger::id)
        .map { it.copy(expression = it.expression.trim()) },
)

class CronExpression private constructor(
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val everyDayOfMonth: Boolean,
    private val everyDayOfWeek: Boolean,
) {
    fun matches(
        minute: Int,
        hour: Int,
        dayOfMonth: Int,
        month: Int,
        dayOfWeek: Int,
    ): Boolean {
        if (minute !in minutes || hour !in hours || month !in months) return false
        val matchesDayOfMonth = dayOfMonth in daysOfMonth
        val matchesDayOfWeek = dayOfWeek % 7 in daysOfWeek
        val matchesDay = when {
            everyDayOfMonth && everyDayOfWeek -> true
            everyDayOfMonth -> matchesDayOfWeek
            everyDayOfWeek -> matchesDayOfMonth
            else -> matchesDayOfMonth || matchesDayOfWeek
        }
        return matchesDay
    }

    companion object {
        fun parse(value: String): CronExpression? {
            val fields = value.trim().split(Regex("\\s+")).takeIf { it.size == 5 }
                ?: return null
            val minutes = parseCronField(fields[0], 0, 59) ?: return null
            val hours = parseCronField(fields[1], 0, 23) ?: return null
            val daysOfMonth = parseCronField(fields[2], 1, 31) ?: return null
            val months = parseCronField(fields[3], 1, 12) ?: return null
            val rawDaysOfWeek = parseCronField(fields[4], 0, 7) ?: return null
            return CronExpression(
                minutes = minutes.values,
                hours = hours.values,
                daysOfMonth = daysOfMonth.values,
                months = months.values,
                daysOfWeek = rawDaysOfWeek.values.map { it % 7 }.toSet(),
                everyDayOfMonth = daysOfMonth.wildcard,
                everyDayOfWeek = rawDaysOfWeek.wildcard,
            )
        }
    }
}

fun isValidCronExpression(value: String): Boolean = CronExpression.parse(value) != null

private data class ParsedCronField(
    val values: Set<Int>,
    val wildcard: Boolean,
)

private fun parseCronField(
    field: String,
    minimum: Int,
    maximum: Int,
): ParsedCronField? {
    if (field.isBlank()) return null
    val values = mutableSetOf<Int>()
    val parts = field.split(',')
    for (part in parts) {
        if (part.isBlank()) return null
        val rangeAndStep = part.split('/')
        if (rangeAndStep.size > 2) return null
        val step = rangeAndStep.getOrNull(1)?.toIntOrNull() ?: 1
        if (step <= 0) return null
        val rangeText = rangeAndStep[0]
        val range = when {
            rangeText == "*" -> minimum..maximum
            '-' in rangeText -> {
                val bounds = rangeText.split('-')
                if (bounds.size != 2) return null
                val start = bounds[0].toIntOrNull() ?: return null
                val end = bounds[1].toIntOrNull() ?: return null
                if (start !in minimum..maximum || end !in minimum..maximum || start > end) {
                    return null
                }
                start..end
            }
            else -> {
                if (rangeAndStep.size != 1) return null
                val exact = rangeText.toIntOrNull() ?: return null
                if (exact !in minimum..maximum) return null
                exact..exact
            }
        }
        range.step(step).forEach { values.add(it) }
    }
    return ParsedCronField(
        values = values,
        wildcard = field == "*",
    )
}

private val autoStartJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
