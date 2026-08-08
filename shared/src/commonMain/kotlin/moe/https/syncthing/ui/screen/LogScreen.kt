package moe.https.syncthing.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.https.syncthing.core.CoreLogSource
import moe.https.syncthing.ui.model.LogUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun LogScreen(
    uiState: LogUiState,
    onSourceSelected: (CoreLogSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(uiState.content) {
        delay(50.milliseconds)
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val logSources = listOf(
            CoreLogSource.SYNCTHING,
            CoreLogSource.LAUNCHER,
            CoreLogSource.CONTROLLER,
        )
        TabRow(
            tabs = listOf("Syncthing", "启动器", "控制器"),
            selectedTabIndex = logSources.indexOf(uiState.source).coerceAtLeast(0),
            onTabSelected = { index -> onSourceSelected(logSources[index]) },
        )

        Text(
            text = uiState.refreshedAt?.let { "最后刷新：$it" } ?: "尚未刷新",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.error != null -> Text(
                        text = uiState.error,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                    )

                    uiState.content.isBlank() -> Text(
                        text = "暂无日志",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> SelectionContainer {
                        Text(
                            text = uiState.content,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp),
                            style = MiuixTheme.textStyles.body2,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
