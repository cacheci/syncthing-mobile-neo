package moe.https.syncthing.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import moe.https.syncthing.AppPage
import moe.https.syncthing.AppSubPage
import moe.https.syncthing.ui.component.AdaptiveTopAppBar
import moe.https.syncthing.ui.component.InfoSwitchCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun DevSettingPage(
    requestSwitchToPageMain: ( targetPage: AppPage ) -> Unit,
    requestSwitchToPagePlain: ( targetPage: AppSubPage ) -> Unit,
    navigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = { AdaptiveTopAppBar(
            title = "DEBUG*",
            showTopAppBar = true,
            isWideScreen = false,
            scrollBehavior = scrollBehavior,
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
        Box (
            modifier = Modifier
                .padding(padding)
                .nestedScroll(
                    scrollBehavior.nestedScrollConnection,
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                InfoSwitchCard(
                    title = "前往页面..."
                ) {
                    LazyColumn {
                        items(AppPage.entries) { item ->
                            ArrowPreference(
                                title = item.title,
                                onClick = { requestSwitchToPageMain(item) },
                            )
                        }
                        items(AppSubPage.entries) { item ->
                            ArrowPreference(
                                title = item.title,
                                onClick = { requestSwitchToPagePlain(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}