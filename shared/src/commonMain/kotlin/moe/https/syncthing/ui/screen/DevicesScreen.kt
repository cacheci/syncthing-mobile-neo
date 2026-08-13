package moe.https.syncthing.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.NewDeviceConfiguration
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingDiscoveryStatus
import moe.https.syncthing.core.SyncthingLocalInfo
import moe.https.syncthing.core.SyncthingListenAddress
import moe.https.syncthing.ui.component.CoreNotReadyTakePlace
import moe.https.syncthing.ui.component.DeviceShareOverlayDialog
import moe.https.syncthing.ui.component.ImputableValueRow
import moe.https.syncthing.ui.component.InfoSwitch
import moe.https.syncthing.ui.component.InfoSwitchCard
import moe.https.syncthing.ui.component.MultipleValueRow
import moe.https.syncthing.ui.model.DevicesUiState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun DevicesScreen(
    uiState: DevicesUiState,
    coreState: CoreState,
    topAppBarScrollBehavior: ScrollBehavior,
    onRefresh: () -> Unit,
    onEditDevice: (SyncthingDevice) -> Unit,
    modifier: Modifier = Modifier,
) {

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefresh (
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        refreshTexts = listOf("下拉刷新", "松手刷新"),
    ) {
        if (coreState != CoreState.RUNNING) {
            CoreNotReadyTakePlace(
                title = "核心未运行",
                message = "启动后才能读取设备连接状态。",
            )
        } else if (uiState.isLoading && uiState.devices.isEmpty()) {
            CoreNotReadyTakePlace(
                title = "正在读取设备",
                message = "正在获取设备列表…",
            )
        } else if (uiState.errorMessage != null) {
            CoreNotReadyTakePlace(
                title = "读取失败",
                message = uiState.errorMessage,
                isError = true,
            )
        } else if (uiState.hasLoaded && uiState.devices.isEmpty()) {
            CoreNotReadyTakePlace(
                title = "暂无设备",
                message = "当前还没有配置的设备。",
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.devices.forEach { device ->
                    if (device.isLocal) LocalDeviceCard(device, uiState.localInfo) else RemoteDeviceCard(device, onEditDevice)
                }
            }
        }
    }

}

@Composable
private fun RemoteDeviceCard(
    device: SyncthingDevice,
    onEditDevice: (SyncthingDevice) -> Unit,
) {
    var holdDown by rememberSaveable { mutableStateOf(false) }
    var showShareOverlay by rememberSaveable { mutableStateOf(false) }
    var foldContentStatus by rememberSaveable { mutableStateOf(false) }

    val statusColor = if (device.connected) {
        Color(0xFF2E7D32)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        holdDownState = holdDown,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(
                        onLongClick = { onEditDevice(device) },
                        onClick = { foldContentStatus = !foldContentStatus },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text(
                        text = "●",
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = device.name ?: "未命名设备",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Text(
                    text = if (device.connected) "已连接" else "未连接",
                    color = statusColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.3f),
                )
            }
            AnimatedVisibility(
                visible = foldContentStatus,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)  // 展开动画时长
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)  // 折叠动画时长
                )
            ) {
                Column ( verticalArrangement = Arrangement.spacedBy(10.dp) ) {
                    HorizontalDivider()

                    MultipleValueRow(
                        label = "设备 ID",
                        values = listOf(device.id),
                        textAlign = TextAlign.Start,
                        onClick = {showShareOverlay = true},
                    )
                    MultipleValueRow(
                        label = "当前地址",
                        values = listOf(device.connectionAddress ?: "—"),
                    )
                    MultipleValueRow(
                        label = "配置地址",
                        values = listOf(device.addresses.joinToString("、").ifBlank { "—" }),
                    )
                    MultipleValueRow(
                        label = "客户端",
                        values = listOf(device.clientVersion ?: "—"),
                    )

                    device.lastConnectionAt?.let { lastConnectionAt ->
                        MultipleValueRow(
                            label = "最后连接",
                            values = listOf(lastConnectionAt.toString()),
                        )
                    }

                    if (device.paused) {
                        Text(
                            text = "此设备已暂停",
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                    if (device.discoveredAddresses.isNotEmpty()) {
                        MultipleValueRow(
                            "发现地址",
                            device.discoveredAddresses,
                        )
                    }
                }
            }
        }
    }


    DeviceShareOverlayDialog(
        show = showShareOverlay,
        onDismissRequest = { showShareOverlay = false },
        onDismissFinished = { holdDown = false },
        deviceID = device.id,
    )
}

