package moe.https.syncthing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.serialization.Serializable
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingFolder
import moe.https.syncthing.core.SyncthingPendingDevice
import moe.https.syncthing.core.SyncthingPendingFolder
import moe.https.syncthing.ui.component.AdaptiveTopAppBar
import moe.https.syncthing.ui.screen.AboutScreen
import moe.https.syncthing.ui.screen.AddDeviceScreen
import moe.https.syncthing.ui.screen.AddFolderScreen
import moe.https.syncthing.ui.screen.CoreScreen
import moe.https.syncthing.ui.screen.DevSettingPage
import moe.https.syncthing.ui.screen.DevicesScreen
import moe.https.syncthing.ui.screen.FoldersScreen
import moe.https.syncthing.ui.screen.LicenceScreen
import moe.https.syncthing.ui.screen.LogScreen
import moe.https.syncthing.ui.screen.SettingCoreSelectScreen
import moe.https.syncthing.ui.screen.SettingEditDiscoveryScreen
import moe.https.syncthing.ui.screen.SettingEditListenScreen
import moe.https.syncthing.ui.screen.SettingScreen
import moe.https.syncthing.ui.screen.SettingStoragePermissionPage
import moe.https.syncthing.ui.screen.WebviewScreen
import moe.https.syncthing.viewmodel.LogViewModel
import moe.https.syncthing.viewmodel.CoreViewModel
import moe.https.syncthing.viewmodel.DevicesViewModel
import moe.https.syncthing.viewmodel.FoldersViewModel
import moe.https.syncthing.viewmodel.SettingViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun App(
    coreViewModel: CoreViewModel,
    logViewModel: LogViewModel,
    devicesViewModel: DevicesViewModel,
    foldersViewModel: FoldersViewModel,
    settingViewModel: SettingViewModel,
    versionName: String,
    developerModeEnabled: Boolean,
    onModifyDeveloperMode: () -> Unit,
    onScanQrCode: () -> Unit,
    onRequestPublicStorageAccess: () -> Unit,
    scannedDeviceId: String,
    webUiUrlProvider: () -> String,
    webView: @Composable (
        url: String,
        reloadToken: Int,
        onScroll: (deltaY: Float, isAtTop: Boolean) -> Unit,
        modifier: Modifier,
    ) -> Unit,
) {
    val coreUiState by coreViewModel.uiState.collectAsState()
    val logUiState by logViewModel.uiState.collectAsState()
    val devicesUiState by devicesViewModel.uiState.collectAsState()
    val foldersUiState by foldersViewModel.uiState.collectAsState()
    val settingUiState by settingViewModel.uiState.collectAsState()
    var currentPageMain by remember { mutableStateOf(AppPage.CORE) }
    var requestedPageMain by remember { mutableStateOf(AppPage.CORE) }
    var editingDevice by remember { mutableStateOf<SyncthingDevice?>(null) }
    var pendingDeviceToAdd by remember { mutableStateOf<SyncthingPendingDevice?>(null) }
    var editingFolder by remember { mutableStateOf<SyncthingFolder?>(null) }
    var pendingFolderToAdd by remember { mutableStateOf<SyncthingPendingFolder?>(null) }
    var webUiReloadToken by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val plainPageSnackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController<AppRoute>(AppRoute.Main)
    val mainScrollBehavior = MiuixScrollBehavior()

    val controller = remember {
        ThemeController(
            ColorSchemeMode.System,
        )
    }

    fun navigateTo(page: AppSubPage) {
        navController.push(AppRoute.Plain(page))
    }

    DisposableEffect(currentPageMain) {
        logViewModel.onPageVisibilityChanged(false)
        onDispose { }
    }

    LaunchedEffect(
        requestedPageMain,
        coreUiState.state,
        settingUiState.errorMessage,
        settingUiState.successMessage,
        devicesUiState.isLoading,
        foldersUiState.isLoading,
        settingUiState.isLoading,
        logUiState.isLoading,
    ) {
        val ready = when (requestedPageMain) {
            AppPage.DEVICES -> !devicesUiState.isLoading
            AppPage.FOLDERS -> !foldersUiState.isLoading
            AppPage.SETTINGS -> !settingUiState.isLoading
            else -> true
        }

        if (ready) {
            currentPageMain = requestedPageMain
        }

        if (coreUiState.state != CoreState.RUNNING) {
            settingViewModel.onCoreUnavailable()
        }

        when {
            !settingUiState.errorMessage.isNullOrBlank() -> {
                snackbarHostState.showSnackbar(
                    "保存失败：${settingUiState.errorMessage}",
                )
            }

            !settingUiState.successMessage.isNullOrBlank() -> {
                snackbarHostState.showSnackbar(
                    "保存成功：${settingUiState.successMessage}",
                )
            }
        }
    }

    fun requestSwitchToPageMain( targetPage: AppPage ) {
        requestedPageMain = targetPage

        when (targetPage) {
            AppPage.DEVICES -> devicesViewModel.refresh()
            AppPage.FOLDERS -> foldersViewModel.refresh()
            AppPage.SETTINGS -> settingViewModel.refresh()
            else -> currentPageMain = targetPage
        }
    }

    MiuixTheme(
        controller = controller,
    ) {
        val swipeBackDirection = when (LocalLayoutDirection.current) {
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        }

        NavDisplay(
            navController = navController,
            effects = NavDisplayEffects(blockInputDuringTransition = true),
        ) {
            entry<AppRoute.Main> {
                Scaffold(
                    topBar = {
                        AdaptiveTopAppBar(
                            title = currentPageMain.title,
                            showTopAppBar = true,
                            scrollBehavior = mainScrollBehavior,
                            actions = {
                                if (currentPageMain == AppPage.DEVICES && coreUiState.state == CoreState.RUNNING) {
                                    IconButton(
                                        onClick = {
                                            editingDevice = null
                                            pendingDeviceToAdd = null
                                            navigateTo(AppSubPage.DEVICE_ADD)
                                        },
                                        content = {
                                            Icon(
                                                contentDescription = "添加设备",
                                                imageVector = MiuixIcons.Add
                                            )
                                        },
                                    )
                                }
                                if ( currentPageMain == AppPage.SETTINGS ) {
                                    IconButton(
                                        onClick = settingViewModel::save,
                                        enabled = settingUiState.isFormValid && !settingUiState.isSaving,
                                        content = {
                                            Icon(
                                                contentDescription = "保存",
                                                imageVector = MiuixIcons.Send,
                                                tint = if (settingUiState.isFormValid && !settingUiState.isSaving) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    )
                                }
                                if ( currentPageMain == AppPage.WEBUI && coreUiState.state == CoreState.RUNNING ) {
                                    IconButton(
                                        onClick = { webUiReloadToken += 1 },
                                        content = {
                                            Icon(
                                                contentDescription = "刷新",
                                                imageVector = MiuixIcons.Refresh,
                                            )
                                        },
                                    )
                                }
                                if ( currentPageMain == AppPage.FOLDERS && coreUiState.state == CoreState.RUNNING ) {
                                    IconButton(
                                        onClick = {
                                            editingFolder = null
                                            pendingFolderToAdd = null
                                            devicesViewModel.refresh()
                                            navigateTo(AppSubPage.FOLDER_ADD)
                                        },
                                        content = {
                                            Icon(
                                                contentDescription = "添加文件夹",
                                                imageVector = MiuixIcons.Add,
                                            )
                                        },
                                    )
                                }
                            },
                            isWideScreen = false,
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.DEVICES,
                                onClick = { requestSwitchToPageMain(AppPage.DEVICES) },
                                icon = MiuixIcons.Link,
                                label = AppPage.DEVICES.title,
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.FOLDERS,
                                onClick = { requestSwitchToPageMain(AppPage.FOLDERS) },
                                icon = MiuixIcons.Folder,
                                label = AppPage.FOLDERS.title,
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.CORE,
                                onClick = { requestSwitchToPageMain( AppPage.CORE) },
                                icon = MiuixIcons.Home,
                                label = AppPage.CORE.title,
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.WEBUI,
                                onClick = { requestSwitchToPageMain( AppPage.WEBUI) },
                                icon = MiuixIcons.HorizontalSplit,
                                label = AppPage.WEBUI.title,
                            )
                            NavigationBarItem(
                                selected = currentPageMain == AppPage.SETTINGS,
                                onClick = { requestSwitchToPageMain(AppPage.SETTINGS) },
                                icon = MiuixIcons.Settings,
                                label = AppPage.SETTINGS.title,
                            )
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .nestedScroll(mainScrollBehavior.nestedScrollConnection),
                    ) {
                        AnimatedContent(
                            targetState = currentPageMain,
                            transitionSpec = {
                                if (targetState.ordinal > initialState.ordinal) {
                                    (slideInHorizontally { it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                                            (slideOutHorizontally { it } + fadeOut())
                                }
                            },
                            label = "MainPageTransition",
                        ) { page ->
                            when (page) {
                                AppPage.DEVICES -> DevicesScreen(
                                    uiState = devicesUiState,
                                    coreState = coreUiState.state,
                                    topAppBarScrollBehavior = mainScrollBehavior,
                                    onRefresh = devicesViewModel::refresh,
                                    onAddPendingDevice = { device ->
                                        editingDevice = null
                                        pendingDeviceToAdd = device
                                        navigateTo(AppSubPage.DEVICE_ADD)
                                    },
                                    onDismissPendingDevice = devicesViewModel::dismissPendingDevice,
                                    onIgnorePendingDevice = devicesViewModel::ignorePendingDevice,
                                    onDeleteDevice = devicesViewModel::deleteDevice,
                                    onEditDevice = { device ->
                                        editingDevice = device
                                        pendingDeviceToAdd = null
                                        navigateTo(AppSubPage.DEVICE_ADD)
                                    },
                                )

                                AppPage.FOLDERS -> FoldersScreen(
                                    uiState = foldersUiState,
                                    coreState = coreUiState.state,
                                    topAppBarScrollBehavior = mainScrollBehavior,
                                    onRefresh = foldersViewModel::refresh,
                                    onAddPendingFolder = { folder ->
                                        editingFolder = null
                                        pendingFolderToAdd = folder
                                        devicesViewModel.refresh()
                                        navigateTo(AppSubPage.FOLDER_ADD)
                                    },
                                    onDismissPendingFolder = foldersViewModel::dismissPendingFolder,
                                    onIgnorePendingFolder = foldersViewModel::ignorePendingFolder,
                                    onEditFolder = { folder ->
                                        editingFolder = folder
                                        pendingFolderToAdd = null
                                        devicesViewModel.refresh()
                                        navigateTo(AppSubPage.FOLDER_ADD)
                                    },
                                )

                                AppPage.SETTINGS -> SettingScreen(
                                    uiState = settingUiState,
                                    settingViewModel = settingViewModel,
                                    developerModeEnabled = developerModeEnabled,
                                    onModifyDeveloperMode = onModifyDeveloperMode,
                                    onChangeToAbout = {
                                        navigateTo(AppSubPage.ABOUT)
                                    },
                                    onEditingStoragePermission = {
                                        navigateTo(AppSubPage.SETTINGS_STORAGE_PERMISSION)
                                    },
                                    onEditingListenAddresses = {
                                        navigateTo(AppSubPage.SETTINGS_LISTEN_EDIT)
                                    },
                                    onEditingDiscoverServers = {
                                        navigateTo(AppSubPage.SETTINGS_DISCOVERY_EDIT)
                                    },
                                    onEditingCores = {
                                        navigateTo(AppSubPage.SETTINGS_CORE_MANAGE)
                                    },
                                    onChangeToLicence = {
                                        navigateTo(AppSubPage.LICENCE)
                                    },
                                    onRedirectingToDeveloperPage = {
                                        navigateTo(AppSubPage.DEV)
                                    }
                                )

                                AppPage.CORE -> CoreScreen(
                                    uiState = coreUiState,
                                    onStartAction = if (coreUiState.isStarted) {
                                        coreViewModel::onStopClicked
                                    } else {
                                        coreViewModel::onStartClicked
                                    },
                                    snackbarHostState = snackbarHostState,
                                    developerModeEnabled = developerModeEnabled,
                                    onModifyDeveloperMode = onModifyDeveloperMode,
                                )

                                AppPage.WEBUI -> WebviewScreen(
                                    coreState = coreUiState.state,
                                    topAppBarScrollBehavior = mainScrollBehavior,
                                    webUiUrl = if (coreUiState.state == CoreState.RUNNING) {
                                        webUiUrlProvider()
                                    } else {
                                        null
                                    },
                                    reloadToken = webUiReloadToken,
                                    webView = webView,
                                )

                            }
                        }
                    }
                }
            }

            entry<AppRoute.Plain>(swipeDismiss = swipeBackDirection) { route ->
                val currentPagePlain = route.page
                val navigateBack = {
                    if (currentPagePlain == AppSubPage.DEVICE_ADD) {
                        editingDevice = null
                        pendingDeviceToAdd = null
                    }
                    if (currentPagePlain == AppSubPage.FOLDER_ADD) {
                        editingFolder = null
                        pendingFolderToAdd = null
                    }
                    navController.pop()
                    Unit
                }

                Scaffold(
                    topBar = {
                        AdaptiveTopAppBar(
                            title = currentPagePlain.title,
                            showTopAppBar = true,
                            isWideScreen = false,
                            scrollBehavior = mainScrollBehavior,
                            navigationIcon = {
                                when (currentPagePlain) {
                                    AppSubPage.FOLDER_ADD,
                                    AppSubPage.DEVICE_ADD -> {
                                        IconButton(onClick = navigateBack) {
                                            Icon(
                                                imageVector = MiuixIcons.Close,
                                                contentDescription = "取消",
                                            )
                                        }
                                    }
                                    else -> {
                                        IconButton(onClick = navigateBack) {
                                            Icon(
                                                imageVector = MiuixIcons.Back,
                                                contentDescription = "返回",
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                when (currentPagePlain) {
                                    AppSubPage.DEVICE_ADD -> {
                                        if ( editingDevice == null && pendingDeviceToAdd == null ) {
                                            IconButton(
                                                onClick = {
                                                    onScanQrCode()
                                                },
                                                content = {
                                                    Icon(
                                                        contentDescription = "扫描二维码",
                                                        imageVector = MiuixIcons.Scan
                                                    )
                                                },
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                // TODO
                                            },
                                            content = {
                                                Icon(
                                                    contentDescription = "保存",
                                                    imageVector = MiuixIcons.Ok
                                                )
                                            },
                                        )
                                    }
                                    AppSubPage.FOLDER_ADD -> {
                                        IconButton(
                                            onClick = {
                                                // TODO
                                            },
                                            content = {
                                                Icon(
                                                    contentDescription = "保存",
                                                    imageVector = MiuixIcons.Ok
                                                )
                                            },
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(state = plainPageSnackbarHostState)
                    },
                ) { padding ->
                    Box (
                        modifier = Modifier
                            .padding(padding)
                            .nestedScroll(
                            mainScrollBehavior.nestedScrollConnection,
                        )
                    ) {
                        when (currentPagePlain) {
                            AppSubPage.DEBUG -> {
                                LogScreen(
                                    uiState = logUiState,
                                    onSourceSelected = logViewModel::onSourceSelected,
                                )
                            }

                            AppSubPage.SETTINGS_STORAGE_PERMISSION -> {
                                SettingStoragePermissionPage(
                                    onRequestPermission = onRequestPublicStorageAccess,
                                )
                            }

                            AppSubPage.DEVICE_ADD -> {
                                AddDeviceScreen(
                                    isSubmitting = devicesUiState.isLoading,
                                    existingDevice = editingDevice,
                                    pendingDevice = pendingDeviceToAdd,
                                    scannedDeviceId = scannedDeviceId,
                                    onConfirm = { configuration ->
                                        if (editingDevice == null) devicesViewModel.addDevice(
                                            configuration
                                        )
                                        else devicesViewModel.updateDevice(configuration)
                                        editingDevice = null
                                        pendingDeviceToAdd = null
                                        navigateBack()
                                    },
                                )
                            }

                            AppSubPage.FOLDER_ADD -> {
                                AddFolderScreen(
                                    isSubmitting = foldersUiState.isLoading,
                                    devices = devicesUiState.devices,
                                    existingFolder = editingFolder,
                                    pendingFolder = pendingFolderToAdd,
                                    snackbarHostState = plainPageSnackbarHostState,
                                    onConfirm = { configuration ->
                                        if (editingFolder == null) foldersViewModel.addFolder(
                                            configuration
                                        )
                                        else foldersViewModel.updateFolder(configuration)
                                        editingFolder = null
                                        pendingFolderToAdd = null
                                        navigateBack()
                                    },
                                )
                            }

                            AppSubPage.ABOUT -> {
                                AboutScreen(
                                    versionName = versionName,
                                )
                            }

                            AppSubPage.LICENCE -> {
                                LicenceScreen()
                            }

                            AppSubPage.SETTINGS_LISTEN_EDIT -> {
                                SettingEditListenScreen(
                                    settingViewModel = settingViewModel,
                                )
                            }

                            AppSubPage.SETTINGS_DISCOVERY_EDIT -> {
                                SettingEditDiscoveryScreen(
                                    settingViewModel = settingViewModel,
                                )
                            }

                            AppSubPage.SETTINGS_CORE_MANAGE -> {
                                SettingCoreSelectScreen(
                                    uiState = coreUiState,
                                    snackbarHostState = plainPageSnackbarHostState,
                                    onCoreSelected = coreViewModel::onCoreSelected,
                                    onImportCore = coreViewModel::onImportCoreClicked,
                                    onCoreDelete = coreViewModel::onCoreDelete,
                                )
                            }

                            AppSubPage.DEV -> {
                                DevSettingPage(
                                    requestSwitchToPageMain = { appPage ->
                                        requestSwitchToPageMain(appPage)
                                        navController.popUntil { it is AppRoute.Main }
                                    },
                                    requestSwitchToPagePlain = { appSubPage ->
                                        if (appSubPage != currentPagePlain) {
                                            navigateTo(appSubPage)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
private sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data class Plain(val page: AppSubPage) : AppRoute
}

internal enum class AppPage(val title: String) {
    DEVICES("连接"),
    FOLDERS("文件夹"),
    CORE("Syncthing"),
    WEBUI("WebUI"),
    SETTINGS("设置"),
}

@Serializable
internal enum class AppSubPage(val title: String) {
    DEBUG("DEBUG*"),
    DEVICE_ADD("设备"),
    FOLDER_ADD("文件夹"),
    ABOUT("关于"),
    LICENCE("开源许可"),
    SETTINGS_LISTEN_EDIT("监听地址"),
    SETTINGS_DISCOVERY_EDIT("发现服务器"),
    SETTINGS_STORAGE_PERMISSION("存储权限"),
    SETTINGS_CORE_MANAGE("核心管理"),
    DEV("开发者设置"),
}
