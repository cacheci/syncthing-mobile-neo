package moe.https.syncthing.ui.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import moe.https.syncthing.core.CoreState
import kotlin.math.round

internal fun CoreState.displayName(): String = when (this) {
    CoreState.NOT_INSTALLED -> "未安装"
    CoreState.STOPPED -> "已停止"
    CoreState.INSTALLING -> "正在导入"
    CoreState.STARTING -> "正在启动"
    CoreState.RUNNING -> "运行中"
    CoreState.STOPPING -> "正在停止"
    CoreState.FAILED -> "运行异常"
}

internal fun formatBytes(value: Long?): String? {
    if (value == null) return null
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

internal fun formatBitsPerSecond(bytesPerSecond: Long?): String? {
    if (bytesPerSecond == null) return null
    val units = listOf("bps", "kbps", "Mbps", "Gbps", "Tbps")
    var number = bytesPerSecond.toDouble() * 8.0
    var unit = 0
    while (number > 1_000.0 && unit < units.lastIndex) {
        number /= 1_000.0
        unit++
    }
    val rounded = when {
        unit == 0 || number >= 100.0 -> round(number)
        number >= 10.0 -> round(number * 10.0) / 10.0
        else -> round(number * 100.0) / 100.0
    }
    val displayValue = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
    return "$displayValue ${units[unit]}"
}

internal fun formatDuration(seconds: Long?): String? {
    if (seconds == null) return null
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

@Serializable
data class ListenAddressListItem(
    val enabled: Boolean,
    val uri: String
)

@Serializable
data class ListenAddressSetting(
    @SerialName("STACK")
    val stackPrefer: UriProtocolStack,

    @SerialName("TCP")
    val tcp: Boolean = true,

    @SerialName("QUIC")
    val quic: Boolean = true,

    @SerialName("PORT")
    val port: Int,

    @SerialName("relay")
    val relays: List<ListenAddressListItem> = emptyList(),
)

enum class SettingProtocolStack(
    val displayName: String,
    val guiListenAddress: String,
) {
    IPV4("IPv4", "127.0.0.1"),
    IPV6("IPv6", "::1"),
    DUAL("双栈", "localhost"),
    CUSTOM("高级", guiListenAddress = "localhost")
}

enum class UriProtocolStack(
    val displayName: String,
) {
    IPV4("IPv4"),
    IPV6("IPv6"),
    DUAL("双栈"),
}
