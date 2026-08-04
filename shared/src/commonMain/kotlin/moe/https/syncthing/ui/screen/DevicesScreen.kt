package moe.https.syncthing.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.NewDeviceConfiguration
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingLocalInfo
import moe.https.syncthing.ui.component.ImputableValueRow
import moe.https.syncthing.ui.component.MultipleValueRow
import moe.https.syncthing.ui.model.DevicesUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun DevicesScreen(
    uiState: DevicesUiState,
    coreState: CoreState,
    onRefresh: () -> Unit,
    onAddDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "设备连接",
                style = MiuixTheme.textStyles.title4,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ActionText(
                    text = "刷新",
                    enabled = coreState == CoreState.RUNNING && !uiState.isLoading,
                    onClick = onRefresh,
                )
                ActionText(
                    text = "添加设备",
                    enabled = coreState == CoreState.RUNNING && !uiState.isLoading,
                    onClick = onAddDevice,
                )
            }
        }

        if (coreState != CoreState.RUNNING) {
            MessageCard(
                title = "核心未运行",
                message = "启动 Syncthing 核心后，才能读取设备连接状态。",
            )
        } else if (uiState.isLoading && uiState.devices.isEmpty()) {
            MessageCard(
                title = "正在读取设备",
                message = "正在从 Syncthing 获取设备列表…",
            )
        } else if (uiState.errorMessage != null) {
            MessageCard(
                title = "读取失败",
                message = uiState.errorMessage,
                isError = true,
            )
        } else if (uiState.hasLoaded && uiState.devices.isEmpty()) {
            MessageCard(
                title = "暂无设备",
                message = "当前还没有配置其他 Syncthing 设备。",
            )
        } else {
            uiState.devices.forEach { device ->
                DeviceCard(device, uiState.localInfo)
            }
        }
    }

}

@Composable
private fun ActionText(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (enabled) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun DeviceCard(
    device: SyncthingDevice,
    localInfo: SyncthingLocalInfo?,
) {
    val statusColor = if (device.connected) {
        Color(0xFF2E7D32)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        color = if (!device.isLocal) statusColor else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = device.name ?: "未命名设备",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (!device.isLocal) {
                    Text(
                        text = if (device.connected) "已连接" else "未连接",
                        color = statusColor,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.3f),
                    )
                }
            }
            HorizontalDivider()
            MultipleValueRow("设备 ID", listOf(device.id))

            if (!device.isLocal){
                MultipleValueRow("当前地址", listOf(device.connectionAddress ?: "—"))
                MultipleValueRow("配置地址", listOf(device.addresses.joinToString("、").ifBlank { "—" }))
                MultipleValueRow("客户端", listOf(device.clientVersion ?: "—"))
                device.lastConnectionAt?.let { lastConnectionAt ->
                    MultipleValueRow("最后连接", listOf(lastConnectionAt))
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
            } else {
                MultipleValueRow("设备发现", listOf(localInfo.discoveryText()))
                MultipleValueRow(
                    label = "监听地址",
                    values = if (localInfo?.listenAddresses.isNullOrEmpty()) {
                        listOf("—")
                    } else {
                        localInfo.listenAddresses
                    },
                )
            }
        }
    }
}

private fun SyncthingLocalInfo?.discoveryText(): String {
    if (this == null) return "—"
    val failed = discoveryStatus.count { it.error != null }
    return when {
        !discoveryEnabled -> "已停用"
        failed == 0 -> "已启用"
        else -> "已启用（$failed 个服务异常）"
    }
}

@Composable
internal fun AddDeviceScreen(
    isSubmitting: Boolean,
    onCancel: () -> Unit,
    onConfirm: (NewDeviceConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deviceId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var addresses by remember { mutableStateOf("") }
    var introducer by remember { mutableStateOf(false) }
    var autoAcceptFolders by remember { mutableStateOf(false) }
    var compression by remember {
        mutableStateOf(NewDeviceConfiguration.Compression.METADATA)
    }
    var numConnections by remember { mutableStateOf("") }
    var maxSendKiBPerSecond by remember { mutableStateOf("") }
    var maxReceiveKiBPerSecond by remember { mutableStateOf("") }
    var untrusted by remember { mutableStateOf(false) }
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
            .padding(vertical = 20.dp, horizontal = 10.dp),
    ) {
        SmallTitle(text = "设备")
        Card (modifier = Modifier.padding(horizontal = 10.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                ImputableValueRow(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = "设备 ID",
                    valueLabel = "必填",
                    singleLine = false,
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
        }

        SmallTitle(text = "权限")
        Card (modifier = Modifier.padding(horizontal = 10.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                BasicComponent(
                    title = "作为中介",
                    summary = "将中介中的设备添加到我们的设备列表中，用于相互共享的文件夹。",
                    enabled = !isSubmitting,
                    role = Role.Switch,
                    onClick = { introducer = !introducer },
                    endActions = {
                        Switch(
                            checked = introducer,
                            onCheckedChange = null,
                            enabled = !isSubmitting,
                        )
                    },
                )
                BasicComponent(
                    title = "自动接受",
                    summary = "自动创建或共享此设备在默认路径上显示的文件夹。",
                    enabled = !isSubmitting,
                    role = Role.Switch,
                    onClick = { autoAcceptFolders = !autoAcceptFolders },
                    endActions = {
                        Switch(
                            checked = autoAcceptFolders,
                            onCheckedChange = null,
                            enabled = !isSubmitting,
                        )
                    },
                )
            }
        }

        SmallTitle(text = "连接")
        Card (modifier = Modifier.padding(horizontal = 10.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
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

                BasicComponent(
                    title = "不受信任",
                    summary = "禁止与此设备共享未加密数据；共享文件夹必须配置加密密码。",
                    enabled = !isSubmitting,
                    role = Role.Switch,
                    onClick = { untrusted = !untrusted },
                    endActions = {
                        Switch(
                            checked = untrusted,
                            onCheckedChange = null,
                            enabled = !isSubmitting,
                        )
                    },
                )

                if (!numericValuesValid) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "连接数和速率限制必须是非负整数。",
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.main,
                    )
                }
            }
        }

        Box (
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            TextButton(
                text = "添加",
                enabled = canSubmit,
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
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isError) {
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.defaultColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MiuixTheme.textStyles.title4)
            Text(text = message, color = MiuixTheme.colorScheme.disabledOnPrimaryButton)
        }
    }
}
