package moe.https.syncthing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode as MiuixColorSchemeMode

typealias AppThemeController = ThemeController
typealias ColorSchemeMode = MiuixColorSchemeMode

@Immutable
data class StatusColors(
    val ok: Color,
    val okContainer: Color,
    val fail: Color,
    val failContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val disconnected: Color,
    val disconnectedContainer: Color,
    val down: Color,
    val downContainer: Color,
)

private val LightStatusColors = StatusColors(
    ok = Color(0xFF2E7D32),
    okContainer = Color(0xFFD5E5D5),
    fail = Color(0xFFE94634),
    failContainer = Color(0xFFFFDDD6),
    pending = Color(0xFFB26A00),
    pendingContainer = Color(0xFFF1E1D2),
    disconnected = Color(0xFF7A48A3),
    disconnectedContainer = Color(0xFFE3D9EE),
    down = Color(0xFF666666),
    downContainer = Color(0xFFDEDEDE),
)

private val DarkStatusColors = StatusColors(
    ok = Color(0xFF2E7D32),
    okContainer = Color(0xFF293528),
    fail = Color(0xFFFF3728),
    failContainer = Color(0xFFD2E29),
    pending = Color(0xFFB26A00),
    pendingContainer = Color(0xFF3E3226),
    disconnected = Color(0xFF7A48A3),
    disconnectedContainer = Color(0xFF342C3C),
    down = Color(0xFF666666),
    downContainer = Color(0xFF303030),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

@Composable
fun AppTheme(
    controller: AppThemeController,
    textStyles: TextStyles = MiuixTheme.textStyles,
    content: @Composable () -> Unit,
) {
    val isDark = when (controller.colorSchemeMode) {
        ColorSchemeMode.Dark,
        ColorSchemeMode.MonetDark,
        -> true

        ColorSchemeMode.Light,
        ColorSchemeMode.MonetLight,
        -> false

        ColorSchemeMode.System,
        ColorSchemeMode.MonetSystem,
        -> controller.isDark ?: isSystemInDarkTheme()
    }
    MiuixTheme(
        controller = controller,
        textStyles = textStyles,
    ) {
        CompositionLocalProvider(
            LocalStatusColors provides if (isDark) DarkStatusColors else LightStatusColors,
            content = content,
        )
    }
}

object AppTheme {
    val colorScheme: Colors
        @Composable @ReadOnlyComposable
        get() = MiuixTheme.colorScheme

    val textStyles: TextStyles
        @Composable @ReadOnlyComposable
        get() = MiuixTheme.textStyles

    val statusColors: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current
}