@Composable
private fun LocalDeviceCard(
    device: SyncthingDevice,
    localInfo: SyncthingLocalInfo?,
) {
    var holdDown by rememberSaveable { mutableStateOf(false) }
    var showShareOverlay by rememberSaveable { mutableStateOf(false) }
    var showDiscoveryOverlay by rememberSaveable { mutableStateOf(false) }
    var showListenOverlay by rememberSaveable { mutableStateOf(false) }
    var foldContentStatus by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        holdDownState = holdDown,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            val (discoveryText, discoveryColor) = countToColouredString(localInfo?.discoveryStatus)
            val (listenText, listenColor) = countToColouredString(localInfo?.listenAddresses)

            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(
                        onLongClick = { },
                        onClick = { foldContentStatus = !foldContentStatus },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.7f),
                ) {
                    Text(
                        text = "●",
                        color = Color(0xFF2E7D32),
                    )
                    Text(
                        text = "本机",
                        style = MiuixTheme.textStyles.headline1,
                    )
                }
            }

            AnimatedVisibility(
                visible = foldContentStatus,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                )
            ) {
                Column ( verticalArrangement = Arrangement.spacedBy(10.dp) ) {
                    HorizontalDivider()

                    MultipleValueRow(
                        label = "设备 ID",
                        values = listOf(device.id),
                        textAlign = TextAlign.Start,
                        onClick = { showShareOverlay = true },
                    )

                    MultipleValueRow(
                        label = "设备发现",
                        values = listOf(discoveryText),
                        color = discoveryColor,
                        onClick = { showDiscoveryOverlay = true },
                    )
                    MultipleValueRow(
                        label = "监听地址",
                        values = listOf(listenText),
                        color = listenColor,
                        onClick = { showListenOverlay = true },
                    )
                }
            }
        }
    }

    OverlayDialog(
        show = showDiscoveryOverlay,
        title = "设备发现",
        onDismissRequest = { showDiscoveryOverlay = false },
        onDismissFinished = { holdDown = false },
        content = {
            Column ( horizontalAlignment = Alignment.CenterHorizontally ) {
                LazyColumn (
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    items(
                        localInfo?.discoveryStatus ?: listOf(
                            SyncthingDiscoveryStatus(
                                method = "无启用的设备发现",
                                error = "将仅连接到手动设置地址的设备。",
                            )
                        )
                    ) { item ->
                        if (item.error != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("●", color = MiuixTheme.colorScheme.error)
                                Column {
                                    Text(item.method)
                                    Text(text = item.error.toCharArray().joinToString("\u200B"), color = MiuixTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("●", color = Color(0xFF2E7D32))
                                Text(item.method)
                            }
                        }
                    }
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    text = "确定",
                    onClick = { showDiscoveryOverlay = false },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )

    OverlayDialog(
        show = showListenOverlay,
        title = "监听地址",
        onDismissRequest = { showListenOverlay = false },
        onDismissFinished = { holdDown = false },
        content = {
            Column (horizontalAlignment = Alignment.CenterHorizontally) {
                LazyColumn (
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    items(
                        localInfo?.listenAddresses ?: listOf (
                            SyncthingListenAddress(
                                address = "无启用的监听地址",
                                error = "将仅能主动连接到其他设备。"
                            )
                        )
                    ) { item ->
                        if (item.error != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("●", color = MiuixTheme.colorScheme.error)
                                Column {
                                    Text(item.address)
                                    Text(text = item.error.toCharArray().joinToString("\u200B"), color = MiuixTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("●", color = Color(0xFF2E7D32))
                                Text(item.address)
                            }
                        }
                    }
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    text = "确定",
                    onClick = { showListenOverlay = false },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )

    DeviceShareOverlayDialog(
        show = showShareOverlay,
        onDismissRequest = { showShareOverlay = false },
        onDismissFinished = { holdDown = false },
        deviceID = device.id,
    )
}

@Composable
@JvmName("countToColouredStringForDiscovery")
private fun countToColouredString( status: List<SyncthingDiscoveryStatus>? ): Pair<String, Color> {
    if (status == null) return "—" to MiuixTheme.colorScheme.onBackground

    val succeeded = status.count { it.error == null }
    val total = status.count()

    return "$succeeded/$total 在线" to when (succeeded) {
        total -> Color(0xFF2E7D32)
        0 -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.primary
    }
}

@Composable
@JvmName("countToColouredStringForListen")
private fun countToColouredString( status: List<SyncthingListenAddress>? ): Pair<String, Color> {
    if (status == null) return "—" to MiuixTheme.colorScheme.onBackground

    val succeeded = status.count { it.error == null }
    val total = status.size

    return "$succeeded/$total 在线" to when (succeeded) {
        total -> Color(0xFF2E7D32)
        0 -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.primary
    }
}

@Composable
internal fun AddDeviceScreen(
    modifier: Modifier = Modifier,
    isSubmitting: Boolean,
    existingDevice: SyncthingDevice? = null,
    onConfirm: (NewDeviceConfiguration) -> Unit,
) {
    var deviceId by remember(existingDevice) { mutableStateOf(existingDevice?.id.orEmpty()) }
    var name by remember(existingDevice) { mutableStateOf(existingDevice?.name.orEmpty()) }
    var group by remember(existingDevice) { mutableStateOf(existingDevice?.group.orEmpty()) }
    var addresses by remember(existingDevice) { mutableStateOf(existingDevice?.addresses?.joinToString(",").orEmpty()) }
    var introducer by remember(existingDevice) { mutableStateOf(existingDevice?.introducer ?: false) }
    var autoAcceptFolders by remember(existingDevice) { mutableStateOf(existingDevice?.autoAcceptFolders ?: false) }
    var compression by remember {
        mutableStateOf(existingDevice?.compression ?: NewDeviceConfiguration.Compression.METADATA)
    }
    var numConnections by remember(existingDevice) { mutableStateOf(existingDevice?.numConnections?.toString().orEmpty()) }
    var maxSendKiBPerSecond by remember(existingDevice) { mutableStateOf(existingDevice?.maxSendKiBPerSecond?.toString().orEmpty()) }
    var maxReceiveKiBPerSecond by remember(existingDevice) { mutableStateOf(existingDevice?.maxReceiveKiBPerSecond?.toString().orEmpty()) }
    var untrusted by remember(existingDevice) { mutableStateOf(existingDevice?.untrusted ?: false) }
    val numericValuesValid = listOf(
        numConnections,
        maxSendKiBPerSecond,
        maxReceiveKiBPerSecond,
    ).all { value -> (value.toIntOrNull()?:0) >= 0 }
    val canSubmit = deviceId.trim().isNotBlank() && numericValuesValid && !isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp, horizontal = 20.dp),
    ) {
        InfoSwitchCard(
            title = "设备",
            content = {
                ImputableValueRow(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = "设备 ID",
                    valueLabel = "必填",
                    singleLine = false,
                    allowEdit = existingDevice == null
                )

                ImputableValueRow(
                    value = name,
                    onValueChange = { name = it },
                    label = "设备名",
                    valueLabel = "选填",
                    singleLine = true,
                )

                ImputableValueRow(
                    value = group,
                    onValueChange = { group = it },
                    label = "设备组",
                    valueLabel = "选填",
                    singleLine = true,
                )
            }
        )

        InfoSwitchCard(
            title = "权限",
            content = {
                InfoSwitch(
                    title = "作为中介",
                    summary = "将中介中的设备添加到我们的设备列表中，用于相互共享的文件夹。",
                    enabled = !isSubmitting,
                    onCheckedChange = { introducer = !introducer },
                    checked = introducer,
                )
                InfoSwitch(
                    title = "自动接受",
                    summary = "自动创建或共享此设备在默认路径上显示的文件夹。",
                    enabled = !isSubmitting,
                    onCheckedChange = { autoAcceptFolders = !autoAcceptFolders },
                    checked = autoAcceptFolders,
                )
                InfoSwitch(
                    title = "不受信任",
                    summary = "禁止与此设备共享未加密数据；共享文件夹必须配置加密密码。",
                    enabled = !isSubmitting,
                    onCheckedChange = { untrusted = !untrusted },
                    checked = untrusted,
                )
            }
        )

        InfoSwitchCard(
            title = "连接",
            content = {
                ImputableValueRow(
                    value = addresses,
                    onValueChange = { addresses = it },
                    label = "地址",
                    valueLabel = "dynamic",
                    singleLine = false,
                )

                ImputableValueRow(
                    value = maxSendKiBPerSecond,
                    onValueChange = { maxSendKiBPerSecond = it },
                    label = "上传限速（KiB/s）",
                    valueLabel = "无限制",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ImputableValueRow(
                    value = maxReceiveKiBPerSecond,
                    onValueChange = { maxReceiveKiBPerSecond = it },
                    label = "下载限速（KiB/s）",
                    valueLabel = "无限制",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                ImputableValueRow(
                    value = numConnections,
                    onValueChange = { numConnections = it },
                    label = "连接数",
                    valueLabel = "auto",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                WindowDropdownPreference(
                    title = "压缩",
                    summary = "选择与此设备通信时使用的压缩方式。",
                    items = listOf("所有", "仅元数据", "关闭"),
                    selectedIndex = compression.ordinal,
                    enabled = !isSubmitting,
                    onSelectedIndexChange = { selectedIndex ->
                        compression = NewDeviceConfiguration.Compression.entries[selectedIndex]
                    },
                )
            }
        )

        if (!numericValuesValid) {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                text = "连接数和速率限制必须是非负整数。",
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.main,
            )
        }

        TextButton(
            text = if (existingDevice == null) "添加" else "保存",
            enabled = canSubmit,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 16.dp)
                .fillMaxWidth(),
            onClick = {
                onConfirm(
                    NewDeviceConfiguration(
                        deviceId = deviceId,
                        name = name,
                        group = group,
                        addresses = addresses
                            .split(',', '\n')
                            .map(String::trim)
                            .filter(String::isNotBlank),
                        introducer = introducer,
                        autoAcceptFolders = autoAcceptFolders,
                        compression = compression,
                        numConnections = numConnections.toIntOrNull() ?: 0,
                        maxSendKiBPerSecond = maxSendKiBPerSecond.toIntOrNull() ?: 0,
                        maxReceiveKiBPerSecond = maxReceiveKiBPerSecond.toIntOrNull() ?: 0,
                        untrusted = untrusted,
                    ),
                )
            },
        )
    }
}
