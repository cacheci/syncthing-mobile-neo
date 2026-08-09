package moe.https.syncthing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.core.SettingController
import moe.https.syncthing.ui.model.SettingFormState
import moe.https.syncthing.ui.model.SettingUiState
import kotlin.time.Duration.Companion.milliseconds

class SettingViewModel(
    private val controller: SettingController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = mutableUiState.asStateFlow()
    private val operationMutex = Mutex()

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
                            state.formState ?: snapshot.configuration.toFormState()
                        } else {
                            snapshot.configuration.toFormState()
                        }
                        state.copy(
                            setting = snapshot.configuration,
                            formState = formState,
                            accessMode = snapshot.accessMode,
                            isLoading = false,
                            isFormValid = formState.isValid(snapshot.accessMode),
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

    fun updateForm(formState: SettingFormState) {
        mutableUiState.update {
            if (it.isSaving || it.formState == null) {
                it
            } else {
                it.copy(
                    formState = formState,
                    isFormValid = formState.isValid(it.accessMode),
                )
            }
        }
    }

    fun save() {
        val state = mutableUiState.value
        val setting = state.setting
        val formState = state.formState
        if (setting == null || formState == null) {
            mutableUiState.update {
                it.copy(errorMessage = "设置尚未加载", successMessage = null)
            }
            return
        }

        val configuration = formState.toConfiguration(setting)
        val normalizedConfiguration = configuration.normalized()
        val accessMode = state.accessMode
        val validationError = when (accessMode) {
            SettingAccessMode.STARTUP_ONLY -> normalizedConfiguration.startupValidationError()
            SettingAccessMode.REST,
            SettingAccessMode.CONFIG_FILE -> normalizedConfiguration.validationError()
            null -> "设置尚未加载"
        }
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
                            normalizedConfiguration.guiAuthenticationEnabled
                        },
                        newGuiPassword = "",
                    )
                    val savedFormState = savedConfiguration.toFormState()
                    delay(1000.milliseconds)
                    mutableUiState.update {
                        it.copy(
                            setting = savedConfiguration,
                            formState = savedFormState,
                            accessMode = result.accessMode,
                            isSaving = false,
                            isFormValid = savedFormState.isValid(result.accessMode),
                            hasLoaded = true,
                            successMessage = result.successMessage(),
                            restartRequired = result.restartRequired,
                        )
                    }
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

    companion object {
        fun factory(controller: SettingController): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingViewModel(controller)
            }
        }
    }
}

private fun SettingUiState.hasUnsavedChanges(): Boolean =
    formState != null && formState != setting?.toFormState()

private fun SettingConfiguration.toFormState(): SettingFormState = SettingFormState(
    deviceName = deviceName,
    minHomeDiskFree = minHomeDiskFree.editableString(),
    minHomeDiskFreeUnit = minHomeDiskFreeUnit,
    usageReportingEnabled = usageReportingEnabled,
    guiListenAddress = guiListenAddress,
    guiPort = guiPort.toString(),
    guiPortConflictBehavior = guiPortConflictBehavior,
    guiAuthenticationEnabled = guiAuthenticationEnabled,
    guiUser = guiUser,
    newGuiPassword = "",
    guiTheme = guiTheme,
    listenAddresses = listenAddresses.joinToString("\n"),
    maxSendKiBPerSecond = maxSendKiBPerSecond.toString(),
    maxReceiveKiBPerSecond = maxReceiveKiBPerSecond.toString(),
    reconnectionIntervalSeconds = reconnectionIntervalSeconds.toString(),
    limitBandwidthInLan = limitBandwidthInLan,
    globalDiscoveryEnabled = globalDiscoveryEnabled,
    globalDiscoveryServers = globalDiscoveryServers.joinToString("\n"),
    localDiscoveryEnabled = localDiscoveryEnabled,
    localDiscoveryPort = localDiscoveryPort.toString(),
    localDiscoveryMulticastAddress = localDiscoveryMulticastAddress,
    announceLanAddresses = announceLanAddresses,
    natEnabled = natEnabled,
    relaysEnabled = relaysEnabled,
    alwaysLocalNetworks = alwaysLocalNetworks.joinToString("\n"),
    connectionLimitMax = connectionLimitMax.toString(),
    allowGuiListenNonLocal = allowGuiListenNonLocal,
)

private fun Double.editableString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun SettingFormState.isValid(accessMode: SettingAccessMode?): Boolean {
    if (accessMode == null) return false
    if (accessMode == SettingAccessMode.STARTUP_ONLY) return true

    return listOf(
        guiPort.toIntOrNull() ?: 8384,
        reconnectionIntervalSeconds.toIntOrNull() ?: 20,
        localDiscoveryPort.toIntOrNull() ?: 0,
        connectionLimitMax.toIntOrNull() ?: 0,
    ).all { it in 0..65535 } && listOf(
        maxSendKiBPerSecond.toIntOrNull() ?: 0,
        maxReceiveKiBPerSecond.toIntOrNull() ?: 0,
    ).all { it >= 0 } && (
        (minHomeDiskFree.toDoubleOrNull() ?: 1.0) >= 0.0
    )
}

