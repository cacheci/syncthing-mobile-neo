package moe.https.syncthing.ui.model

import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration

data class SettingUiState(
    val setting: SettingConfiguration? = null,
    val formState: SettingFormState? = null,
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
    val deviceName: String,
    val minHomeDiskFree: String,
    val minHomeDiskFreeUnit: SettingConfiguration.DiskSpaceUnit,
    val usageReportingEnabled: Boolean,
    val guiListenAddress: String,
    val guiPort: String,
    val guiPortConflictBehavior: SettingConfiguration.GuiPortConflictBehavior,
    val guiAuthenticationEnabled: Boolean,
    val guiUser: String,
    val newGuiPassword: String,
    val guiTheme: SettingConfiguration.GuiTheme,
    val listenAddresses: String,
    val maxSendKiBPerSecond: String,
    val maxReceiveKiBPerSecond: String,
    val reconnectionIntervalSeconds: String,
    val limitBandwidthInLan: Boolean,
    val globalDiscoveryEnabled: Boolean,
    val globalDiscoveryServers: String,
    val localDiscoveryEnabled: Boolean,
    val localDiscoveryPort: String,
    val localDiscoveryMulticastAddress: String,
    val announceLanAddresses: Boolean,
    val natEnabled: Boolean,
    val relaysEnabled: Boolean,
    val alwaysLocalNetworks: String,
    val connectionLimitMax: String,
    val allowGuiListenNonLocal: Boolean,
)
