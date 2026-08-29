package moe.https.syncthing.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreAvailability
import moe.https.syncthing.core.SettingAccessMode
import moe.https.syncthing.core.SettingConfiguration
import moe.https.syncthing.core.defaultFolderPath
import moe.https.syncthing.platform.FolderPickerResult
import moe.https.syncthing.platform.rememberFolderPicker
import moe.https.syncthing.ui.component.AdaptiveTopAppBar
import moe.https.syncthing.ui.component.CheckableInputValueRow
import moe.https.syncthing.ui.component.InfoSwitch
import moe.https.syncthing.ui.component.InfoSwitchCard
import moe.https.syncthing.ui.component.InputValueRow
import moe.https.syncthing.ui.component.MessageCard
import moe.https.syncthing.ui.component.StatusColor
import moe.https.syncthing.ui.component.TextWithOptionField
import moe.https.syncthing.ui.model.CoreUiState
import moe.https.syncthing.ui.model.SettingFormState
import moe.https.syncthing.ui.model.SettingUiState
import moe.https.syncthing.ui.util.ListenAddressListItem
import moe.https.syncthing.ui.util.SettingProtocolStack
import moe.https.syncthing.ui.util.UriProtocolStack
import moe.https.syncthing.viewmodel.DiscoveryServerPingState
import moe.https.syncthing.viewmodel.SettingViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingScreen(
    uiState: SettingUiState,
    settingViewModel: SettingViewModel,
    modifier: Modifier = Modifier,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    onEditingDiscoverServers: () -> Unit,
    onEditingListenAddresses: () -> Unit,
    onEditingStoragePermission: () -> Unit,
    onEditingCores: () -> Unit,
    onChangeToAbout: () -> Unit,
    onChangeToLicence: () -> Unit,
    onEditingBackgroundPermission: () -> Unit,
    onRedirectingToDeveloperPage: () -> Unit,
) {
    var developerModeVisible by remember { mutableStateOf(developerModeEnabled) }
    val settingAvailable = uiState.settingRaw != null && uiState.accessMode != null
    val displayedSetting = uiState.settingRaw ?: SettingConfiguration.startupDefaults(
        guiListenAddress = settingViewModel.addressProtocolStack.guiListenAddress,
    )
    val displayedAccessMode = uiState.accessMode ?: SettingAccessMode.STARTUP_ONLY

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // TODO: 异步加载
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

            uiState.hasLoaded && uiState.settingRaw == null -> MessageCard(
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
            formState = uiState.formState,
            accessMode = displayedAccessMode,
            isSaving = uiState.isSaving,
            settingViewModel = settingViewModel,
            developerModeEnabled = developerModeEnabled,
            onModifyDeveloperMode = onModifyDeveloperMode,
            developerModeVisible = developerModeVisible,
            settingAvailable = settingAvailable,
            onEditingDiscoverServers = onEditingDiscoverServers,
            onEditingListenAddresses = onEditingListenAddresses,
            onEditingStoragePermission = onEditingStoragePermission,
            onEditingCores = onEditingCores,
            onRedirectingToDeveloperPage = onRedirectingToDeveloperPage,
            onEditingBackgroundPermission = onEditingBackgroundPermission,
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
    settingViewModel: SettingViewModel,
    developerModeEnabled: Boolean,
    developerModeVisible: Boolean,
    onModifyDeveloperMode: () -> Unit,
    onEditingDiscoverServers: () -> Unit,
    onEditingListenAddresses: () -> Unit,
    onEditingStoragePermission: () -> Unit,
    onRedirectingToDeveloperPage: () -> Unit,
    onEditingCores: () -> Unit,
    onEditingBackgroundPermission: () -> Unit,
    settingAvailable: Boolean,
) {
    val startupOnly = accessMode == SettingAccessMode.STARTUP_ONLY
    val startupSettingEnabled = settingAvailable && !isSaving
    val fullSettingEnabled = startupSettingEnabled && !startupOnly

    InfoSwitchCard(title = "常规") {
        InputValueRow(
            value = formState.deviceName,
            onValueChange = { settingViewModel.onFormChange(deviceName = it) },
            label = "设备名",
            valueLabel = "必填",
            allowEdit = fullSettingEnabled,
        )
        InfoSwitch(
            title = "匿名使用报告",
            summary = "允许 Syncthing 发送匿名使用报告。",
            checked = formState.usageReportingEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(usageReportingEnabled = it) },
        )
        if (developerModeVisible) {
            InfoSwitch(
                title = "开发者模式",
                summary = "可能需要重启生效",
                checked = developerModeEnabled,
                enabled = true,
                onCheckedChange = { onModifyDeveloperMode() }
            )
            ArrowPreference(
                title = "开发者选项",
                onClick = onRedirectingToDeveloperPage,
            )
        }
    }

    InfoSwitchCard(title = "磁盘与存储") {
        ArrowPreference(
            title = "存储权限",
            onClick = onEditingStoragePermission,
        )

        TextWithOptionField(
            value = formState.minHomeDiskFree,
            title = "最低磁盘剩余空间",
            onValueChange = { settingViewModel.onFormChange(minHomeDiskFree = it) },
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
                settingViewModel.onFormChange(
                    minHomeDiskFreeUnit = SettingConfiguration.DiskSpaceUnit.entries[index],
                )
            },
        )
    }

    InfoSwitchCard(title = "WebUI") {
        InputValueRow(
            value = formState.guiPort,
            onValueChange = { settingViewModel.onFormChange(guiPort = it) },
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
                settingViewModel.onFormChange(
                    guiPortConflictBehavior = SettingConfiguration.GuiPortConflictBehavior.entries[index],
                )
            },
        )

        InfoSwitch(
            title = "身份验证",
            summary = "使用用户名和密码登录 WebUI。",
            checked = formState.guiAuthenticationEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = {
                settingViewModel.onFormChange(guiAuthenticationEnabled = it)
            },
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
                InputValueRow(
                    value = formState.guiUser,
                    onValueChange = { settingViewModel.onFormChange(guiUser = it) },
                    label = "身份验证用户",
                    valueLabel = "必填",
                    allowEdit = fullSettingEnabled,
                )
                InputValueRow(
                    value = formState.newGuiPassword,
                    onValueChange = { settingViewModel.onFormChange(newGuiPassword = it) },
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
                settingViewModel.onFormChange(
                    guiTheme = SettingConfiguration.GuiTheme.entries[index],
                )
            },
        )
    }

    InfoSwitchCard(title = "连接") {
        ArrowPreference(
            title = "监听地址",
            onClick = onEditingListenAddresses,
            enabled = fullSettingEnabled,
        )
        InputValueRow(
            value = formState.maxSendKiBPerSecond,
            onValueChange = { settingViewModel.onFormChange(maxSendKiBPerSecond = it) },
            label = "上传限速（KiB/s）",
            valueLabel = "无限制",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        InputValueRow(
            value = formState.maxReceiveKiBPerSecond,
            onValueChange = { settingViewModel.onFormChange(maxReceiveKiBPerSecond = it) },
            label = "下载限速（KiB/s）",
            valueLabel = "无限制",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        InputValueRow(
            value = formState.reconnectionIntervalSeconds,
            onValueChange = { settingViewModel.onFormChange(reconnectionIntervalSeconds = it) },
            label = "重连间隔（s）",
            valueLabel = "60",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        InfoSwitch(
            title = "局域网限速",
            summary = "对局域网设备启用限速",
            checked = formState.limitBandwidthInLan,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(limitBandwidthInLan = it) },
        )
    }

    InfoSwitchCard(title = "设备发现") {
        InfoSwitch(
            title = "广域网设备发现",
            summary = "通过发现服务器查找其他设备。",
            checked = formState.globalDiscoveryEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(globalDiscoveryEnabled = it) },
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
                    onCheckedChange = { settingViewModel.onFormChange(announceLanAddresses = it) },
                )

                ArrowPreference(
                    title = "广域网发现服务器",
                    onClick = onEditingDiscoverServers,
                    enabled = fullSettingEnabled,
                )
            }
        }

        InfoSwitch(
            title = "局域网设备发现",
            summary = "通过组播查找其他设备。",
            checked = formState.localDiscoveryEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(localDiscoveryEnabled = it) },
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
                InputValueRow(
                    value = formState.localDiscoveryPort,
                    onValueChange = { settingViewModel.onFormChange(localDiscoveryPort = it) },
                    label = "IPv4 组播监听端口",
                    valueLabel = "21027",
                    allowEdit = fullSettingEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                InputValueRow(
                    value = formState.localDiscoveryMulticastAddress,
                    onValueChange = {
                        settingViewModel.onFormChange(localDiscoveryMulticastAddress = it)
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
        OverlayDropdownPreference(
            title = "协议栈",
            summary = "连接使用的协议栈",
            items = SettingProtocolStack.entries.map { it.displayName },
            selectedIndex = settingViewModel.addressProtocolStack.ordinal,
            enabled = startupSettingEnabled,
            onSelectedIndexChange = { index ->
                val selectedStack = SettingProtocolStack.entries[index]
                settingViewModel.addressProtocolStack = selectedStack
                settingViewModel.actualListenStack = when (selectedStack) {
                        SettingProtocolStack.CUSTOM -> settingViewModel.listenAddressSettingUnsaved.stackPrefer
                        SettingProtocolStack.IPV4 -> UriProtocolStack.IPV4
                        SettingProtocolStack.IPV6 -> UriProtocolStack.IPV6
                        SettingProtocolStack.DUAL -> UriProtocolStack.DUAL
                }
                settingViewModel.onFormChange(
                    guiListenAddress = selectedStack.guiListenAddress,
                )
            },
            onExpandedChange = {},
        )
        InfoSwitch(
            title = "NAT 穿透",
            summary = "尝试通过路由器自动映射设备连接端口。",
            checked = formState.natEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(natEnabled = it) },
        )
        InfoSwitch(
            title = "使用中继",
            summary = "直接连接不可用时，允许通过 Syncthing 中继建立连接。",
            checked = formState.relaysEnabled,
            enabled = fullSettingEnabled,
            onCheckedChange = { settingViewModel.onFormChange(relaysEnabled = it) },
        )
        InputValueRow(
            value = formState.alwaysLocalNetworks,
            onValueChange = { settingViewModel.onFormChange(alwaysLocalNetworks = it) },
            label = "额外局域网网段",
            valueLabel = "CIDR，每行一个",
            singleLine = false,
            allowEdit = fullSettingEnabled,
        )
        InputValueRow(
            value = formState.connectionLimitMax,
            onValueChange = { settingViewModel.onFormChange(connectionLimitMax = it) },
            label = "最大连接数",
            valueLabel = "无限制",
            allowEdit = fullSettingEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }

    InfoSwitchCard(title = "后台运行") {
        ArrowPreference(
            title = "当连接到网络...",
            onClick = null,
            enabled = false, // TODO
        )
        ArrowPreference(
            title = "当电池状态...",
            onClick = null,
            enabled = false, // TODO
        )
        ArrowPreference(
            title = "特定时间段...",
            onClick = null,
            enabled = false, // TODO
        )
        ArrowPreference(
            title = "后台运行权限",
            onClick = onEditingBackgroundPermission,
        )
    }

    InfoSwitchCard(title = "核心设置") {
        ArrowPreference(
            title = "核心选择",
            onClick = onEditingCores,
        )
    }
}

@Composable
internal fun SettingEditListenScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel,
) {
    val isSettingProtocolStackCustom = settingViewModel.addressProtocolStack == SettingProtocolStack.CUSTOM

    Column( modifier = modifier.fillMaxSize().padding(20.dp) ) {
        Card {
            OverlayDropdownPreference(
                title = "协议栈",
                summary = "监听使用的协议栈",
                items = UriProtocolStack.entries.map { it.displayName },
                selectedIndex = settingViewModel.actualListenStack.ordinal,
                enabled = isSettingProtocolStackCustom,
                onSelectedIndexChange = { index ->
                    settingViewModel.actualListenStack = UriProtocolStack.entries.getOrNull(index)!!
                    settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                        stackPrefer = UriProtocolStack.entries.getOrNull(index)!!
                    )
                },
            )
            InfoSwitch(
                title = "TCP",
                checked = settingViewModel.listenAddressSettingUnsaved.tcp,
                enabled = true,
                onCheckedChange = { settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                    tcp = !settingViewModel.listenAddressSettingUnsaved.tcp
                ) },
            )
            InfoSwitch(
                title = "QUIC",
                checked = settingViewModel.listenAddressSettingUnsaved.quic,
                enabled = true,
                onCheckedChange = { settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                    quic = !settingViewModel.listenAddressSettingUnsaved.quic
                ) },
            )
        }

        HorizontalDivider( modifier = Modifier.padding(vertical = 20.dp) )

        Row (
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text (text = "Relay 服务器")
            IconButton(
                onClick = {
                    settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                        relays = settingViewModel.listenAddressSettingUnsaved.relays + ListenAddressListItem( false, "relay://" )
                    )
                },
                content = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        contentDescription = "添加 Relay 服务器",
                        imageVector = MiuixIcons.Add
                    )
                },
            )
        }

        Card {
            LazyColumn (
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed( settingViewModel.listenAddressSettingUnsaved.relays ) { index, item ->
                    CheckableInputValueRow(
                        state = item.enabled,
                        value = item.uri,
                        valueLabel = "必填",
                        singleLine = true,
                        onValueChange = { result ->
                            settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                                relays = settingViewModel.listenAddressSettingUnsaved.relays.mapIndexed { itemIndex, item2 ->
                                    if ( itemIndex == index ) { item2.copy(uri = result) } else item2
                                },
                            )
                        },
                        onStateChange = {
                            settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                                relays = settingViewModel.listenAddressSettingUnsaved.relays.mapIndexed { itemIndex, item2 ->
                                    if ( itemIndex == index ) { item2.copy(enabled = !item2.enabled) } else item2
                                },
                            )
                        },
                        onDelete = {
                            settingViewModel.listenAddressSettingUnsaved = settingViewModel.listenAddressSettingUnsaved.copy(
                                relays = settingViewModel.listenAddressSettingUnsaved.relays.filterIndexed { itemIndex, _ ->
                                    itemIndex != index
                                }
                            )
                        },
                        valueValidator = settingViewModel::listenRelayAddressValidator
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingEditDiscoveryScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel
) {
    Column ( modifier = modifier.fillMaxSize().padding(20.dp) ) {
        Card (modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Syncthing 依赖发现服务器来查找互联网上某处的其他设备。" +
                "任何人都可以运行发现服务器，并将 Syncthing 实例指向该服务器。" +
                "Syncthing 项目也维护着一个供公众使用的全球集群。" +
                "请确保要同步的设备使用了相同的发现服务器，或它们能通过其他方式相互发现，例如设置固定的 IP 或使用局域网设备发现。",
                style = MiuixTheme.textStyles.paragraph,
                modifier = Modifier.padding(16.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Discovery 服务器")
            IconButton(
                onClick = {
                    settingViewModel.discoveryAddressSettingUnsaved +=
                        ListenAddressListItem(false, "")
                },
                content = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        contentDescription = "添加 Discovery 服务器",
                        imageVector = MiuixIcons.Add
                    )
                },
            )
        }

        Card {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                itemsIndexed(settingViewModel.discoveryAddressSettingUnsaved) { index, item ->
                    val pingState = settingViewModel.discoveryServerPingState(item.uri)
                    CheckableInputValueRow(
                        state = item.enabled,
                        value = item.uri,
                        valueLabel = "必填",
                        singleLine = true,
                        onValueChange = { result ->
                            settingViewModel.clearDiscoveryServerPingState(item.uri)
                            settingViewModel.discoveryAddressSettingUnsaved =
                                settingViewModel.discoveryAddressSettingUnsaved.mapIndexed { itemIndex, currentItem ->
                                    if (itemIndex == index) {
                                        currentItem.copy(uri = result)
                                    } else {
                                        currentItem
                                    }
                                }
                        },
                        onStateChange = {
                            settingViewModel.discoveryAddressSettingUnsaved =
                                settingViewModel.discoveryAddressSettingUnsaved.mapIndexed { itemIndex, currentItem ->
                                    if (itemIndex == index) {
                                        currentItem.copy(enabled = !item.enabled)
                                    } else {
                                        currentItem
                                    }
                                }
                        },
                        onDelete = {
                            settingViewModel.clearDiscoveryServerPingState(item.uri)
                            settingViewModel.discoveryAddressSettingUnsaved =
                                    settingViewModel.discoveryAddressSettingUnsaved.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    }
                        },
                        valueValidator = { true }
                    ) {
                        Row (
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = pingState != DiscoveryServerPingState.InProgress &&
                                        item.uri.isNotBlank(),
                                    onClick = { settingViewModel.pingDiscoveryServer(item.uri) },
                                ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "延迟",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                when (pingState) {
                                    DiscoveryServerPingState.InProgress -> "···"
                                    is DiscoveryServerPingState.Success -> "${pingState.latencyMillis} ms"
                                    is DiscoveryServerPingState.Failure -> "失败"
                                    null -> "—"
                                },
                                style = MiuixTheme.textStyles.body2,
                                color = when (pingState) {
                                    is DiscoveryServerPingState.Success -> {
                                        if (pingState.latencyMillis < 100) StatusColor.OK.color else StatusColor.PENDING.color
                                    }
                                    is DiscoveryServerPingState.Failure -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingStoragePermissionPage(
    granted: Boolean,
    onRequestPermission: () -> Unit,
    folderId: String? = null,
    selectedFolderPath: String? = null,
    onFolderPathSelected: ((String?) -> Unit)? = null,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()
    val defaultPath = folderId?.let(::defaultFolderPath)
    var chosenFolderPath by remember(folderId, selectedFolderPath) {
        mutableStateOf(selectedFolderPath?.takeUnless { it == defaultPath })
    }
    var selectedFolderType by remember(folderId, selectedFolderPath) {
        mutableIntStateOf(
            if (selectedFolderPath == null || selectedFolderPath == defaultPath) 0 else 1,
        )
    }
    var folderPickerError by remember { mutableStateOf<String?>(null) }
    val openFolderPicker = rememberFolderPicker { result ->
        when (result) {
            FolderPickerResult.Cancelled -> Unit
            is FolderPickerResult.Error -> folderPickerError = result.message
            is FolderPickerResult.Selected -> {
                chosenFolderPath = result.path
                selectedFolderType = 1
                folderPickerError = null
                onFolderPathSelected?.invoke(result.path)
            }
        }
    }

    LaunchedEffect(folderPickerError) {
        folderPickerError?.let { snackbarHostState.showSnackbar(it) }
        folderPickerError = null
    }

    Scaffold(
        topBar = { AdaptiveTopAppBar(
            title = "存储权限",
            showTopAppBar = true,
            isWideScreen = false,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton( onClick = navigateBack ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                    )
                }
            },
        ) },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { padding ->
        Box (
            modifier = Modifier
                .padding(padding)
                .nestedScroll(
                    scrollBehavior.nestedScrollConnection,
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (!granted || (folderId == null)) {
                    MessageCard(
                        title = "公共目录访问权限",
                        message = "若希望同步公有存储中的文件夹，请启用公共目录访问权限。",
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        InfoSwitch(
                            title = "公共目录访问权限",
                            checked = granted,
                            onCheckedChange = { onRequestPermission() },
                            enabled = true,
                        )
                    }
                }

                if (folderId != null && onFolderPathSelected != null) {
                    InfoSwitchCard(
                        title = "选择路径"
                    ) {
                        RadioButtonPreference(
                            title = "默认路径",
                            summary = defaultPath,
                            selected = selectedFolderType == 0,
                            onClick = {
                                selectedFolderType = 0
                                chosenFolderPath = null
                                onFolderPathSelected.invoke(null)
                            },
                        )

                        RadioButtonPreference(
                            title = "外部路径",
                            enabled = granted,
                            summary = chosenFolderPath,
                            selected = selectedFolderType == 1,
                            onClick = openFolderPicker,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingCoreSelectScreen(
    uiState: CoreUiState,
    snackbarHostState: SnackbarHostState,
    onCoreSelected: (String) -> Unit,
    onImportCore: () -> Unit,
    onCoreDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Syncthing 核心")
            IconButton(
                onClick = onImportCore,
                enabled = uiState.canImportCore,
                content = {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        contentDescription = "导入核心",
                        imageVector = MiuixIcons.Add,
                    )
                },
            )
        }

        if (!uiState.availableCores.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = uiState.availableCores,
                        key = { _, option -> option.id },
                    ) { _, option ->
                        val available = option.availability == CoreAvailability.AVAILABLE
                        val selected = option.id == uiState.selectedCoreId
                        CheckableInputValueRow(
                            state = selected,
                            value = if (option.internal) "内置核心" else (option.version + (option.unavailableReason?: "")),
                            onValueChange = {},
                            valueValidator = { available },
                            onStateChange = { if (!selected && available) onCoreSelected(option.id) },
                            enabled = uiState.canSelectCore,
                            readOnly = true,
                            onDelete = if (option.internal || selected) null else {{onCoreDelete(option.id)}},
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingBackgroundRunningPage(
    batteryOptimizationExempt: Boolean,
    onBatteryOptimizationRequest: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    navigateBack: () -> Unit,
) {
    var selectedTabIndex by remember { mutableStateOf(BackgroundRunningSystemType.ANDROID) }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = "后台运行",
                showTopAppBar = true,
                isWideScreen = false,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            var showBackgroundLockOverlay by rememberSaveable { mutableStateOf(false) }

            TabRow(
                tabs = listOf("Android", "小米"),
                selectedTabIndex = selectedTabIndex.ordinal,
                onTabSelected = { index ->
                    selectedTabIndex = BackgroundRunningSystemType.entries[index]
                },
                modifier = Modifier.padding(vertical = 20.dp)
            )

            when (selectedTabIndex) {
                BackgroundRunningSystemType.ANDROID -> {
                    MessageCard(
                        title = "忽略电池优化",
                        message = "为确保 Syncthing 在后台持续运行，建议启用忽略电池优化。" +
                                "若未忽略电池优化，则应用后台运行时可能会被Android系统终止。"
                    ) {
                        InfoSwitch(
                            title = "允许忽略电池优化",
                            checked = batteryOptimizationExempt,
                            onCheckedChange = { onBatteryOptimizationRequest() },
                            enabled = true,
                        )
                    }
                }

                BackgroundRunningSystemType.XIAOMI -> {
                    MessageCard(
                        title = "忽略电池优化",
                        message = "为确保 Syncthing 在后台持续运行，建议启用以下几项设置。"
                    ) {
                        InfoSwitch(
                            title = "允许忽略电池优化",
                            checked = batteryOptimizationExempt,
                            onCheckedChange = { onBatteryOptimizationRequest() },
                            enabled = true,
                        )
                        ArrowPreference(
                            title = "允许应用自启动",
                            onClick = onOpenAppDetailsSettings,
                        )
                        ArrowPreference(
                            title = "后台进程锁定",
                            onClick = { showBackgroundLockOverlay = true }, //TODO
                        )
                    }
                }
            }

            OverlayDialog(
                title = "后台进程锁定",
                show = showBackgroundLockOverlay,
                onDismissRequest = { showBackgroundLockOverlay = false },
                onDismissFinished = { showBackgroundLockOverlay = false },
            ) {
                Column ( verticalArrangement = Arrangement.spacedBy(20.dp) ) {
                    Text("从屏幕底部向上滑动，或点击屏幕底部的最近任务键，找到本应用并长按，点击🔒后出现锁定标识，即表示锁定成功。")
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = "确定",
                        onClick = { showBackgroundLockOverlay = false },
                        colors = TextButtonColors(
                            color = MiuixTheme.colorScheme.primary,
                            textColor = MiuixTheme.colorScheme.onPrimary,
                            disabledColor = MiuixTheme.colorScheme.primary,
                            disabledTextColor = MiuixTheme.colorScheme.disabledOnPrimary,
                        )
                    )
                }
            }
        }
    }
}

private enum class BackgroundRunningSystemType {
    ANDROID,
    XIAOMI,
}
