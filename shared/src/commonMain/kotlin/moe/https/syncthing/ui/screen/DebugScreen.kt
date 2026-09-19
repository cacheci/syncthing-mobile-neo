package moe.https.syncthing.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.https.syncthing.AppSubPage
import moe.https.syncthing.ui.component.BlurredSmallTopAppBar
import moe.https.syncthing.ui.component.InfoSwitchCard
import moe.https.syncthing.ui.component.barBackdropSource
import moe.https.syncthing.ui.model.AppPage
import moe.https.syncthing.ui.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.scripta.editor.CodeEditor
import top.yukonga.scripta.editor.EditorColors
import top.yukonga.scripta.editor.EditorLanguage
import top.yukonga.scripta.editor.EditorSymbol
import top.yukonga.scripta.editor.rememberSaveableCodeEditorController

@Composable
internal fun DevSettingPage(
    requestSwitchToPageMain: ( targetPage: AppPage ) -> Unit,
    requestSwitchToPagePlain: ( targetPage: AppSubPage ) -> Unit,
    navigateBack: () -> Unit,
    pagePaddingHorizontal: Dp,
    barBackdrop: LayerBackdrop?,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = AppTheme.colorScheme.surface,
        topBar = { BlurredSmallTopAppBar(
            title = "DEBUG*",
            scrollBehavior = scrollBehavior,
            backdrop = barBackdrop,
            navigationIcon = {
                IconButton( onClick = navigateBack ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                    )
                }
            },
        ) },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .barBackdropSource(barBackdrop)
                .nestedScroll(
                    scrollBehavior.nestedScrollConnection,
                )
                .padding(horizontal = pagePaddingHorizontal),
        ) {
            item {
                Spacer(
                    modifier = Modifier.padding(top = padding.calculateTopPadding())
                )
            }
            item {
                InfoSwitchCard(
                    title = "前往页面..."
                ) {
                    Column {
                        AppPage.entries.forEach { item ->
                            ArrowPreference(
                                title = item.title,
                                onClick = { requestSwitchToPageMain(item) },
                            )
                        }
                        AppSubPage.entries.forEach { item ->
                            ArrowPreference(
                                title = item.title,
                                onClick = { requestSwitchToPagePlain(item) },
                            )
                        }
                    }
                }
            }

            item {
                val ignoreEditorController = rememberSaveableCodeEditorController(
                    initialText = "test",
                )
                CodeEditor(
                    controller = ignoreEditorController,
                    language = EditorLanguage.PlainText,
                    colors = if (isSystemInDarkTheme()) EditorColors.Default else EditorColors.Light,
                    symbols = listOf(
                        EditorSymbol(label = "*"),
                        EditorSymbol(label = "**"),
                        EditorSymbol(label = "!"),
                        EditorSymbol(label = "//"),
                        EditorSymbol(label = "(?d)"),
                        EditorSymbol(label = "(?i)"),
                        EditorSymbol(label = "#include", value = "#include "),
                    ),
                    windowInsetsEnabled = false,
                    readOnly = false,
                    softWrap = true,
                    overscrollEnabled = false,
                    autoClosePairs = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 20.dp)
                        .height(320.dp)
                )
            }

            item {
                Spacer(
                    modifier = Modifier.padding(top = padding.calculateBottomPadding())
                )
            }
        }
    }
}
