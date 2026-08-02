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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingLocalInfo
import moe.https.syncthing.ui.model.DevicesUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DevicesScreen(
    uiState: DevicesUiState,
    coreState: CoreState,
    onRefresh: () -> Unit,
    onAddDevice: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

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
                    onClick = { showAddDialog = true },
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

    if (showAddDialog) {
        AddDeviceDialog(
            isSubmitting = uiState.isLoading,
            onDismiss = { showAddDialog = false },
            onConfirm = { deviceId, name, addresses ->
                onAddDevice(deviceId, name, addresses)
                showAddDialog = false
            },
        )
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
            DeviceValueRow("设备 ID", listOf(device.id))

            if (!device.isLocal){
                DeviceValueRow("当前地址", listOf(device.connectionAddress ?: "—"))
                DeviceValueRow("配置地址", listOf(device.addresses.joinToString("、").ifBlank { "—" }))
                DeviceValueRow("客户端", listOf(device.clientVersion ?: "—"))
                device.lastConnectionAt?.let { lastConnectionAt ->
                    DeviceValueRow("最后连接", listOf(lastConnectionAt))
                }
                if (device.paused) {
                    Text(
                        text = "此设备已暂停",
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
                if (device.discoveredAddresses.isNotEmpty()) {
                    DeviceValueRow(
                        "发现地址",
                        device.discoveredAddresses,
                    )
                }
            } else {
                DeviceValueRow("设备发现", listOf(localInfo.discoveryText()))
                DeviceValueRow(
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

@Composable
private fun DeviceValueRow(
    label: String,
    values: List<String>,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.35f),
        )
        Column(
            modifier = Modifier
                .weight(0.65f)
                .clickable(
                    enabled = onClick != null,
                    onClick = { onClick?.invoke() },
                ),
        ) {
            if (values.count() > 1) {
                values.forEach { values ->
                    Row {
                        Text(
                            text = "·",
                            modifier = Modifier.weight(0.05f),
                        )
                        Text(
                            text = values.toCharArray().joinToString("\u200B"),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.95f),
                        )
                    }
                }
            } else {
                Row {
                    Box(modifier = Modifier.weight(0.02f))
                    Text(
                        text = values[0],
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.98f),
                    )
                }
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
private fun AddDeviceDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var deviceId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var addresses by remember { mutableStateOf("") }
    val canSubmit = deviceId.trim().isNotBlank() && !isSubmitting

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "添加设备",
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = "填写远程 Syncthing 设备信息。设备 ID 必须与对方设备显示的 ID 完全一致。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                FormField(
                    label = "设备 ID *",
                    value = deviceId,
                    onValueChange = { deviceId = it },
                )
                FormField(
                    label = "设备名称",
                    value = name,
                    onValueChange = { name = it },
                )
                FormField(
                    label = "地址（可选，多个地址用逗号分隔）",
                    value = addresses,
                    onValueChange = { addresses = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionText(
                        text = "取消",
                        enabled = !isSubmitting,
                        onClick = onDismiss,
                    )
                    ActionText(
                        text = if (isSubmitting) "添加中…" else "添加",
                        enabled = canSubmit,
                        onClick = { onConfirm(deviceId, name, addresses) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
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
