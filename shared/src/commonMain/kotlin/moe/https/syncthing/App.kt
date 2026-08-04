package moe.https.syncthing

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import moe.https.syncthing.core.CoreState
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
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
    var currentPage by remember { mutableStateOf(AppPage.CORE) }
    val navController = rememberNavController()

    DisposableEffect(currentPage) {
        logViewModel.onPageVisibilityChanged(currentPage == AppPage.LOGS)
        onDispose {
            if (currentPage == AppPage.LOGS) {
                logViewModel.onPageVisibilityChanged(false)
            }
        }
    }

    LaunchedEffect(currentPage, coreUiState.state) {
        if (currentPage == AppPage.DEVICES && coreUiState.state == CoreState.RUNNING) {
            devicesViewModel.refresh()
        }
    }

    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        NavHost(
            navController = navController,
            startDestination = MAIN_ROUTE,
        ) {
            composable(MAIN_ROUTE) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = currentPage.title,
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentPage == AppPage.DEVICES,
                                onClick = { currentPage = AppPage.DEVICES },
                                icon = MiuixIcons.Link,
                                label = "连接",
                            )
                            NavigationBarItem(
                                selected = currentPage == AppPage.FOLDERS,
                                onClick = { currentPage = AppPage.FOLDERS },
                                icon = MiuixIcons.Folder,
                                label = "文件夹",
                            )
                            NavigationBarItem(
                                selected = currentPage == AppPage.CORE,
                                onClick = { currentPage = AppPage.CORE },
                                icon = MiuixIcons.Home,
                                label = "核心",
                            )
                            NavigationBarItem(
                                selected = currentPage == AppPage.LOGS,
                                onClick = { currentPage = AppPage.LOGS },
                                icon = MiuixIcons.Notes,
                                label = "日志",
                            )
                            NavigationBarItem(
                                selected = currentPage == AppPage.SETTINGS,
                                onClick = { currentPage = AppPage.SETTINGS },
                                icon = MiuixIcons.Settings,
                                label = "设置",
                            )
                        }
                    },
                ) { padding ->
                    when (currentPage) {
                        AppPage.DEVICES -> DevicesScreen(
                            uiState = devicesUiState,
                            coreState = coreUiState.state,
                            onRefresh = devicesViewModel::refresh,
                            onAddDevice = { navController.navigate(ADD_DEVICE_ROUTE) },
                            modifier = Modifier.padding(padding),
                        )

                        AppPage.FOLDERS,
                        AppPage.SETTINGS -> EmptyScreen(
                            modifier = Modifier.padding(padding),
                        )

                        AppPage.CORE -> CoreScreen(
                            uiState = coreUiState,
                            onStartAction = if (coreUiState.isStarted) {
                                coreViewModel::onStopClicked
                            } else {
                                coreViewModel::onStartClicked
                            },
                            onImportCore = coreViewModel::onImportCoreClicked,
                            modifier = Modifier.padding(padding),
                        )

                        AppPage.LOGS -> LogScreen(
                            uiState = logUiState,
                            onSourceSelected = logViewModel::onSourceSelected,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }

            composable(ADD_DEVICE_ROUTE) {
                val navigateBack = {
                    navController.popBackStack()
                    Unit
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "添加设备",
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
                    AddDeviceScreen(
                        isSubmitting = devicesUiState.isLoading,
                        onCancel = navigateBack,
                        onConfirm = { deviceId, name, addresses ->
                            devicesViewModel.addDevice(deviceId, name, addresses)
                            navigateBack()
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

private const val MAIN_ROUTE = "main"
private const val ADD_DEVICE_ROUTE = "devices/add"

private enum class AppPage(val title: String) {
    DEVICES("连接"),
    FOLDERS("文件夹"),
    CORE("Syncthing"),
    LOGS("日志"),
    SETTINGS("设置"),
}
