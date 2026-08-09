package moe.https.syncthing.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.storage.ProtocolStack
import moe.https.syncthing.ui.component.ImputableValueRow
import moe.https.syncthing.ui.component.InfoSwitch
import moe.https.syncthing.ui.component.InfoSwitchCard
import moe.https.syncthing.ui.component.MessageCard
import moe.https.syncthing.ui.component.TextWithOptionField
import moe.https.syncthing.ui.model.SettingFormState
import moe.https.syncthing.ui.model.SettingUiState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingScreen(
    uiState: SettingUiState,
    onFormChange: (SettingFormState) -> Unit,
    modifier: Modifier = Modifier,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    protocolStack: ProtocolStack,
    onProtocolStackChange: (ProtocolStack) -> Unit,
    onChangeToAbout: () -> Unit,
    onChangeToLicence: () -> Unit,
) {
    var developerModeVisible by remember { mutableStateOf(developerModeEnabled) }
    val settingAvailable = uiState.setting != null &&
        uiState.formState != null &&
        uiState.accessMode != null
    val displayedSetting = uiState.setting ?: SettingConfiguration.startupDefaults(
        guiListenAddress = protocolStack.guiListenAddress,
    )
    val displayedFormState = uiState.formState ?: SettingFormState()
    val displayedAccessMode = uiState.accessMode ?: SettingAccessMode.STARTUP_ONLY

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        when {
            uiState.isLoading && !settingAvailable -> MessageCard(
                title = "正在读取设置",
                message = "正在读取核心状态与本地配置文件…",
            )

            uiState.errorMessage != null && !settingAvailable -> MessageCard(
                title = "读取失败",
                message = uiState.errorMessage,
                isError = true,
            )

            uiState.hasLoaded && uiState.setting == null -> MessageCard(
                title = "暂无设置",
                message = "没有可用的设置项。",
                isError = true,
            )

            uiState.hasLoaded && !settingAvailable -> MessageCard(
                title = "设置不可用",
                message = "没有可用的设置方式。",
                isError = true,
            )

            !settingAvailable -> MessageCard(
                title = "设置尚未加载",
                message = "正在等待读取核心状态与本地配置文件。",
            )

            else -> {
                MessageCard(
                    title = displayedAccessMode.title,
                    message = if ( uiState.restartRequired && uiState.successMessage == null ) {
                        "设置已保存，将在下次启动核心后生效。"
                    } else displayedAccessMode.caption
                )
            }
        }

        SettingForm(
            setting = displayedSetting,
            formState = displayedFormState,
            accessMode = displayedAccessMode,
            isSaving = uiState.isSaving,
            isFormValid = uiState.isFormValid,
            onFormChange = onFormChange,
            developerModeEnabled = developerModeEnabled,
            onModifyDeveloperMode = onModifyDeveloperMode,
            developerModeVisible = developerModeVisible,
            protocolStack = protocolStack,
            onProtocolStackChange = onProtocolStackChange,
            settingAvailable = settingAvailable,
        )

        InfoSwitchCard( title = "关于" ) {
            ArrowPreference(
                title = "关于",
                summary = "关于此 App",
                onClick = { onChangeToAbout() },
            )

            ArrowPreference(
                title = "开源许可",
                summary = "使用到的第三方开源项目",
                onClick = { onChangeToLicence() },
            )
        }
    }
}

