package moe.https.syncthing.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.ui.model.CoreUiState
import moe.https.syncthing.ui.util.displayName
import moe.https.syncthing.ui.util.formatBytes
import moe.https.syncthing.ui.util.formatDuration
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun StatusCard(
    uiState: CoreUiState,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var developerModeClickTimes by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

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
            MultipleValueRow(
                label = "核心版本",
                values = listOf(uiState.version ?: "未导入"),
                onClick = {
                    if (!developerModeEnabled) {
                        developerModeClickTimes += 1
                        if (developerModeClickTimes >= 10) {
                            onModifyDeveloperMode()
                            developerModeClickTimes = 0
                            scope.launch {
                                snackbarHostState.showSnackbar("已开启开发者模式")
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("您已处于开发者模式")
                        }
                    }
                }
            )
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
    textAlign: TextAlign = TextAlign.End,
    color: Color = MiuixTheme.colorScheme.onBackground
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
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
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
                        textAlign = textAlign,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(0.98f),
                        color = color,
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
    allowEdit: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Row (
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
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
                    color = if (allowEdit) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSecondaryContainer
                ),
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                enabled = allowEdit,
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
internal fun InfoSwitchCard(
    title: String,
    content: @Composable () -> Unit,
) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 10.dp),
    )
    Card {
        Column(content = { content() })
    }
}

@Composable
internal fun MessageCard(
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Card(
        modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth(),
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
            Text(text = message, color = MiuixTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun InfoSwitch(
    title: String,
    summary: String ?= null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
internal fun CoreNotReadyTakePlace(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = if (isError) { MiuixTheme.colorScheme.error } else { MiuixTheme.colorScheme.onSurfaceVariantSummary },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
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
