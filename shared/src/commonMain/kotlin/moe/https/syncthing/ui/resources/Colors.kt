package moe.https.syncthing.ui.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import moe.https.syncthing.core.CoreState
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun CoreState.displayBackgroundColor(): Color = lerp(
    start = this.displayColor(),
    stop = MiuixTheme.colorScheme.background,
    fraction = 0.8f,
)

@Composable
internal fun CoreState.displayColor(): Color = when (this) {
    CoreState.RUNNING -> StatusColor.OK.color
    CoreState.FAILED -> StatusColor.FAIL.color
    CoreState.STARTING,
    CoreState.STOPPING,
    CoreState.INSTALLING -> StatusColor.PENDING.color
    else -> StatusColor.DOWN.color
}

internal enum class StatusColor ( val color: Color ){
    OK(Color(0xFF2E7D32)),
    FAIL(Color(0xFFFF3728)),
    PENDING(Color(0xFFB26A00)),
    PAUSED(Color(0xFF7a48a3)),
    DOWN(Color(0xFF666666)),
}