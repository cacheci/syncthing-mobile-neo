package moe.https.syncthing.core

data class SettingConfiguration(
    val deviceName: String,
    val minHomeDiskFree: Double,
    val minHomeDiskFreeUnit: DiskSpaceUnit,
    val usageReportingEnabled: Boolean,
    val usageReportingVersion: Int,
    val guiListenAddress: String,
    val guiPort: Int,
    val guiPortConflictBehavior: GuiPortConflictBehavior,
    val guiAuthenticationEnabled: Boolean,
    val guiUser: String,
    val guiPasswordConfigured: Boolean,
    val newGuiPassword: String = "",
    val guiTheme: GuiTheme,
    val listenAddresses: List<String>,
    val maxSendKiBPerSecond: Int,
    val maxReceiveKiBPerSecond: Int,
    val reconnectionIntervalSeconds: Int,
    val limitBandwidthInLan: Boolean,
    val globalDiscoveryEnabled: Boolean,
    val globalDiscoveryServers: List<String>,
    val localDiscoveryEnabled: Boolean,
    val localDiscoveryPort: Int,
    val localDiscoveryMulticastAddress: String,
    val announceLanAddresses: Boolean,
    val natEnabled: Boolean,
    val relaysEnabled: Boolean,
    val alwaysLocalNetworks: List<String>,
    val connectionLimitEnough: Int,
    val connectionLimitMax: Int,
) {
    enum class DiskSpaceUnit(val apiValue: String, val displayName: String) {
        PERCENT("%", "%"),
        KILOBYTE("kB", "KiB"),
        MEGABYTE("MB", "MiB"),
        GIGABYTE("GB", "GiB"),
        TERABYTE("TB", "TiB"),
    }

    enum class GuiTheme(val apiValue: String, val displayName: String) {
        DEFAULT("default", "跟随系统"),
        LIGHT("light", "浅色"),
        DARK("dark", "深色"),
        BLACK("black", "OLED 纯黑"),
    }

    enum class GuiPortConflictBehavior(val displayName: String) {
        FAIL("关闭"),
        TRY_NEXT("自增"),
    }

    companion object {
        fun startupDefaults(
            guiListenAddress: String = "127.0.0.1",
            guiPort: Int = 8384,
            guiPortConflictBehavior: GuiPortConflictBehavior = GuiPortConflictBehavior.FAIL,
        ): SettingConfiguration = SettingConfiguration(
            deviceName = "Syncthing",
            minHomeDiskFree = 1.0,
            minHomeDiskFreeUnit = DiskSpaceUnit.PERCENT,
            usageReportingEnabled = false,
            usageReportingVersion = 1,
            guiListenAddress = guiListenAddress,
            guiPort = guiPort,
            guiPortConflictBehavior = guiPortConflictBehavior,
            guiAuthenticationEnabled = false,
            guiUser = "",
            guiPasswordConfigured = false,
            guiTheme = GuiTheme.DEFAULT,
            listenAddresses = listOf("default"),
            maxSendKiBPerSecond = 0,
            maxReceiveKiBPerSecond = 0,
            reconnectionIntervalSeconds = 60,
            limitBandwidthInLan = false,
            globalDiscoveryEnabled = true,
            globalDiscoveryServers = listOf("default"),
            localDiscoveryEnabled = true,
            localDiscoveryPort = 21027,
            localDiscoveryMulticastAddress = "[ff12::8384]:21027",
            announceLanAddresses = true,
            natEnabled = true,
            relaysEnabled = true,
            alwaysLocalNetworks = emptyList(),
            connectionLimitEnough = 0,
            connectionLimitMax = 0,
        )
    }
}

enum class SettingAccessMode(val title: String, val caption: String) {
    REST(title = "核心配置模式", caption = "核心正在运行，更改将在点击保存设置后生效。"),
    CONFIG_FILE(title = "离线配置模式", caption = "核心未运行，更改将在下次启动时生效。"),
    STARTUP_ONLY(title = "尚未初始化", caption = "部分设置不可用，请启动一次核心以初始化。"),
}



data class SettingSnapshot(
    val configuration: SettingConfiguration,
    val accessMode: SettingAccessMode,
)

data class SettingSaveResult(
    val restartRequired: Boolean,
    val accessMode: SettingAccessMode,
)
