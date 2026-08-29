package moe.https.syncthing.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eygraber.uri.Uri
import com.eygraber.uri.toKmpUriOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.core.SettingController
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.model.SettingFormState
import moe.https.syncthing.ui.model.SettingUiState
import moe.https.syncthing.ui.util.ListenAddressListItem
import moe.https.syncthing.ui.util.ListenAddressSetting
import moe.https.syncthing.ui.util.SettingProtocolStack
import moe.https.syncthing.ui.util.UriProtocolStack
import kotlin.time.Duration.Companion.milliseconds

sealed interface DiscoveryServerPingState {
    data object InProgress : DiscoveryServerPingState
    data class Success(val latencyMillis: Long) : DiscoveryServerPingState
    data class Failure(val message: String) : DiscoveryServerPingState
}

class SettingViewModel(
    private val controller: SettingController,
    private val appSettingsStorage: AppSettingPrivateStorage,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = mutableUiState.asStateFlow()
    private val operationMutex = Mutex()
    private val discoveryServerPingStates = mutableStateMapOf<String, DiscoveryServerPingState>()
    var listenAddressSettingUnsaved by mutableStateOf( loadSavedListenSetting() )
    var discoveryAddressSettingUnsaved by mutableStateOf( loadSavedDiscoverySetting() )
    var addressProtocolStack by mutableStateOf(
        appSettingsStorage.getString(AppSettingPrivateStorage.KEY_PROTOCOL_STACK)
            ?.let { storedValue ->
                SettingProtocolStack.entries.firstOrNull { it.name == storedValue }
            }
            ?: SettingProtocolStack.DUAL,
    )

    var actualListenStack by mutableStateOf(
        when (addressProtocolStack) {
            SettingProtocolStack.CUSTOM -> listenAddressSettingUnsaved.stackPrefer
            SettingProtocolStack.IPV4 -> UriProtocolStack.IPV4
            SettingProtocolStack.IPV6 -> UriProtocolStack.IPV6
            SettingProtocolStack.DUAL -> UriProtocolStack.DUAL
        }
    )

    fun discoveryServerPingState(address: String): DiscoveryServerPingState? =
        discoveryServerPingStates[address.trim()]

    fun pingDiscoveryServer(address: String) {
        val normalizedAddress = address.trim()
        if (normalizedAddress.isBlank()) {
            discoveryServerPingStates[normalizedAddress] = DiscoveryServerPingState.Failure(
                "Discovery 地址不能为空",
            )
            return
        }
        if (discoveryServerPingStates[normalizedAddress] == DiscoveryServerPingState.InProgress) {
            return
        }

        discoveryServerPingStates[normalizedAddress] = DiscoveryServerPingState.InProgress
        viewModelScope.launch {
            try {
                val latencyMillis = controller.pingDiscoveryServer(normalizedAddress)
                discoveryServerPingStates[normalizedAddress] =
                    DiscoveryServerPingState.Success(latencyMillis)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                discoveryServerPingStates[normalizedAddress] = DiscoveryServerPingState.Failure(
                    error.userMessage(),
                )
            }
        }
    }

    fun clearDiscoveryServerPingState(address: String) {
        discoveryServerPingStates.remove(address.trim())
    }


    fun onCoreUnavailable() {
        mutableUiState.update {
            it.copy(
                successMessage = null,
                restartRequired = false,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            operationMutex.withLock {
                if (mutableUiState.value.isLoading || mutableUiState.value.isSaving) {
                    return@withLock
                }

                mutableUiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        successMessage = null,
                    )
                }
                try {
                    val snapshot = controller.loadSetting()
                    mutableUiState.update { state ->
                        val formState = if (state.hasUnsavedChanges()) {
                            state.formState
                        } else {
                            snapshot.configuration.toFormState()
                        }
                        state.copy(
                            settingRaw = snapshot.configuration,
                            formState = formState,
                            accessMode = snapshot.accessMode,
                            isLoading = false,
                            isFormValid = formState.isValid(snapshot.configuration, snapshot.accessMode),
                            hasLoaded = true,
                            errorMessage = null,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = error.userMessage(),
                        )
                    }
                }
            }
        }
    }

    fun onFormChange(
        deviceName: String? = null,
        minHomeDiskFree: String? = null,
        minHomeDiskFreeUnit: SettingConfiguration.DiskSpaceUnit? = null,
        usageReportingEnabled: Boolean? = null,
        guiListenAddress: String? = null,
        guiPort: String? = null,
        guiPortConflictBehavior: SettingConfiguration.GuiPortConflictBehavior? = null,
        guiAuthenticationEnabled: Boolean? = null,
        guiUser: String? = null,
        newGuiPassword: String? = null,
        guiTheme: SettingConfiguration.GuiTheme? = null,
        listenAddresses: String? = null,
        maxSendKiBPerSecond: String? = null,
        maxReceiveKiBPerSecond: String? = null,
        reconnectionIntervalSeconds: String? = null,
        limitBandwidthInLan: Boolean? = null,
        globalDiscoveryEnabled: Boolean? = null,
        globalDiscoveryServers: String? = null,
        localDiscoveryEnabled: Boolean? = null,
        localDiscoveryPort: String? = null,
        localDiscoveryMulticastAddress: String? = null,
        announceLanAddresses: Boolean? = null,
        natEnabled: Boolean? = null,
        relaysEnabled: Boolean? = null,
        alwaysLocalNetworks: String? = null,
        connectionLimitMax: String? = null,
    ) {
        if (
            deviceName == null &&
            minHomeDiskFree == null &&
            minHomeDiskFreeUnit == null &&
            usageReportingEnabled == null &&
            guiListenAddress == null &&
            guiPort == null &&
            guiPortConflictBehavior == null &&
            guiAuthenticationEnabled == null &&
            guiUser == null &&
            newGuiPassword == null &&
            guiTheme == null &&
            listenAddresses == null &&
            maxSendKiBPerSecond == null &&
            maxReceiveKiBPerSecond == null &&
            reconnectionIntervalSeconds == null &&
            limitBandwidthInLan == null &&
            globalDiscoveryEnabled == null &&
            globalDiscoveryServers == null &&
            localDiscoveryEnabled == null &&
            localDiscoveryPort == null &&
            localDiscoveryMulticastAddress == null &&
            announceLanAddresses == null &&
            natEnabled == null &&
            relaysEnabled == null &&
            alwaysLocalNetworks == null &&
            connectionLimitMax == null
        ) {
            return
        }

        mutableUiState.update { state ->
            val setting = state.settingRaw
            if (state.isSaving || setting == null) {
                state
            } else {
                val currentFormState = state.formState
                val changedFormState = currentFormState.copy(
                    deviceName = deviceName ?: currentFormState.deviceName,
                    minHomeDiskFree = minHomeDiskFree ?: currentFormState.minHomeDiskFree,
                    minHomeDiskFreeUnit = minHomeDiskFreeUnit ?: currentFormState.minHomeDiskFreeUnit,
                    usageReportingEnabled = usageReportingEnabled ?: currentFormState.usageReportingEnabled,
                    guiListenAddress = guiListenAddress ?: currentFormState.guiListenAddress,
                    guiPort = guiPort ?: currentFormState.guiPort,
                    guiPortConflictBehavior =
                        guiPortConflictBehavior ?: currentFormState.guiPortConflictBehavior,
                    guiAuthenticationEnabled =
                        guiAuthenticationEnabled ?: currentFormState.guiAuthenticationEnabled,
                    guiUser = guiUser ?: currentFormState.guiUser,
                    newGuiPassword = newGuiPassword ?: currentFormState.newGuiPassword,
                    guiTheme = guiTheme ?: currentFormState.guiTheme,
                    listenAddresses = listenAddresses ?: currentFormState.listenAddresses,
                    maxSendKiBPerSecond =
                        maxSendKiBPerSecond ?: currentFormState.maxSendKiBPerSecond,
                    maxReceiveKiBPerSecond =
                        maxReceiveKiBPerSecond ?: currentFormState.maxReceiveKiBPerSecond,
                    reconnectionIntervalSeconds =
                        reconnectionIntervalSeconds ?: currentFormState.reconnectionIntervalSeconds,
                    limitBandwidthInLan = limitBandwidthInLan ?: currentFormState.limitBandwidthInLan,
                    globalDiscoveryEnabled =
                        globalDiscoveryEnabled ?: currentFormState.globalDiscoveryEnabled,
                    globalDiscoveryServers =
                        globalDiscoveryServers ?: currentFormState.globalDiscoveryServers,
                    localDiscoveryEnabled =
                        localDiscoveryEnabled ?: currentFormState.localDiscoveryEnabled,
                    localDiscoveryPort = localDiscoveryPort ?: currentFormState.localDiscoveryPort,
                    localDiscoveryMulticastAddress =
                        localDiscoveryMulticastAddress ?: currentFormState.localDiscoveryMulticastAddress,
                    announceLanAddresses = announceLanAddresses ?: currentFormState.announceLanAddresses,
                    natEnabled = natEnabled ?: currentFormState.natEnabled,
                    relaysEnabled = relaysEnabled ?: currentFormState.relaysEnabled,
                    alwaysLocalNetworks = alwaysLocalNetworks ?: currentFormState.alwaysLocalNetworks,
                    connectionLimitMax = connectionLimitMax ?: currentFormState.connectionLimitMax,
                )
                if (changedFormState == currentFormState) {
                    state
                } else {
                    state.copy(
                        formState = changedFormState,
                        isFormValid = changedFormState.isValid(setting, state.accessMode),
                    )
                }
            }
        }
    }

    fun save() {
        val state = mutableUiState.value
        val settingRaw = state.settingRaw
        val formState = state.formState
            .copy(
                listenAddresses = getListenAddressStringFromUnsaved(listenAddressSettingUnsaved),
                globalDiscoveryServers = getDiscoveryAddressStringFromUnsaved(discoveryAddressSettingUnsaved)
            )
        if ( settingRaw == null ) {
            mutableUiState.update {
                it.copy(errorMessage = "设置尚未加载", successMessage = null)
            }
            return
        }

        val configuration = formState.toConfiguration(settingRaw)
        val normalizedConfiguration = configuration.trim()
        val accessMode = state.accessMode
        val validationError = formState.validationError(settingRaw, accessMode)
        if (validationError != null) {
            mutableUiState.update {
                it.copy(errorMessage = validationError, successMessage = null)
            }
            return
        }

        viewModelScope.launch {
            operationMutex.withLock {
                if (mutableUiState.value.isLoading || mutableUiState.value.isSaving) {
                    return@withLock
                }

                mutableUiState.update {
                    it.copy(
                        isSaving = true,
                        errorMessage = null,
                        successMessage = null,
                    )
                }
                try {
                    val result = controller.saveSetting(normalizedConfiguration)
                    val savedConfiguration = normalizedConfiguration.copy(
                        guiPasswordConfigured = if (result.accessMode == SettingAccessMode.STARTUP_ONLY) {
                            normalizedConfiguration.guiPasswordConfigured
                        } else {
                            normalizedConfiguration.guiPasswordConfigured ||
                                normalizedConfiguration.guiAuthenticationEnabled
                        },
                        newGuiPassword = "",
                    )
                    val savedFormState = savedConfiguration.toFormState()
                    delay(1000.milliseconds)
                    mutableUiState.update {
                        it.copy(
                            settingRaw = savedConfiguration,
                            formState = savedFormState,
                            accessMode = result.accessMode,
                            isSaving = false,
                            isFormValid = savedFormState.isValid(savedConfiguration, result.accessMode),
                            hasLoaded = true,
                            successMessage = result.successMessage(),
                            restartRequired = result.restartRequired,
                        )
                    }

                    appSettingsStorage.putString(
                        AppSettingPrivateStorage.KEY_LISTEN_PREFERENCE,
                        Json.encodeToString(listenAddressSettingUnsaved.trim()),
                    )

                    appSettingsStorage.putString(
                        AppSettingPrivateStorage.KEY_DISCOVERY_PREFERENCE,
                        Json.encodeToString(discoveryAddressSettingUnsaved.trim()),
                    )

                    appSettingsStorage.putString(
                        AppSettingPrivateStorage.KEY_PROTOCOL_STACK,
                        addressProtocolStack.name
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.userMessage(),
                        )
                    }
                }
            }
        }
    }

    fun loadSavedListenSetting(): ListenAddressSetting {
        return runCatching {
            Json.decodeFromString<ListenAddressSetting>(
                appSettingsStorage.getString( AppSettingPrivateStorage.KEY_LISTEN_PREFERENCE)!!
            )
        }.getOrDefault( ListenAddressSetting(
            stackPrefer = UriProtocolStack.DUAL,
            tcp = true,
            quic = true,
            port = 22000,
            relays = listOf(
                ListenAddressListItem(
                    enabled = true,
                    uri = "dynamic+https://relays.syncthing.net/endpoint"
                )
            )
        ))
    }

    fun loadSavedDiscoverySetting(): List<ListenAddressListItem> {
        return runCatching {
            Json.decodeFromString<List<ListenAddressListItem>>(
                appSettingsStorage.getString( AppSettingPrivateStorage.KEY_DISCOVERY_PREFERENCE)!!
            )
        }.getOrDefault( listOf(
            ListenAddressListItem(
                enabled = true, uri = "https://discovery-announce-v4.syncthing.net/v2/?nolookup"
            ),
            ListenAddressListItem(
                enabled = true, uri = "https://discovery-announce-v6.syncthing.net/v2/?nolookup"
            ),
            ListenAddressListItem(
                enabled = true, uri = "https://discovery-lookup.syncthing.net/v2/?noannounce"
            ),
        ))
    }

    fun getListenAddressStringFromUnsaved(listenAddressSetting: ListenAddressSetting ): String {
        val result = mutableListOf<String>()

        if ( ifStackFits( actualListenStack, UriProtocolStack.IPV4 ) ) {
            if ( listenAddressSetting.tcp ) result += ("tcp4://0.0.0.0:" + listenAddressSetting.port.toString())
            if ( listenAddressSetting.quic ) result += ("quic4://0.0.0.0:" + listenAddressSetting.port.toString())
        }
        if ( ifStackFits( actualListenStack, UriProtocolStack.IPV6 ) ) {
            if ( listenAddressSetting.tcp ) result += ("tcp6://[::]:" + listenAddressSetting.port.toString())
            if ( listenAddressSetting.quic ) result += ("quic6://[::]:" + listenAddressSetting.port.toString())
        }

        for (item in listenAddressSetting.relays) {
            if (item.enabled) { result += item.uri }
        }

        return result.joinToString(", ")
    }

    fun getDiscoveryAddressStringFromUnsaved(listenAddressListItem: List<ListenAddressListItem>): String {
        val result = mutableListOf<String>()

        for (item in listenAddressListItem) {
            if (item.enabled) { result += item.uri }
        }

        return result.joinToString(", ")
    }

    fun listenRelayAddressValidator(address: String): Boolean {
        return ( address.startsWith("relay://") || address.startsWith("dynamic+https://") )
    }

    fun ifStackFits(parent: UriProtocolStack, item: UriProtocolStack): Boolean {
        return when {
            ( parent == UriProtocolStack.DUAL ) || ( item == UriProtocolStack.DUAL ) || ( parent == item ) -> true
            else -> false
        }
    }

    companion object {
        fun factory(
            controller: SettingController,
            appSettingsStorage: AppSettingPrivateStorage,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingViewModel(
                    controller = controller,
                    appSettingsStorage = appSettingsStorage,
                )
            }
        }
    }
}