@Suppress("UnrememberedMutableState")
@Composable
private fun SettingForm(
    setting: SettingConfiguration,
    formState: SettingFormState,
    accessMode: SettingAccessMode,
    isSaving: Boolean,
    isFormValid: Boolean,
    onFormChange: (SettingFormState) -> Unit,
    developerModeEnabled: Boolean,
    developerModeVisible: Boolean,
    onModifyDeveloperMode: () -> Unit,
    protocolStack: ProtocolStack,
    onProtocolStackChange: (ProtocolStack) -> Unit,
    settingAvailable: Boolean,
) {
    val startupOnly = accessMode == SettingAccessMode.STARTUP_ONLY
    val startupSettingEnabled = settingAvailable && !isSaving
    val fullSettingEnabled = startupSettingEnabled && !startupOnly

    if (settingAvailable && !isFormValid) {
        Text(
            text = "部分设置项设定了无效值。",
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    InfoSwitchCard(title = "常规") {
        ImputableValueRow(
            value = formState.deviceName,
            onValueChange = { onFormChange(formState.copy(deviceName = it)) },
            label = "设备名",
            valueLabel = "必填",
            allowEdit = fullSettingEnabled,
        )
        InfoSwitch(
            title = "匿名使用报告",
            summary = "允许 Syncthing 发送匿名使用报告。",
            checked = formState.usageReportingEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(usageReportingEnabled = it)) },
        )
        if (developerModeVisible) {
            InfoSwitch(
                title = "开发者模式",
                summary = "可能需要重启生效",
                checked = developerModeEnabled,
                enabled = true,
                onCheckedChange = { onModifyDeveloperMode() }
            )
        }
    }

    InfoSwitchCard(title = "磁盘与存储") {
        ArrowPreference(
            title = "存储权限",
            onClick = null,
            enabled = false,
        )

        TextWithOptionField(
            value = formState.minHomeDiskFree,
            title = "最低磁盘剩余空间",
            onValueChange = { onFormChange(formState.copy(minHomeDiskFree = it)) },
            label = "1",
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            items = SettingConfiguration.DiskSpaceUnit.entries.map { it.displayName },
            selectedIndex = formState.minHomeDiskFreeUnit.ordinal,
            enabled = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            onSelectedIndexChange = { index ->
                onFormChange(
                    formState.copy(
                        minHomeDiskFreeUnit = SettingConfiguration.DiskSpaceUnit.entries[index],
                    ),
                )
            },
        )
    }

    InfoSwitchCard(title = "WebUI") {
        ImputableValueRow(
            value = formState.guiPort,
            onValueChange = { onFormChange(formState.copy(guiPort = it)) },
            label = "端口",
            valueLabel = "8384",
            allowEdit = startupSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        WindowDropdownPreference(
            title = "端口自增",
            items = SettingConfiguration.GuiPortConflictBehavior.entries.map { it.displayName },
            selectedIndex = formState.guiPortConflictBehavior.ordinal,
            enabled = startupSettingEnabled,
            onSelectedIndexChange = { index ->
                onFormChange(
                    formState.copy(
                        guiPortConflictBehavior = SettingConfiguration.GuiPortConflictBehavior.entries[index],
                    ),
                )
            },
        )

        InfoSwitch(
            title = "身份验证",
            summary = "使用用户名和密码登录 WebUI。",
            checked = formState.guiAuthenticationEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(guiAuthenticationEnabled = it)) },
        )

        AnimatedVisibility(
            visible = !settingAvailable || formState.guiAuthenticationEnabled,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300)
            ),
        ) {
            Column {
                ImputableValueRow(
                    value = formState.guiUser,
                    onValueChange = { onFormChange(formState.copy(guiUser = it)) },
                    label = "身份验证用户",
                    valueLabel = "必填",
                    allowEdit = fullSettingEnabled,
                )
                ImputableValueRow(
                    value = formState.newGuiPassword,
                    onValueChange = { onFormChange(formState.copy(newGuiPassword = it)) },
                    label = "身份验证密码",
                    valueLabel = if (setting.guiPasswordConfigured) "***" else "必填",
                    allowEdit = fullSettingEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }

        WindowDropdownPreference(
            title = "WebUI 主题",
            items = SettingConfiguration.GuiTheme.entries.map { it.displayName },
            selectedIndex = formState.guiTheme.ordinal,
            enabled = fullSettingEnabled,
            onSelectedIndexChange = { index ->
                onFormChange(
                    formState.copy(guiTheme = SettingConfiguration.GuiTheme.entries[index]),
                )
            },
        )
    }

    InfoSwitchCard(title = "连接") {
        OverlayDropdownPreference(
            title = "协议栈",
            summary = "连接使用的协议栈",
            items = ProtocolStack.entries.map { it.displayName },
            selectedIndex = protocolStack.ordinal,
            enabled = startupSettingEnabled,
            onSelectedIndexChange = { index ->
                val selectedStack = ProtocolStack.entries[index]
                onProtocolStackChange(selectedStack)
                onFormChange(
                    formState.copy(guiListenAddress = selectedStack.guiListenAddress),
                )
            },
            onExpandedChange = {},
        )
        // TODO: 监听/relay等遵守协议栈
        ImputableValueRow(
            value = formState.listenAddresses,
            onValueChange = { onFormChange(formState.copy(listenAddresses = it)) },
            label = "监听地址",
            valueLabel = "default",
            singleLine = false,
            allowEdit = fullSettingEnabled,
        )
        ImputableValueRow(
            value = formState.maxSendKiBPerSecond,
            onValueChange = { onFormChange(formState.copy(maxSendKiBPerSecond = it)) },
            label = "上传限速（KiB/s）",
            valueLabel = "无限制",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ImputableValueRow(
            value = formState.maxReceiveKiBPerSecond,
            onValueChange = { onFormChange(formState.copy(maxReceiveKiBPerSecond = it)) },
            label = "下载限速（KiB/s）",
            valueLabel = "无限制",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ImputableValueRow(
            value = formState.reconnectionIntervalSeconds,
            onValueChange = { onFormChange(formState.copy(reconnectionIntervalSeconds = it)) },
            label = "重连间隔（s）",
            valueLabel = "20",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        InfoSwitch(
            title = "局域网限速",
            summary = "对局域网设备启用限速",
            checked = formState.limitBandwidthInLan,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(limitBandwidthInLan = it)) },
        )
    }

    InfoSwitchCard(title = "设备发现") {
        InfoSwitch(
            title = "广域网设备发现",
            summary = "通过发现服务器查找其他设备。",
            checked = formState.globalDiscoveryEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(globalDiscoveryEnabled = it)) },
        )

        AnimatedVisibility(
            visible = !settingAvailable || formState.globalDiscoveryEnabled,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300)
            ),
        ) {
            Column {
                InfoSwitch(
                    title = "公布局域网地址",
                    summary = "向发现服务器公布局域网地址。",
                    checked = formState.announceLanAddresses,
                    enabled = fullSettingEnabled,
                    onCheckedChange = { onFormChange(formState.copy(announceLanAddresses = it)) },
                )

                ImputableValueRow(
                    value = formState.globalDiscoveryServers,
                    onValueChange = { onFormChange(formState.copy(globalDiscoveryServers = it)) },
                    label = "广域网发现服务器",
                    valueLabel = "default",
                    singleLine = false,
                    allowEdit = fullSettingEnabled,
                )
            }
        }

        InfoSwitch(
            title = "局域网设备发现",
            summary = "通过组播查找其他设备。",
            checked = formState.localDiscoveryEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(localDiscoveryEnabled = it)) },
        )

        AnimatedVisibility(
            visible = !settingAvailable || formState.localDiscoveryEnabled,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300)
            ),
        ) {
            Column {
                ImputableValueRow(
                    value = formState.localDiscoveryPort,
                    onValueChange = { onFormChange(formState.copy(localDiscoveryPort = it)) },
                    label = "IPv4 组播监听端口",
                    valueLabel = "21027",
                    allowEdit = fullSettingEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ImputableValueRow(
                    value = formState.localDiscoveryMulticastAddress,
                    onValueChange = {
                        onFormChange(formState.copy(localDiscoveryMulticastAddress = it))
                    },
                    label = "IPv6 组播地址",
                    valueLabel = "[ff12::8384]:21027",
                    singleLine = false,
                    allowEdit = fullSettingEnabled,
                )
            }
        }
    }

    InfoSwitchCard(title = "网络") {
        InfoSwitch(
            title = "NAT 穿透",
            summary = "尝试通过路由器自动映射设备连接端口。",
            checked = formState.natEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(natEnabled = it)) },
        )
        InfoSwitch(
            title = "使用中继",
            summary = "直接连接不可用时，允许通过 Syncthing 中继建立连接。",
            checked = formState.relaysEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { onFormChange(formState.copy(relaysEnabled = it)) },
        )
        ImputableValueRow(
            value = formState.alwaysLocalNetworks,
            onValueChange = { onFormChange(formState.copy(alwaysLocalNetworks = it)) },
            label = "额外局域网网段",
            valueLabel = "CIDR，每行一个",
            singleLine = false,
            allowEdit = fullSettingEnabled,
        )
        ImputableValueRow(
            value = formState.connectionLimitMax,
            onValueChange = { onFormChange(formState.copy(connectionLimitMax = it)) },
            label = "最大连接数",
            valueLabel = "0 为不限",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }

    InfoSwitchCard(title = "后台运行") {
        ArrowPreference(
            title = "当连接到网络...",
            onClick = null,
            enabled = false,
        )
        ArrowPreference(
            title = "当电池状态...",
            onClick = null,
            enabled = false,
        )
        ArrowPreference(
            title = "特定时间段...",
            onClick = null,
            enabled = false,
        )
        ArrowPreference(
            title = "后台运行权限",
            onClick = null,
            enabled = false,
        )
    }
}