private fun SettingFormState.toConfiguration(setting: SettingConfiguration): SettingConfiguration = setting.copy(
    deviceName = deviceName,
    minHomeDiskFree = minHomeDiskFree.toDoubleOrNull() ?: 1.0,
    minHomeDiskFreeUnit = minHomeDiskFreeUnit,
    usageReportingEnabled = usageReportingEnabled,
    guiListenAddress = guiListenAddress,
    guiPort = guiPort.toIntOrNull() ?: 8384,
    guiPortConflictBehavior = guiPortConflictBehavior,
    guiAuthenticationEnabled = guiAuthenticationEnabled,
    guiUser = guiUser,
    newGuiPassword = newGuiPassword,
    guiTheme = guiTheme,
    listenAddresses = listenAddresses.toValues(),
    maxSendKiBPerSecond = maxSendKiBPerSecond.toIntOrNull() ?: 0,
    maxReceiveKiBPerSecond = maxReceiveKiBPerSecond.toIntOrNull() ?: 0,
    reconnectionIntervalSeconds = reconnectionIntervalSeconds.toIntOrNull() ?: 20,
    limitBandwidthInLan = limitBandwidthInLan,
    globalDiscoveryEnabled = globalDiscoveryEnabled,
    globalDiscoveryServers = globalDiscoveryServers.toValues(),
    localDiscoveryEnabled = localDiscoveryEnabled,
    localDiscoveryPort = localDiscoveryPort.toIntOrNull() ?: 0,
    localDiscoveryMulticastAddress = localDiscoveryMulticastAddress,
    announceLanAddresses = announceLanAddresses,
    natEnabled = natEnabled,
    relaysEnabled = relaysEnabled,
    alwaysLocalNetworks = alwaysLocalNetworks.toValues(),
    connectionLimitMax = connectionLimitMax.toIntOrNull() ?: 0,
    allowGuiListenNonLocal = allowGuiListenNonLocal,
)

private fun String.toValues(): List<String> = split(',', '\n')
    .map(String::trim)
    .filter(String::isNotBlank)

private fun SettingConfiguration.normalized(): SettingConfiguration = copy(
    deviceName = deviceName.trim(),
    guiListenAddress = guiListenAddress.trim().removePrefix("[").removeSuffix("]"),
    guiUser = guiUser.trim(),
    listenAddresses = listenAddresses.normalizedValues(),
    globalDiscoveryServers = globalDiscoveryServers.normalizedValues(),
    localDiscoveryMulticastAddress = localDiscoveryMulticastAddress.trim(),
    alwaysLocalNetworks = alwaysLocalNetworks.normalizedValues(),
)

private fun SettingConfiguration.validationError(): String? = when {
    deviceName.isBlank() -> "设备名不能为空"
    !minHomeDiskFree.isFinite() || minHomeDiskFree < 0 -> "最低磁盘剩余空间必须是非负数"
    minHomeDiskFreeUnit == SettingConfiguration.DiskSpaceUnit.PERCENT && minHomeDiskFree > 100 -> {
        "最低磁盘剩余空间使用百分比时不能超过 100%"
    }
    guiListenAddress !in SUPPORTED_GUI_LISTEN_ADDRESSES && !allowGuiListenNonLocal -> {
        "监听地址默认仅支持本机地址。"
    }
    guiPort !in 1..65535 -> "GUI 端口必须在 1 到 65535 之间"
    guiListenAddress in WILDCARD_GUI_LISTEN_ADDRESSES && !guiAuthenticationEnabled -> {
        "WebUI 监听所有网络接口时必须启用身份验证"
    }
    guiAuthenticationEnabled && guiUser.isBlank() -> "启用 GUI 身份验证时，用户名不能为空"
    guiAuthenticationEnabled && !guiPasswordConfigured && newGuiPassword.isBlank() -> {
        "密码不能为空"
    }
    newGuiPassword.isNotEmpty() && newGuiPassword.isBlank() -> "身份验证密码不能仅包含空字符"
    newGuiPassword.encodeToByteArray().size > 72 -> "身份验证密码不能超过 72 字节"
    listenAddresses.isEmpty() -> "至少需要一个设备连接监听地址"
    maxSendKiBPerSecond < 0 || maxReceiveKiBPerSecond < 0 -> "上传和下载速率限制必须是非负整数"
    reconnectionIntervalSeconds <= 0 -> "重新连接间隔必须大于 0 秒"
    globalDiscoveryEnabled && globalDiscoveryServers.isEmpty() -> "启用全局发现时，至少需要一个发现服务器"
    localDiscoveryPort !in 1..65535 -> "本地发现端口必须在 1 到 65535 之间"
    localDiscoveryEnabled && localDiscoveryMulticastAddress.isBlank() -> "启用本地发现时，IPv6 组播地址不能为空"
    connectionLimitEnough < 0 || connectionLimitMax < 0 -> "连接数量限制必须是非负整数"
    connectionLimitMax in 1..<connectionLimitEnough -> {
        "足够连接数不能大于最大连接数"
    }
    else -> null
}

private fun SettingConfiguration.startupValidationError(): String? = when {
    guiListenAddress !in SUPPORTED_GUI_LISTEN_ADDRESSES && !allowGuiListenNonLocal -> {
        "监听地址默认仅支持本机地址。"
    }
    guiPort !in 1..65535 -> "GUI 端口必须在 1 到 65535 之间"
    guiListenAddress in WILDCARD_GUI_LISTEN_ADDRESSES -> {
        "首次生成配置前不能监听所有网络接口；请先启动一次核心并设置 GUI 身份验证"
    }
    else -> null
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

private val SUPPORTED_GUI_LISTEN_ADDRESSES = setOf(
    "127.0.0.1",
    "localhost",
    "0.0.0.0",
    "::1",
    "::",
)

private val WILDCARD_GUI_LISTEN_ADDRESSES = setOf("0.0.0.0", "::")
