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
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.SyncthingRecentChange
import moe.https.syncthing.ui.component.CoreNotReadyTakePlace
import moe.https.syncthing.ui.component.StatusColor
import moe.https.syncthing.ui.component.ValueRow
import moe.https.syncthing.ui.model.RecentChangesUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RecentChangesScreen(
    uiState: RecentChangesUiState,
    coreState: CoreState,
    topAppBarScrollBehavior: ScrollBehavior,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefresh(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        refreshTexts = listOf("下拉刷新", "松手刷新"),
    ) {
        when {
            coreState != CoreState.RUNNING -> CoreNotReadyTakePlace(
                title = "核心未运行",
                message = "启动后才能读取最近文件变化。",
            )

            uiState.isLoading && uiState.changes.isEmpty() -> {}

            uiState.errorMessage != null -> CoreNotReadyTakePlace(
                title = "读取失败",
                message = uiState.errorMessage,
                isError = true,
            ) {
                TextButton(
                    text = "刷新",
                    onClick = onRefresh,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            uiState.hasLoaded && uiState.changes.isEmpty() -> {
                CoreNotReadyTakePlace(
                    title = "暂无文件变化",
                    message = "当前还没有记录到的文件变化",
                ) {
                    TextButton(
                        text = "刷新",
                        onClick = onRefresh,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.changes.forEach { change ->
                    key(change.id) {
                        RecentChangeCard(change)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentChangeCard(change: SyncthingRecentChange) {
    val isFolder = change.itemType == "dir" || change.itemType == "folder"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row ( horizontalArrangement = Arrangement.spacedBy(8.dp) ) {
                Icon(
                    contentDescription = "",
                    imageVector = if (isFolder) MiuixIcons.Folder else MiuixIcons.File,
                    tint = MiuixTheme.colorScheme.onBackground
                )
                Text(
                    text = if (change.action == "deleted") "- " else "~ " + change.path,
                    color = if (change.action == "deleted") {
                        StatusColor.FAIL.color
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                )
            }
            HorizontalDivider()
            ValueRow(
                label = "文件夹",
                value = change.folderLabel?.takeIf(String::isNotBlank) ?: change.folderId,
            )
            change.modifiedBy?.let { modifiedBy ->
                ValueRow(label = "设备", value = if (change.source == SyncthingRecentChange.Source.LOCAL ) "本机" else modifiedBy)
            }
            ValueRow(label = "时间", value = change.time.toClockTime()) // TODO: To use a better format for time
        }
    }
}

private fun String.toClockTime(): String =
    if (length >= 19 && getOrNull(10) == 'T') {
        substring(11, 19)
    } else {
        this
    }
