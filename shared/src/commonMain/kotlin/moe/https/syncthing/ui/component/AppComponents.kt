package moe.https.syncthing.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
private fun ValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(0.5f)
                .clickable(
                    enabled = onClick != null,
                    onClick = { onClick?.invoke() },
                ),
        )
    }
}

@Composable
internal fun MultipleValueRow(
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

@Composable
internal fun ImputableValueRow(
    modifier: Modifier = Modifier,
    label: String,
    valueLabel: String,
    value: String,
    onValueChange: ((String) -> Unit),
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
) {
    Row (
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.5f),
            style = MiuixTheme.textStyles.main.copy(
                fontWeight = FontWeight.Medium,
            )
        )
        Box (
            modifier = Modifier.weight(0.4f),
        ) {
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                textStyle = MiuixTheme.textStyles.main.copy(
                    textAlign = TextAlign.End,
                ),
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
            )
            if (!value.isNotEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = valueLabel,
                    textAlign = TextAlign.End,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
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