private fun ListenAddressSetting.trim(): ListenAddressSetting =
    copy(
        relays = relays.map { relayItem ->
            relayItem.copy(uri = ( rebuildUriOrNot(relayItem.uri)) )
        }
    )

private fun List<ListenAddressListItem>.trim(): List<ListenAddressListItem> =
    map { item ->
        item.copy(
            uri = rebuildUriOrNot(item.uri),
        )
    }

private fun rebuildUriOrNot(raw: String): String {
    val parsed = raw.trim().toKmpUriOrNull() ?: return raw
    val scheme = parsed.scheme?.lowercase() ?: return raw
    val authority = parsed.encodedAuthority ?: return raw

    if (!parsed.isHierarchical || parsed.host.isNullOrBlank()) {
        return raw
    }

    return Uri.Builder()
        .scheme(scheme)
        .encodedAuthority(authority)
        .path(parsed.path)
        .encodedQuery(parsed.encodedQuery)
        .fragment(parsed.fragment)
        .build()
        .toString()
}

private fun SettingUiState.hasUnsavedChanges(): Boolean =
    settingRaw != null && formState != settingRaw.toFormState()

private fun SettingConfiguration.toFormState(): SettingFormState {
    val defaults = SettingConfiguration.startupDefaults()
    return SettingFormState(
        deviceName = deviceName,
        minHomeDiskFree = minHomeDiskFree.editableStringUnless(defaults.minHomeDiskFree),
        minHomeDiskFreeUnit = minHomeDiskFreeUnit,
        usageReportingEnabled = usageReportingEnabled,
        guiListenAddress = guiListenAddress,
        guiPort = guiPort.editableStringUnless(defaults.guiPort),
        guiPortConflictBehavior = guiPortConflictBehavior,
        guiAuthenticationEnabled = guiAuthenticationEnabled,
        guiUser = guiUser,
        newGuiPassword = "",
        guiTheme = guiTheme,
        maxSendKiBPerSecond = maxSendKiBPerSecond.editableStringUnless(defaults.maxSendKiBPerSecond),
        maxReceiveKiBPerSecond = maxReceiveKiBPerSecond.editableStringUnless(defaults.maxReceiveKiBPerSecond),
        reconnectionIntervalSeconds =
            reconnectionIntervalSeconds.editableStringUnless(defaults.reconnectionIntervalSeconds),
        limitBandwidthInLan = limitBandwidthInLan,
        globalDiscoveryEnabled = globalDiscoveryEnabled,
        localDiscoveryEnabled = localDiscoveryEnabled,
        localDiscoveryPort = localDiscoveryPort.editableStringUnless(defaults.localDiscoveryPort),
        localDiscoveryMulticastAddress =
            localDiscoveryMulticastAddress.takeUnless { it == defaults.localDiscoveryMulticastAddress }.orEmpty(),
        announceLanAddresses = announceLanAddresses,
        natEnabled = natEnabled,
        relaysEnabled = relaysEnabled,
        alwaysLocalNetworks = alwaysLocalNetworks.editableStringUnless(defaults.alwaysLocalNetworks),
        connectionLimitMax = connectionLimitMax.editableStringUnless(defaults.connectionLimitMax),
    )
}

