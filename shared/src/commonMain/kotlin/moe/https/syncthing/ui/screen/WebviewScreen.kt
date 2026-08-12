package moe.https.syncthing.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.ui.component.CoreNotReadyTakePlace
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun WebviewScreen(
    coreState: CoreState,
    topAppBarScrollBehavior: ScrollBehavior,
    webUiUrl: String?,
    reloadToken: Int,
    webView: @Composable (
        url: String,
        reloadToken: Int,
        onScroll: (deltaY: Float, isAtTop: Boolean) -> Unit,
        modifier: Modifier,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (coreState == CoreState.RUNNING && webUiUrl != null) {
        LaunchedEffect(webUiUrl, topAppBarScrollBehavior) {
            topAppBarScrollBehavior.state.heightOffset = 0f
            topAppBarScrollBehavior.state.contentOffset = 0f
        }
        val onScroll = remember(topAppBarScrollBehavior) {
            { deltaY: Float, isAtTop: Boolean ->
                val state = topAppBarScrollBehavior.state
                if (isAtTop) {
                    state.heightOffset = 0f
                    state.contentOffset = 0f
                } else {
                    state.heightOffset -= deltaY
                    state.contentOffset -= deltaY
                }
            }
        }
        webView(webUiUrl, reloadToken, onScroll, modifier.fillMaxSize())
        return
    }

    val (title, message) = when (coreState) {
        CoreState.NOT_INSTALLED -> "核心未安装" to "请先在 Syncthing 页面导入核心。"
        CoreState.STOPPED -> "核心未运行" to "启动 Syncthing 以使用 WebUI。"
        CoreState.INSTALLING -> "正在安装核心" to "核心安装完成后再启动 WebUI。"
        CoreState.STARTING -> "核心正在启动" to "WebUI 将在核心就绪后自动显示。"
        CoreState.STOPPING -> "核心正在停止" to "WebUI 当前不可用。"
        CoreState.FAILED -> "核心运行失败" to "请返回 Syncthing 页面查看错误信息。"
        CoreState.RUNNING -> "WebUI 地址不可用" to "请检查 GUI 监听地址和端口设置。"
    }

    CoreNotReadyTakePlace(title = title, message = message, modifier = modifier)
}
