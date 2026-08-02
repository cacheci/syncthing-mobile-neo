package moe.https.syncthing.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.ui.model.CoreUiState
import moe.https.syncthing.ui.util.displayName
import moe.https.syncthing.ui.util.formatBytes
import moe.https.syncthing.ui.util.formatDuration
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun StatusCard(uiState: CoreUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("●", color = uiState.state.displayColor())
                    Text("核心状态", style = MiuixTheme.textStyles.headline1)
                }
                Text(
                    text = uiState.state.displayName(),
                    color = uiState.state.displayColor(),
                    fontWeight = FontWeight.Medium,
                )
            }
            HorizontalDivider()
            ValueRow("核心版本", uiState.version ?: "未导入")
            ValueRow("支持架构", "arm64-v8a")
        }
    }
}

@Composable
internal fun RuntimeCard(uiState: CoreUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("运行状态", style = MiuixTheme.textStyles.headline1)
            HorizontalDivider()
            ValueRow("运行时长", formatDuration(uiState.uptimeSeconds))
            ValueRow("实际内存 RSS", formatBytes(uiState.rssBytes))
            ValueRow("Go 已分配内存", formatBytes(uiState.allocatedBytes))
            ValueRow("Go 系统内存", formatBytes(uiState.systemBytes))
            ValueRow("Goroutine", uiState.goroutines?.toString() ?: "—")
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(0.4f)) {
            Text(
                text = label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.weight(0.1f))
        Box(
            modifier = Modifier.weight(0.5f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun CoreState.displayColor(): Color = when (this) {
    CoreState.RUNNING -> Color(0xFF2E7D32)
    CoreState.FAILED -> MiuixTheme.colorScheme.error
    CoreState.STARTING,
    CoreState.STOPPING,
    CoreState.INSTALLING -> Color(0xFFB26A00)
    else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}