private fun Double.editableString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Double.editableStringUnless(defaultValue: Double): String =
    takeUnless { it == defaultValue }?.editableString().orEmpty()

private fun Int.editableStringUnless(defaultValue: Int): String =
    takeUnless { it == defaultValue }?.toString().orEmpty()

private fun List<String>.editableStringUnless(defaultValue: List<String>): String =
    takeUnless { it == defaultValue }?.joinToString("\n").orEmpty()

private fun SettingFormState.isValid(
    setting: SettingConfiguration,
    accessMode: SettingAccessMode?,
): Boolean = validationError(setting, accessMode) == null

private fun SettingFormState.toConfiguration(setting: SettingConfiguration): SettingConfiguration {
    val defaults = SettingConfiguration.startupDefaults()
    return setting.copy(
        deviceName = deviceName,
        minHomeDiskFree = minHomeDiskFree.toDoubleOrNull() ?: defaults.minHomeDiskFree,
        minHomeDiskFreeUnit = minHomeDiskFreeUnit,
        usageReportingEnabled = usageReportingEnabled,
        guiListenAddress = guiListenAddress,
        guiPort = guiPort.toIntOrNull() ?: defaults.guiPort,
        guiPortConflictBehavior = guiPortConflictBehavior,
        guiAuthenticationEnabled = guiAuthenticationEnabled,
        guiUser = guiUser,
        newGuiPassword = if (guiAuthenticationEnabled) newGuiPassword else "",
        guiTheme = guiTheme,
        listenAddresses = listenAddresses.toValues(),
        maxSendKiBPerSecond = maxSendKiBPerSecond.toIntOrNull() ?: defaults.maxSendKiBPerSecond,
        maxReceiveKiBPerSecond = maxReceiveKiBPerSecond.toIntOrNull() ?: defaults.maxReceiveKiBPerSecond,
        reconnectionIntervalSeconds =
            reconnectionIntervalSeconds.toIntOrNull() ?: defaults.reconnectionIntervalSeconds,
        limitBandwidthInLan = limitBandwidthInLan,
        globalDiscoveryEnabled = globalDiscoveryEnabled,
        globalDiscoveryServers = globalDiscoveryServers.toValues(),
        localDiscoveryEnabled = localDiscoveryEnabled,
        localDiscoveryPort = localDiscoveryPort.toIntOrNull() ?: defaults.localDiscoveryPort,
        localDiscoveryMulticastAddress =
            localDiscoveryMulticastAddress.ifBlank { defaults.localDiscoveryMulticastAddress },
        announceLanAddresses = announceLanAddresses,
        natEnabled = natEnabled,
        relaysEnabled = relaysEnabled,
        alwaysLocalNetworks = alwaysLocalNetworks.toValues().ifEmpty { defaults.alwaysLocalNetworks },
        connectionLimitMax = connectionLimitMax.toIntOrNull() ?: defaults.connectionLimitMax,
    )
}

