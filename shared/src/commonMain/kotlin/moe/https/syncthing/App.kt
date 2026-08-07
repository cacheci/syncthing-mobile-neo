package moe.https.syncthing

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.ui.component.AdaptiveTopAppBar
import moe.https.syncthing.ui.screen.AddDeviceScreen
import moe.https.syncthing.ui.screen.CoreScreen
import moe.https.syncthing.ui.screen.DevicesScreen
import moe.https.syncthing.ui.screen.EmptyScreen
import moe.https.syncthing.ui.screen.LogScreen
import moe.https.syncthing.viewmodel.LogViewModel
import moe.https.syncthing.viewmodel.CoreViewModel
import moe.https.syncthing.viewmodel.DevicesViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun App(
    coreViewModel: CoreViewModel,
    logViewModel: LogViewModel,
    devicesViewModel: DevicesViewModel
) {
    val coreUiState by coreViewModel.uiState.collectAsState()
    val logUiState by logViewModel.uiState.collectAsState()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    var currentPageMain by remember { mutableStateOf(AppPage.CORE) }
    var currentPagePlain by remember { mutableStateOf(AppSubPage.DEBUG) }
    val navController = rememberNavController()
    val mainScrollBehavior = MiuixScrollBehavior()

    DisposableEffect(currentPageMain) {
        logViewModel.onPageVisibilityChanged(currentPageMain == AppPage.LOGS)
        onDispose {
            if (currentPageMain == AppPage.LOGS) {
                logViewModel.onPageVisibilityChanged(false)
            }
        }
    }

    LaunchedEffect(currentPageMain, coreUiState.state) {
        if (currentPageMain == AppPage.DEVICES && coreUiState.state == CoreState.RUNNING) {
            devicesViewModel.refresh()
        }
    }

    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        NavHost(
            navController = navController,
            startDestination = MAIN_PAGE_ROUTE,
            enterTransition = {
                slideIntoContainer(
                    towards = SlideDirection.Start,
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = SlideDirection.Start,
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = SlideDirection.End,
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = SlideDirection.End,
                    animationSpec = tween(300),
                )
            },
        ) {
            composable(MAIN_PAGE_ROUTE) {
                Scaffold(
                    topBar = {
                        AdaptiveTopAppBar(
                            title = currentPageMain.title,
                            showTopAppBar = true,
                            scrollBehavior = if (currentPageMain != AppPage.LOGS) {
                                mainScrollBehavior
                            } else {
                                MiuixScrollBehavior()
                            },
                            actions = {
                                if (currentPageMain == AppPage.DEVICES && coreUiState.state == CoreState.RUNNING) {
                                    IconButton(
                                        onClick = {
                                            currentPagePlain = AppSubPage.DEVICE_ADD
                                            navController.navigate(PLAIN_PAGE_ROUTE)
                                        },
                                        content = {
                                            Icon(
                                                contentDescription = "添加设备",
                                                imageVector = MiuixIcons.Add
                                            )
                                        },
                                    )
                                }
                            },
                            // isWideScreen = isWideScreen,
                            isWideScreen = false,
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.DEVICES,
                                onClick = { currentPageMain = AppPage.DEVICES },
                                icon = MiuixIcons.Link,
                                label = "连接",
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.FOLDERS,
                                onClick = { currentPageMain = AppPage.FOLDERS },
                                icon = MiuixIcons.Folder,
                                label = "文件夹",
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.CORE,
                                onClick = { currentPageMain = AppPage.CORE },
                                icon = MiuixIcons.Home,
                                label = "核心",
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.LOGS,
                                onClick = { currentPageMain = AppPage.LOGS },
                                icon = MiuixIcons.Notes,
                                label = "日志",
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.SETTINGS,
                                onClick = { currentPageMain = AppPage.SETTINGS },
                                icon = MiuixIcons.Settings,
                                label = "设置",
                            )
                        }
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .nestedScroll(mainScrollBehavior.nestedScrollConnection),
                    ) {
                        when (currentPageMain) {
                            AppPage.DEVICES -> DevicesScreen(
                                uiState = devicesUiState,
                                coreState = coreUiState.state,
                                topAppBarScrollBehavior = mainScrollBehavior,
                                onRefresh = devicesViewModel::refresh,
                            )

                            AppPage.FOLDERS,
                            AppPage.SETTINGS -> EmptyScreen()

                            AppPage.CORE -> CoreScreen(
                                uiState = coreUiState,
                                onStartAction = if (coreUiState.isStarted) {
                                    coreViewModel::onStopClicked
                                } else {
                                    coreViewModel::onStartClicked
                                },
                                onImportCore = coreViewModel::onImportCoreClicked,
                            )

                            AppPage.LOGS -> LogScreen(
                                uiState = logUiState,
                                onSourceSelected = logViewModel::onSourceSelected,
                            )
                        }
                    }
                }
            }

            composable(PLAIN_PAGE_ROUTE) {
                val navigateBack = {
                    navController.popBackStack()
                    Unit
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = currentPagePlain.title,
                            navigationIcon = {
                                IconButton(onClick = navigateBack) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = "返回",
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    when (currentPagePlain) {
                        AppSubPage.DEBUG -> {}
                        AppSubPage.DEVICE_ADD -> {
                            AddDeviceScreen(
                                isSubmitting = devicesUiState.isLoading,
                                onConfirm = { configuration ->
                                    devicesViewModel.addDevice(configuration)
                                    navigateBack()
                                },
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAIN_PAGE_ROUTE = "main"
private const val PLAIN_PAGE_ROUTE = "devices/add"

private enum class AppPage(val title: String) {
    DEVICES("连接"),
    FOLDERS("文件夹"),
    CORE("Syncthing"),
    LOGS("日志"),
    SETTINGS("设置"),
}

private enum class AppSubPage(val title: String) {
    DEBUG("DEBUG*"),
    DEVICE_ADD("添加设备")
}
