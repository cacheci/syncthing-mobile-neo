package moe.https.syncthing.ui.util

import moe.https.syncthing.core.CoreState

internal fun CoreState.displayName(): String = when (this) {
    CoreState.NOT_INSTALLED -> "未安装"
    CoreState.STOPPED -> "已停止"
    CoreState.INSTALLING -> "正在导入"
    CoreState.STARTING -> "正在启动"
    CoreState.RUNNING -> "运行中"
    CoreState.STOPPING -> "正在停止"
    CoreState.FAILED -> "运行异常"
}

internal fun formatBytes(value: Long?): String {
    if (value == null) return "—"
    val units = listOf("B", "KiB", "MiB", "GiB")
    var number = value.toDouble()
    var unit = 0
    while (number >= 1024 && unit < units.lastIndex) {
        number /= 1024
        unit++
    }
    return if (unit == 0) {
        "${number.toLong()} ${units[unit]}"
    } else {
        "${(number * 10).toLong() / 10.0} ${units[unit]}"
    }
}

internal fun formatDuration(seconds: Long?): String {
    if (seconds == null) return "—"
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    val remainingSeconds = seconds % 60
    return buildString {
        if (days > 0) append("${days}天 ")
        if (hours > 0 || days > 0) append("${hours}小时 ")
        if (minutes > 0 || hours > 0 || days > 0) append("${minutes}分 ")
        append("${remainingSeconds}秒")
    }
}