private fun String.toValues(): List<String> = split(',', '\n')
    .map(String::trim)
    .filter(String::isNotBlank)

private fun SettingConfiguration.trim(): SettingConfiguration = copy(
    deviceName = deviceName.trim(),
    guiListenAddress = guiListenAddress.trim().removePrefix("[").removeSuffix("]"),
    guiUser = guiUser.trim(),
    listenAddresses = listenAddresses.normalizedValues(),
    globalDiscoveryServers = globalDiscoveryServers.normalizedValues(),
    localDiscoveryMulticastAddress = localDiscoveryMulticastAddress.trim(),
    alwaysLocalNetworks = alwaysLocalNetworks.normalizedValues(),
)

private fun SettingFormState.validationError(
    setting: SettingConfiguration,
    accessMode: SettingAccessMode?,
): String? {
    if (accessMode == null) return "设置尚未加载"

    if (guiPort.isNotBlank() && guiPort.toIntOrNull() == null) return "WebUI 端口必须是整数"
    if (guiPort.toIntOrNull()?.let { it !in 1..65535 } == true) {
        return "WebUI 端口必须在 1 到 65535 之间"
    }
    if (accessMode == SettingAccessMode.STARTUP_ONLY) return null

    val configuration = toConfiguration(setting).trim()
    if (deviceName.isBlank()) return "设备名不能为空"
    if (minHomeDiskFree.isNotBlank() && minHomeDiskFree.toDoubleOrNull() == null) {
        return "最低磁盘剩余空间必须是数字"
    }
    if (!configuration.minHomeDiskFree.isFinite() || configuration.minHomeDiskFree < 0) {
        return "最低磁盘剩余空间必须是非负数"
    }
    if (configuration.minHomeDiskFreeUnit == SettingConfiguration.DiskSpaceUnit.PERCENT &&
        configuration.minHomeDiskFree > 100
    ) {
        return "最低磁盘剩余空间使用百分比时不能超过 100%"
    }
    if (guiAuthenticationEnabled && guiUser.isBlank()) {
        return "启用 GUI 身份验证时，用户名不能为空"
    }
    if (guiAuthenticationEnabled && !setting.guiPasswordConfigured && newGuiPassword.isBlank()) {
        return "密码不能为空"
    }
    if (guiAuthenticationEnabled && newGuiPassword.isNotEmpty() && newGuiPassword.isBlank()) {
        return "身份验证密码不能仅包含空字符"
    }
    if (guiAuthenticationEnabled && newGuiPassword.encodeToByteArray().size > 72) {
        return "身份验证密码不能超过 72 字节"
    }
    if (maxSendKiBPerSecond.isNotBlank() && maxSendKiBPerSecond.toIntOrNull() == null) {
        return "上传限速必须是整数"
    }
    if (maxReceiveKiBPerSecond.isNotBlank() && maxReceiveKiBPerSecond.toIntOrNull() == null) {
        return "下载限速必须是整数"
    }
    if (configuration.maxSendKiBPerSecond < 0 || configuration.maxReceiveKiBPerSecond < 0) {
        return "上传和下载速率限制必须是非负整数"
    }
    if (reconnectionIntervalSeconds.isNotBlank() && reconnectionIntervalSeconds.toIntOrNull() == null) {
        return "重新连接间隔必须是整数"
    }
    if (configuration.reconnectionIntervalSeconds < 0) {
        return "重新连接间隔必须是非负整数"
    }
    if (localDiscoveryPort.isNotBlank() && localDiscoveryPort.toIntOrNull() == null) {
        return "本地发现端口必须是整数"
    }
    if (configuration.localDiscoveryPort !in 1..65535) {
        return "本地发现端口必须在 1 到 65535 之间"
    }
    if (connectionLimitMax.isNotBlank() && connectionLimitMax.toIntOrNull() == null) {
        return "最大连接数必须是整数"
    }
    if (configuration.connectionLimitEnough !in 0..1023 || configuration.connectionLimitMax !in 0..1023) {
        return "连接数量限制必须在 0 到 1023 之间"
    }
    if (configuration.connectionLimitMax in 1..<configuration.connectionLimitEnough) {
        return "足够连接数不能大于最大连接数"
    }
    return null
}

private fun moe.https.syncthing.core.SettingSaveResult.successMessage(): String = when (accessMode) {
    SettingAccessMode.REST -> if (restartRequired) {
        "设置已保存，部分更改将在重启后生效。"
    } else {
        "设置已保存。"
    }
    SettingAccessMode.CONFIG_FILE -> "配置文件已更新，将在启动时生效。"
    SettingAccessMode.STARTUP_ONLY -> "启动参数已保存，将在首次启动时使用。"
}

private fun List<String>.normalizedValues(): List<String> = map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

private fun Throwable.userMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
