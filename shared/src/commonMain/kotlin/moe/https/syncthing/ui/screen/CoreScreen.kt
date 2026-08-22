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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.https.syncthing.ui.component.MessageCard
import moe.https.syncthing.ui.component.ValueRow
import moe.https.syncthing.ui.component.displayColor
import moe.https.syncthing.ui.model.CoreUiState
import moe.https.syncthing.ui.util.displayName
import moe.https.syncthing.ui.util.formatBytes
import moe.https.syncthing.ui.util.formatDuration
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CoreScreen(
    uiState: CoreUiState,
    snackbarHostState: SnackbarHostState,
    onStartAction: () -> Unit,
    modifier: Modifier = Modifier,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
) {
    var developerModeClickTimes by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                ValueRow(
                    label = "核心版本",
                    value = uiState.version ?: "不可用",
                    onClick = {
                        if (!developerModeEnabled) {
                            developerModeClickTimes += 1
                            if (developerModeClickTimes >= 10) {
                                onModifyDeveloperMode()
                                developerModeClickTimes = 0
                                scope.launch { snackbarHostState.showSnackbar("已开启开发者模式") }
                            }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("您已处于开发者模式") }
                        }
                    }
                )
                ValueRow(label = "系统架构", value = "arm64-v8a")
            }
        }

        TextButton(
            text = uiState.actionBtnText,
            onClick = onStartAction,
            enabled = uiState.canAction,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("运行状态", style = MiuixTheme.textStyles.headline1)
                HorizontalDivider()
                ValueRow(label = "运行时长", value = formatDuration(uiState.uptimeSeconds))
                ValueRow(label = "实际内存 RSS", value = formatBytes(uiState.rssBytes))
                ValueRow(label = "Go 已分配内存", value = formatBytes(uiState.allocatedBytes))
                ValueRow(label = "Go 系统内存", value = formatBytes(uiState.systemBytes))
                ValueRow(label = "Goroutine", value = uiState.goroutines?.toString() ?: "—")
            }
        }

        uiState.lastError?.let { message ->
            MessageCard(
                title = "错误",
                message = message,
                isError = true,
            )
        }
    }
}
