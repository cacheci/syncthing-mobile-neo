package moe.https.syncthing.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.ui.model.DevicesUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DevicesScreen(
    uiState: DevicesUiState,
    coreState: CoreState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        } else {
            uiState.devices.forEach { device ->
                DeviceCard(device)
            }
        }
    }
}

@Composable
private fun DeviceCard(device: SyncthingDevice) {
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!device.isLocal) {
                        Text(
                            text = if (device.connected) "●" else "○",
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
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
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            HorizontalDivider()
            DeviceValueRow("设备 ID", device.id)

            if (!device.isLocal){
                DeviceValueRow("当前地址", device.connectionAddress ?: "—")
                DeviceValueRow("配置地址", device.addresses.joinToString("、").ifBlank { "—" })
                DeviceValueRow("客户端", device.clientVersion ?: "—")
                device.lastConnectionAt?.let { lastConnectionAt ->
                    DeviceValueRow("最后连接", lastConnectionAt)
                }
                if (device.paused) {
                    Text(
                        text = "此设备已暂停",
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceValueRow(label: String, value: String) {
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
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.65f),
        )
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
