package moe.https.syncthing.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.ui.component.MessageCard
import moe.https.syncthing.ui.model.CoreUiState
import moe.https.syncthing.ui.resources.Syncthing
import moe.https.syncthing.ui.resources.displayBackgroundColor
import moe.https.syncthing.ui.resources.displayColor
import moe.https.syncthing.ui.util.displayName
import moe.https.syncthing.ui.util.formatBitsPerSecond
import moe.https.syncthing.ui.util.formatBytes
import moe.https.syncthing.ui.util.formatDuration
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CoreScreen(
    uiState: CoreUiState,
    snackbarHostState: SnackbarHostState,
    onStartAction: () -> Unit,
    modifier: Modifier = Modifier,
    uiPadding: PaddingValues,
    pagePaddingHorizontal: Dp,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    onChangeToAbout: () -> Unit,
    onChangeToLicence: () -> Unit,
) {
    @Composable
    fun InfoComponent(
        title: String,
        summary: String?,
    ) {
        BasicComponent(
            title = title,
            summary = summary ?: "-",
            insideMargin = PaddingValues(vertical = 10.dp, horizontal = 16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(uiPadding)
            .padding(horizontal = pagePaddingHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoreCard(
            uiState = uiState,
            developerModeEnabled = developerModeEnabled,
            onModifyDeveloperMode = onModifyDeveloperMode,
            snackbarHostState = snackbarHostState,
        )

        TextButton(
            text = if (uiState.state == CoreState.STOPPED) "启动" else "停止",
            onClick = onStartAction,
            enabled = uiState.canAction,
            modifier = Modifier.fillMaxWidth(),
        )

        Card {
            Column {
                InfoComponent(title = "设备名", summary = uiState.deviceName ?: "无名称")
                InfoComponent(title = "运行时长", summary = formatDuration(uiState.uptimeSeconds) ?: "未运行")
                InfoComponent(
                    title = "下载速率",
                    summary = uiState.downloadBytesPerSecond?.let {
                        uiState.downloadedBytes?.let {
                            "${formatBitsPerSecond(uiState.downloadBytesPerSecond)} (${formatBytes(uiState.downloadedBytes)})"
                        } ?: formatBitsPerSecond(uiState.downloadBytesPerSecond)
                    } ?: "已暂停",
                )
                InfoComponent(
                    title = "上传速率",
                    summary = uiState.uploadBytesPerSecond?.let {
                        uiState.uploadedBytes?.let {
                            "${formatBitsPerSecond(uiState.uploadBytesPerSecond)} (${formatBytes(uiState.uploadedBytes)})"
                        } ?: formatBitsPerSecond(uiState.uploadBytesPerSecond)
                    } ?: "已暂停",
                )
                InfoComponent(title = "总计文件大小", summary = formatBytes(uiState.totalFileSizeBytes))
                InfoComponent(title = "内存占用", summary = formatBytes(uiState.rssBytes))
            }
        }

        uiState.lastError?.let { message ->
            MessageCard(
                title = "错误",
                message = message,
                isError = true,
            )
        }

        Card {
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

@Composable
private fun CoreCard (
    uiState: CoreUiState,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var developerModeClickTimes by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardColors(
            color = uiState.state.displayBackgroundColor(),
            contentColor = MiuixTheme.colorScheme.onBackground,
        )
    ) {
        Row (
            modifier = Modifier.fillMaxWidth().clickable(
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
            ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.state.displayName(),
                    fontWeight = FontWeight.Medium,
                    style = MiuixTheme.textStyles.title3,
                )
                Text(
                    uiState.version ?: "不可用",
                    fontWeight = FontWeight.Medium,
                    style = MiuixTheme.textStyles.body1,
                    color = uiState.state.displayColor()
                )
            }
            Icon (
                imageVector = Syncthing,
                contentDescription = "",
                modifier = Modifier
                    .size(72.dp)
                    .offset(x = 16.dp, y = 16.dp)
                    .scale(1.6f),
                tint = uiState.state.displayColor(),
            )
        }
    }
}
