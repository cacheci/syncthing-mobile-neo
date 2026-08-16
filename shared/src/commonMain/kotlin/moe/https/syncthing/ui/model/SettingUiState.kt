package moe.https.syncthing.ui.model

import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration

data class SettingUiState(
    val settingRaw: SettingConfiguration? = null,
    val formState: SettingFormState = SettingFormState(),
    val accessMode: SettingAccessMode? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isFormValid: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val restartRequired: Boolean = false,
)

data class SettingFormState(
    val deviceName: String = "",
    val minHomeDiskFree: String = "",
    val minHomeDiskFreeUnit: SettingConfiguration.DiskSpaceUnit = SettingConfiguration.DiskSpaceUnit.PERCENT,
    val usageReportingEnabled: Boolean = false,
    val guiListenAddress: String = "",
    val guiPort: String = "",
    val guiPortConflictBehavior: SettingConfiguration.GuiPortConflictBehavior =
        SettingConfiguration.GuiPortConflictBehavior.FAIL,
    val guiAuthenticationEnabled: Boolean = false,
    val guiUser: String = "",
    val newGuiPassword: String = "",
    val guiTheme: SettingConfiguration.GuiTheme = SettingConfiguration.GuiTheme.DEFAULT,
    val listenAddresses: String = "",
    val maxSendKiBPerSecond: String = "",
    val maxReceiveKiBPerSecond: String = "",
    val reconnectionIntervalSeconds: String = "",
    val limitBandwidthInLan: Boolean = false,
    val globalDiscoveryEnabled: Boolean = false,
    val globalDiscoveryServers: String = "",
    val localDiscoveryEnabled: Boolean = false,
    val localDiscoveryPort: String = "",
    val localDiscoveryMulticastAddress: String = "",
    val announceLanAddresses: Boolean = false,
    val natEnabled: Boolean = false,
    val relaysEnabled: Boolean = false,
    val alwaysLocalNetworks: String = "",
    val connectionLimitMax: String = "",
)
