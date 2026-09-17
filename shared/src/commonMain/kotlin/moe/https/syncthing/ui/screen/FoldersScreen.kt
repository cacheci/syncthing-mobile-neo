package moe.https.syncthing.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import moe.https.syncthing.core.CoreState
import moe.https.syncthing.core.FolderDeviceConfiguration
import moe.https.syncthing.core.NewFolderConfiguration
import moe.https.syncthing.core.RemoteFolderState
import moe.https.syncthing.core.SyncthingDevice
import moe.https.syncthing.core.SyncthingFolder
import moe.https.syncthing.core.SyncthingPendingFolder
import moe.https.syncthing.core.defaultFolderPath
import moe.https.syncthing.ui.component.BlurredSmallTopAppBar
import moe.https.syncthing.ui.component.CoreNotReadyTakePlace
import moe.https.syncthing.ui.component.InfoSwitch
import moe.https.syncthing.ui.component.InfoSwitchCard
import moe.https.syncthing.ui.component.InputValueRow
import moe.https.syncthing.ui.component.MultipleValueRow
import moe.https.syncthing.ui.component.PendingCard
import moe.https.syncthing.ui.component.barBackdropSource
import moe.https.syncthing.ui.model.FoldersUiState
import moe.https.syncthing.ui.resources.StatusColor
import moe.https.syncthing.ui.util.formatBytes
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.scripta.editor.CodeEditor
import top.yukonga.scripta.editor.EditorColors
import top.yukonga.scripta.editor.EditorLanguage
import top.yukonga.scripta.editor.EditorSymbol
import top.yukonga.scripta.editor.rememberSaveableCodeEditorController

@Composable
internal fun FoldersScreen(
    uiState: FoldersUiState,
    coreState: CoreState,
    topAppBarScrollBehavior: ScrollBehavior,
    onRefresh: () -> Unit,
    onAddPendingFolder: (SyncthingPendingFolder) -> Unit,
    onDismissPendingFolder: (SyncthingPendingFolder) -> Unit,
    onIgnorePendingFolder: (SyncthingPendingFolder) -> Unit,
    onEditFolder: (SyncthingFolder) -> Unit,
    onSetFolderPaused: (folderId: String, paused: Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    uiPadding: PaddingValues,
    pagePaddingHorizontal: Dp,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.takeIf(String::isNotBlank)?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    when {
        coreState != CoreState.RUNNING -> CoreNotReadyTakePlace(
            title = "核心未运行",
            message = "启动后才能读取文件夹状态。",
        )

        uiState.isLoading && uiState.folders.isEmpty() && uiState.pendingFolders.isEmpty() -> {}

        uiState.loadError != null -> CoreNotReadyTakePlace(
            title = "读取失败",
            message = uiState.loadError,
            isError = true,
        )

        uiState.hasLoaded && uiState.folders.isEmpty() && uiState.pendingFolders.isEmpty() -> CoreNotReadyTakePlace(
            title = "暂无文件夹",
            message = "当前还没有配置文件夹。",
        )

        else -> {
            PullToRefresh(
                modifier = Modifier.padding(top = uiPadding.calculateTopPadding()),
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                refreshTexts = listOf("下拉刷新", "松手刷新"),
            ) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = uiPadding.calculateBottomPadding())
                        .padding(horizontal = pagePaddingHorizontal, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.pendingFolders.forEach { folder ->
                        key("pending:${folder.id}:${folder.source}") {
                            NewFolderCard(
                                folder = folder,
                                enabled = !uiState.isPendingFolderActionInProgress,
                                onAdd = { onAddPendingFolder(folder) },
                                onDismiss = { onDismissPendingFolder(folder) },
                                onIgnore = { onIgnorePendingFolder(folder) },
                            )
                        }
                    }
                    uiState.folders.forEach { folder ->
                        key(folder.id) {
                            FolderCard(
                                folder = folder,
                                isLoading = !uiState.isLoading,
                                onEditFolder = onEditFolder,
                                onSetPaused = { paused ->
                                    onSetFolderPaused(folder.id, paused)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: SyncthingFolder,
    isLoading: Boolean,
    onEditFolder: (SyncthingFolder) -> Unit,
    onSetPaused: (Boolean) -> Unit,
) {
    var holdDown by rememberSaveable { mutableStateOf(false) }
    var foldContentStatus by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        holdDownState = holdDown,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { foldContentStatus = !foldContentStatus },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "●",
                        color = folder.statusColor(),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = folder.label?.takeIf(String::isNotBlank) ?: folder.id,
                        style = MiuixTheme.textStyles.headline1,
                    )
                }
                Text(
                    text = folder.statusName(),
                    color = folder.statusColor(),
                    fontWeight = FontWeight.Medium,
                )
            }

            AnimatedVisibility(
                visible = foldContentStatus,
                enter = expandVertically(animationSpec = tween(durationMillis = 300)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 300)),
            ) {
                Column (verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    FolderValueRow("文件夹 ID", folder.id)
                    FolderValueRow("路径", folder.path)
                    FolderValueRow("类型", folder.typeName())
                    FolderValueRow(
                        "本地数据",
                        "${folder.localFiles} 个文件 · ${formatBytes(folder.localBytes)}",
                    )
                    FolderValueRow(
                        "待同步",
                        "${folder.needFiles} 个文件 · ${formatBytes(folder.needBytes)}",
                    )
                    if (folder.pullErrors > 0) {
                        FolderValueRow("同步错误", "${folder.pullErrors} 个文件", isError = true)
                    }
                    Row (
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = if (folder.paused) "恢复" else "暂停",
                            enabled = isLoading,
                            onClick = { onSetPaused(!folder.paused) },
                        )
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = "编辑",
                            enabled = isLoading,
                            onClick = { onEditFolder( folder ) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderValueRow(
    label: String,
    value: String,
    isError: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun NewFolderCard(
    folder: SyncthingPendingFolder,
    enabled: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
) {
    PendingCard(title = "远程文件夹：${folder.name}") {
        Column (
            modifier = Modifier.padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MultipleValueRow(
                label = "文件夹 ID",
                values = listOf(folder.id),
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            MultipleValueRow(
                label = "共享来源",
                values = listOf(folder.sourceName),
                modifier = Modifier.padding(horizontal = 18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(0.3f),
                    text = "黑名单",
                    enabled = enabled,
                    onClick = onIgnore,
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                )
                TextButton(
                    modifier = Modifier.weight(0.3f),
                    text = "忽略",
                    enabled = enabled,
                    onClick = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(0.3f),
                    text = "添加",
                    enabled = enabled,
                    onClick = onAdd,
                )
            }
        }
    }
}

@Composable
internal fun AddFolderScreen(
    isSubmitting: Boolean,
    devices: List<SyncthingDevice>,
    selectedFolderPath: String?,
    onConfirm: (NewFolderConfiguration) -> Unit,
    onRedirectToPathChooserPage: (folderId: String) -> Unit,
    navigateBack: () -> Unit,
    pagePaddingHorizontal: Dp,
    barBackdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    actionError: String? = null,
    existingFolder: SyncthingFolder? = null,
    pendingFolder: SyncthingPendingFolder? = null,
    onDeleteFolder: (folderId: String, deleteLocalFiles: Boolean) -> Unit = { _, _ -> },
) {
    val isEditingFolder = (existingFolder != null)
    val isAddingRemote = (pendingFolder != null)
    var label by remember(existingFolder, pendingFolder) {
        mutableStateOf(existingFolder?.label ?: pendingFolder?.name.orEmpty())
    }
    var group by remember(existingFolder) { mutableStateOf(existingFolder?.group.orEmpty()) }
    var folderId by remember(existingFolder, pendingFolder) {
        mutableStateOf(existingFolder?.id ?: pendingFolder?.id.orEmpty())
    }
    var versioning by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioning ?: NewFolderConfiguration.Versioning.NONE)
    }
    val versioningOptions = NewFolderConfiguration.Versioning.entries
    var versioningFsPath by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioningFsPath.orEmpty())
    }
    var cleanoutDays by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioningCleanoutDays?.toString().orEmpty())
    }
    var keepVersions by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioningKeep?.toString() ?: "5")
    }
    var cleanupIntervalSeconds by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioningCleanupIntervalSeconds?.toString() ?: "3600")
    }
    var externalCommand by remember(existingFolder) {
        mutableStateOf(existingFolder?.versioningExternalCommand.orEmpty())
    }
    var ignorePatternsEnabled by remember(existingFolder) {
        mutableStateOf(false)
    }
    val initialIgnoreText = existingFolder?.ignorePatterns?.joinToString("\n").orEmpty()
    var acceptedIgnoreText by rememberSaveable(existingFolder?.id) {
        mutableStateOf(initialIgnoreText)
    }
    val ignoreEditorController = rememberSaveableCodeEditorController(
        initialText = initialIgnoreText,
    )
    var fsWatcherEnabled by remember(existingFolder) {
        mutableStateOf(existingFolder?.fsWatcherEnabled ?: true)
    }
    var rescanIntervalSeconds by remember(existingFolder) {
        mutableStateOf(existingFolder?.rescanIntervalSeconds?.toString() ?: "3600")
    }
    var folderType by remember(existingFolder) {
        mutableStateOf(
            when (existingFolder?.type) {
                "receiveonly" -> NewFolderConfiguration.Type.RECEIVE_ONLY
                "sendonly" -> NewFolderConfiguration.Type.SEND_ONLY
                "receiveencrypted" -> NewFolderConfiguration.Type.RECEIVE_ENCRYPTED
                else -> NewFolderConfiguration.Type.SEND_RECEIVE
            },
        )
    }
    val folderTypeOptions = if (isEditingFolder) {
        NewFolderConfiguration.Type.entries.filter { type ->
            type != NewFolderConfiguration.Type.RECEIVE_ENCRYPTED ||
                folderType == NewFolderConfiguration.Type.RECEIVE_ENCRYPTED
        }
    } else {
        NewFolderConfiguration.Type.entries
    }
    val folderTypeNames = mapOf(
        NewFolderConfiguration.Type.SEND_RECEIVE to "发送和接收",
        NewFolderConfiguration.Type.RECEIVE_ONLY to "仅接收",
        NewFolderConfiguration.Type.SEND_ONLY to "仅发送",
        NewFolderConfiguration.Type.RECEIVE_ENCRYPTED to "加密接收",
    )
    val isReceiveEncrypted = folderType == NewFolderConfiguration.Type.RECEIVE_ENCRYPTED
    val remoteDevices = devices.filterNot { it.isLocal }
    val remoteDeviceIds = remoteDevices.map { it.id }
    var selectedDeviceIds by remember(existingFolder, pendingFolder, remoteDeviceIds) {
        mutableStateOf(
            existingFolder?.devices?.map { it.deviceId }?.toSet()
                ?: pendingFolder?.let { setOf(it.source) }
                ?: remoteDeviceIds.toSet(),
        )
    }
    var devicePasswords by remember(existingFolder, remoteDeviceIds) {
        mutableStateOf(
            existingFolder?.devices
                ?.associate { it.deviceId to it.encryptionPassword }
                .orEmpty(),
        )
    }
    val defaultPath = if (folderId.isBlank()) null else {
        defaultFolderPath(folderId.trim())
    }
    val canSubmit = (
            (folderId.trim().isNotBlank()) &&
            (listOf(
                cleanoutDays,
                keepVersions,
                cleanupIntervalSeconds,
                rescanIntervalSeconds,
            ).all { value -> (value.toIntWithDefaultForEmpty(0))?.let{ it >= 0 } == true }) &&
            (cleanupIntervalSeconds.toIntWithDefaultForEmpty(3600)?.let{ it <= 31_536_000 } == true) &&
            (versioning != NewFolderConfiguration.Versioning.EXTERNAL || externalCommand.trim().isNotBlank()) &&
            (isReceiveEncrypted || remoteDevices
                .filter { it.untrusted && it.id in selectedDeviceIds }
                .all { device -> devicePasswords[device.id].orEmpty().isNotBlank() }) &&
            (!isSubmitting)
    )

    var showEditorBottomSheet by remember { mutableStateOf(false) }
    var showStIgnoreHelp by remember { mutableStateOf(false) }
    var showDeleteOverlay by rememberSaveable { mutableStateOf(false) }
    var deleteLocalFiles by rememberSaveable { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()
    LaunchedEffect(actionError) {
        actionError?.takeIf(String::isNotBlank)?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { BlurredSmallTopAppBar(
            title = if (isEditingFolder) "编辑文件夹" else "添加文件夹",
            scrollBehavior = scrollBehavior,
            backdrop = barBackdrop,
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消",
                    )
                }
            },
            actions = {
                IconButton(
                    enabled = canSubmit,
                    onClick = {
                        onConfirm(
                            NewFolderConfiguration(
                                folderId = folderId,
                                label = label,
                                group = group,
                                path = selectedFolderPath ?: defaultFolderPath(folderId.trim()),
                                versioning = versioning,
                                updateVersioning = existingFolder?.versioningSupported != false,
                                versioningFsPath = versioningFsPath.trim(),
                                versioningCleanoutDays = cleanoutDays.toIntOrNull() ?: 0,
                                versioningKeep = keepVersions.toIntOrNull() ?: 5,
                                versioningCleanupIntervalSeconds = cleanupIntervalSeconds.toIntOrNull() ?: 3600,
                                versioningExternalCommand = externalCommand.trim(),
                                ignorePatterns = if (isEditingFolder) {
                                    acceptedIgnoreText.toIgnorePatternLines()
                                } else {
                                    emptyList()
                                },
                                updateIgnorePatterns = if (isEditingFolder) {
                                    acceptedIgnoreText != initialIgnoreText
                                } else {
                                    ignorePatternsEnabled
                                },
                                fsWatcherEnabled = fsWatcherEnabled,
                                rescanIntervalSeconds = rescanIntervalSeconds.toIntOrNull() ?: 3600,
                                type = folderType,
                                devices = remoteDevices
                                    .filter { it.id in selectedDeviceIds }
                                    .map { device ->
                                        FolderDeviceConfiguration(
                                            deviceId = device.id,
                                            encryptionPassword = if (isReceiveEncrypted) {
                                                ""
                                            } else {
                                                devicePasswords[device.id].orEmpty()
                                            },
                                        )
                                    },
                                availableDeviceIds = remoteDeviceIds.toSet(),
                            ),
                        )
                    },
                    content = {
                        Icon(
                            contentDescription = if (isEditingFolder) "保存" else "添加",
                            imageVector = MiuixIcons.Ok,
                            tint = if (canSubmit) {
                                MiuixTheme.colorScheme.onSurface
                            } else MiuixTheme.colorScheme.disabledOnSurface
                        )
                    },
                )
            }
        ) },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { padding ->
        Box (
            modifier = Modifier
                .barBackdropSource(barBackdrop)
                .padding(padding)
                .nestedScroll(
                    scrollBehavior.nestedScrollConnection,
                )
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pagePaddingHorizontal),
            ) {
                InfoSwitchCard(
                    title = "文件夹",
                    content = {
                        InputValueRow(
                            label = "文件夹 ID",
                            summary = "区分大小写，所有设备上必须相同",
                            value = folderId,
                            valueLabel = "必填，唯一",
                            allowEdit = !isSubmitting && !isEditingFolder && !isAddingRemote,
                            onValueChange = { folderId = it },
                        )

                        InputValueRow(
                            label = "名称",
                            value = label,
                            valueLabel = "可选",
                            allowEdit = !isSubmitting,
                            onValueChange = { label = it },
                        )

                        InputValueRow(
                            label = "文件夹组",
                            summary = "文件夹的可选分组",
                            value = group,
                            valueLabel = "可选",
                            allowEdit = !isSubmitting,
                            onValueChange = { group = it },
                        )

                        ArrowPreference(
                            title = "文件夹位置",
                            summary = selectedFolderPath ?: defaultPath ?: "本地计算机上文件夹的路径",
                            onClick = {
                                onRedirectToPathChooserPage(folderId.trim())
                            },
                            enabled = !isSubmitting,
                        )
                    }
                )

                InfoSwitchCard(
                    title = "设备",
                    content = {
                        if (remoteDevices.isEmpty()) {
                            Text(
                                text = "无设备",
                                color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            )
                        } else {
                            remoteDevices.forEach { device ->
                                AddFolderDevices(
                                    device = device,
                                    isSubmitting = isSubmitting,
                                    selected = device.id in selectedDeviceIds,
                                    remoteFolderState = existingFolder?.devices
                                        ?.firstOrNull { it.deviceId == device.id }
                                        ?.remoteFolderState,
                                    encryptionPassword = if (isReceiveEncrypted) null else {
                                        devicePasswords[device.id].orEmpty()
                                    },
                                    onSelectedChange = { selected ->
                                        selectedDeviceIds = if (selected) {
                                            selectedDeviceIds + device.id
                                        } else {
                                            selectedDeviceIds - device.id
                                        }
                                    },
                                    onEncryptionPasswordChange = { password ->
                                        devicePasswords = devicePasswords + (device.id to password)
                                    },
                                )
                            }
                        }
                    }
                )

                InfoSwitchCard(
                    title = "版本控制",
                    content = {
                        WindowDropdownPreference(
                            title = "文件版本控制",
                            summary = when {
                                isReceiveEncrypted -> "接收加密数据不支持文件版本控制"
                                versioning == NewFolderConfiguration.Versioning.TRASHCAN ->
                                    "当 Syncthing 替换或删除文件时，文件将移动到 .stversions 目录。"
                                versioning == NewFolderConfiguration.Versioning.SIMPLE ->
                                    "当 Syncthing 替换或删除文件时，文件将移动到 .stversions 目录，文件名带有时间戳。"
                                versioning == NewFolderConfiguration.Versioning.STAGGERED ->
                                    "当 Syncthing 替换或删除文件时，文件将移动到 .stversions 目录，文件名带有时间戳。超过最长保留时间，或超过一定份数，则会自动删除。"
                                versioning == NewFolderConfiguration.Versioning.EXTERNAL ->
                                    "有关受支持的模板命令行参数，请参阅外部版本控制帮助。注意：在 Android 环境下外部版本控制的能力受限。"
                                existingFolder?.versioningSupported == false ->
                                    "当前版本控制类型暂不支持编辑"
                                else -> null
                            },
                            items = versioningOptions.map { it.displayName },
                            selectedIndex = versioningOptions.indexOf(versioning),
                            enabled = !isSubmitting &&
                                !isReceiveEncrypted &&
                                existingFolder?.versioningSupported != false,
                            onSelectedIndexChange = { selectedIndex ->
                                versioning = versioningOptions[selectedIndex]
                            },
                        )

                        AnimatedVisibility(
                            visible = (versioning != NewFolderConfiguration.Versioning.NONE) && (versioning != NewFolderConfiguration.Versioning.EXTERNAL),
                            enter = expandVertically(
                                animationSpec = tween(durationMillis = 300)
                            ),
                            exit = shrinkVertically(
                                animationSpec = tween(durationMillis = 300)
                            ),
                        ) {
                            Column {
                                InputValueRow(
                                    label = "保留时长（天）",
                                    value = cleanoutDays,
                                    valueLabel = "永久",
                                    allowEdit = !isSubmitting,
                                    onValueChange = { cleanoutDays = it },
                                    valueValidator = {
                                        cleanoutDays.toIntWithDefaultForEmpty(0)?.let { it >= 0 } == true
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )

                                InputValueRow(
                                    label = "历史版本路径",
                                    value = versioningFsPath,
                                    valueLabel = ".stversions",
                                    allowEdit = !isSubmitting,
                                    onValueChange = { versioningFsPath = it },
                                )

                                // 简易版本控制
                                AnimatedVisibility(
                                    visible = versioning == NewFolderConfiguration.Versioning.SIMPLE,
                                    enter = expandVertically(
                                        animationSpec = tween(durationMillis = 300)
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = tween(durationMillis = 300)
                                    ),
                                ) {
                                    Column {
                                        InputValueRow(
                                            label = "保留版本数量",
                                            value = keepVersions,
                                            valueLabel = "5",
                                            allowEdit = !isSubmitting,
                                            onValueChange = { keepVersions = it },
                                            valueValidator = {
                                                keepVersions.toIntWithDefaultForEmpty(5)
                                                    ?.let{ it > 0 } == true
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        )
                                    }
                                }

                                InputValueRow(
                                    label = "定期清除间隔（秒）",
                                    value = cleanupIntervalSeconds,
                                    valueLabel = "3600",
                                    allowEdit = !isSubmitting,
                                    onValueChange = { cleanupIntervalSeconds = it },
                                    valueValidator = {
                                        cleanupIntervalSeconds.toIntWithDefaultForEmpty(3600)
                                            ?.let { it in 0..31_536_000 } == true
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = versioning == NewFolderConfiguration.Versioning.EXTERNAL,
                            enter = expandVertically(
                                animationSpec = tween(durationMillis = 300)
                            ),
                            exit = shrinkVertically(
                                animationSpec = tween(durationMillis = 300)
                            ),
                        ) {
                            InputValueRow(
                                label = "命令",
                                value = externalCommand,
                                valueLabel = "必填",
                                allowEdit = !isSubmitting,
                                onValueChange = { externalCommand = it },
                                valueValidator = { externalCommand.trim().isNotBlank() },
                            )
                        }
                    }
                )

                InfoSwitchCard(
                    title = "忽略模式",
                    content = {
                        if (isEditingFolder) {
                            ArrowPreference(
                                title = "编辑忽略文件",
                                enabled = !isReceiveEncrypted,
                                onClick = {
                                    ignoreEditorController.setDocument(acceptedIgnoreText)
                                    showEditorBottomSheet = true
                                }
                            )
                        } else {
                            InfoSwitch(
                                title = "添加忽略文件",
                                summary = "启用后，文件夹创建时将自动添加 .stignore",
                                checked = ignorePatternsEnabled,
                                enabled = !isSubmitting && !isReceiveEncrypted,
                                onCheckedChange = { ignorePatternsEnabled = it },
                            )
                        }

                        existingFolder?.ignoreError?.let { error ->
                            Text(
                                text = "读取 .stignore 时出错：$error",
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.main,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                )

                InfoSwitchCard(
                    title = "同步控制",
                    content = {

                        WindowDropdownPreference(
                            title = "文件变化检测",
                            items = listOf("监听并定期扫描", "定期扫描"),
                            selectedIndex = if (fsWatcherEnabled) 0 else 1,
                            enabled = !isSubmitting,
                            onSelectedIndexChange = { selectedIndex ->
                                fsWatcherEnabled = selectedIndex == 0
                            },
                        )

                        InputValueRow(
                            label = "重新扫描间隔（秒）",
                            value = rescanIntervalSeconds,
                            valueLabel = "3600",
                            allowEdit = !isSubmitting,
                            onValueChange = { rescanIntervalSeconds = it },
                            valueValidator = { rescanIntervalSeconds.toIntWithDefaultForEmpty(3600)?.let{ it >= 0 } == true },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        WindowDropdownPreference(
                            title = "同步方向",
                            summary = when {
                                isEditingFolder && isReceiveEncrypted ->
                                    "接收加密数据类型创建后不能更改"
                                folderType == NewFolderConfiguration.Type.SEND_ONLY ->
                                    "文件受到保护，不会在其他设备上进行更改，但在此设备上所做的更改将发送到集群的其他设备。"
                                folderType == NewFolderConfiguration.Type.RECEIVE_ONLY ->
                                    "文件从集群同步，但本地所做的任何更改都不会发送到其他设备。"
                                folderType == NewFolderConfiguration.Type.RECEIVE_ENCRYPTED ->
                                    "仅存储和同步加密数据。所有连接设备上的文件夹都需要使用相同的密码设置，或者也需要设置为“加密接收”类型。"
                                else -> null
                            },
                            items = folderTypeOptions.map { type -> folderTypeNames.getValue(type) },
                            selectedIndex = folderTypeOptions.indexOf(folderType),
                            enabled = !isSubmitting && !(isEditingFolder && isReceiveEncrypted),
                            onSelectedIndexChange = { selectedIndex ->
                                folderType = folderTypeOptions[selectedIndex]
                                if (folderType == NewFolderConfiguration.Type.RECEIVE_ENCRYPTED) {
                                    fsWatcherEnabled = false
                                    versioning = NewFolderConfiguration.Versioning.NONE
                                    ignorePatternsEnabled = false
                                }
                            },
                        )
                    }
                )

                if (isEditingFolder) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        text = "删除",
                        enabled = !isSubmitting,
                        onClick = {
                            deleteLocalFiles = false
                            showDeleteOverlay = true
                        },
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                    )
                }
            }

            OverlayBottomSheet(
                title = "编辑忽略文件",
                show = showEditorBottomSheet,
                allowDismiss = true,
                enableNestedScroll = false,
                defaultWindowInsetsPadding = false,
                insideMargin = DpSize.Zero,
                onDismissRequest = { showEditorBottomSheet = false },
                onDismissFinished = { showEditorBottomSheet = false },
                startAction = {
                    IconButton(
                        modifier = Modifier.padding(start = pagePaddingHorizontal),
                        onClick = {
                            ignoreEditorController.setDocument(acceptedIgnoreText)
                            showEditorBottomSheet = false
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "取消",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                endAction = {
                    IconButton(
                        modifier = Modifier.padding(end = pagePaddingHorizontal),
                        onClick = {
                            acceptedIgnoreText = ignoreEditorController.getText()
                            showEditorBottomSheet = false
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = "确定",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            ) {
                Column (
                    modifier = Modifier.padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row (
                        modifier = Modifier.padding(horizontal = pagePaddingHorizontal).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("请输入要忽略的内容，每行一条。")

                        if ( !showStIgnoreHelp ) Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { showStIgnoreHelp = true },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Help,
                                contentDescription = "确定",
                                tint = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                            )
                        }
                    }

                    if (showStIgnoreHelp) {
                        StIgnoreHelpItem("(?d)", "此前缀表示，如果文件阻止删除目录则文件可被删除")
                        StIgnoreHelpItem("(?i)", "此前缀表示，后面的模式在匹配时不区分大小写")
                        StIgnoreHelpItem(" !  ", "此前缀表示给定条件的反转（即不排除）")
                        StIgnoreHelpItem(" *  ", "单级通配符（仅匹配单层文件夹）")
                        StIgnoreHelpItem(" ** ", "多级通配符（用以匹配多层文件夹）")
                        StIgnoreHelpItem(" // ", "注释，在行首使用")
                        StIgnoreHelpItem("#include", "从指定文件加载忽略模式")
                        ArrowPreference(
                            title = "查看完整帮助",
                            onClick = { uriHandler.openUri("https://docs.syncthing.net/users/ignoring") }
                        )
                        TextButton(
                            text = "确定",
                            modifier = Modifier.padding(horizontal = pagePaddingHorizontal).fillMaxWidth(),
                            onClick = { showStIgnoreHelp = false }
                        )
                    } else {
                        Text(
                            text = ".stignore",
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier= Modifier
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.secondaryContainer)
                                .padding(4.dp)
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
                            readOnly = isSubmitting,
                            softWrap = true,
                            overscrollEnabled = false,
                            autoClosePairs = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        )
                    }
                }
            }

            OverlayDialog(
                show = showDeleteOverlay,
                title = "删除文件夹",
                onDismissRequest = { showDeleteOverlay = false },
                onDismissFinished = {
                    showDeleteOverlay = false
                    deleteLocalFiles = false
                },
            ) {
                Column (
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "确定要删除文件夹 “${folderId.toCharArray().joinToString("\u200B")}” 吗？",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !isSubmitting,
                                role = Role.Checkbox,
                                onClick = { deleteLocalFiles = !deleteLocalFiles },
                            ),
                    ) {
                        Checkbox(
                            state = ToggleableState(deleteLocalFiles),
                            enabled = !isSubmitting,
                            onClick = { deleteLocalFiles = !deleteLocalFiles },
                        )
                        Text(
                            "同时删除本地文件",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(end = 34.dp).fillMaxWidth(),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = "取消",
                            enabled = !isSubmitting,
                            onClick = { showDeleteOverlay = false },
                        )
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = "删除",
                            enabled = !isSubmitting,
                            onClick = {
                                val shouldDeleteLocalFiles = deleteLocalFiles
                                showDeleteOverlay = false
                                deleteLocalFiles = false
                                onDeleteFolder(folderId, shouldDeleteLocalFiles)
                            },
                            colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StIgnoreHelpItem(item: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Text(
            text = item,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(4.dp)
        )
        Text(text)
    }
}

private fun String.toIgnorePatternLines(): List<String> {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    return if (normalized.isEmpty()) emptyList() else normalized.split('\n')
}

@Composable
private fun AddFolderDevices(
    device: SyncthingDevice,
    isSubmitting: Boolean,
    selected: Boolean,
    remoteFolderState: RemoteFolderState?,
    encryptionPassword: String?,
    onSelectedChange: (Boolean) -> Unit,
    onEncryptionPasswordChange: (String) -> Unit,
) {
    Column {
        InfoSwitch (
            title = device.name?.takeIf(String::isNotBlank) ?: "未命名设备",
            summary = if (device.id == device.name) null else device.id,
            checked = selected,
            enabled = !isSubmitting,
            statusColor = when (remoteFolderState) {
                RemoteFolderState.VALID -> StatusColor.OK.color
                RemoteFolderState.NOT_SHARING -> StatusColor.PENDING.color
                RemoteFolderState.PAUSED -> StatusColor.PAUSED.color
                RemoteFolderState.UNKNOWN -> StatusColor.FAIL.color
                null -> StatusColor.DOWN.color
            },
            onCheckedChange = onSelectedChange,
        )
        encryptionPassword?.let {
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 300)
                ),
            ) {
                InputValueRow(
                    label = "共享密码",
                    value = it,
                    onValueChange = onEncryptionPasswordChange,
                    valueValidator = {
                        !(device.untrusted && encryptionPassword.isBlank())
                    },
                    valueLabel = "无密码",
                    allowEdit = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
    }
}

private fun SyncthingFolder.statusName(): String = when {
    paused -> "已暂停"
    pullErrors > 0 -> "存在错误"
    state == "idle" && needFiles == 0L -> "已同步"
    state == "scanning" -> "正在扫描"
    state == "scan-wait" -> "等待扫描"
    state == "sync-wait" -> "等待同步"
    state == "sync-preparing" -> "准备同步"
    state == "syncing" -> "正在同步"
    state == "clean-wait" -> "等待清理"
    state == "cleaning" -> "正在清理"
    state == "error" -> "状态异常"
    needFiles > 0 -> "需要同步"
    else -> state.ifBlank { "未知" }
}

@Composable
private fun SyncthingFolder.statusColor(): Color = when {
    paused -> StatusColor.DOWN.color
    pullErrors > 0 || state == "error" -> StatusColor.FAIL.color
    state == "idle" && needFiles == 0L -> StatusColor.OK.color
    else -> StatusColor.PENDING.color
}

private fun SyncthingFolder.typeName(): String = when (type) {
    "sendreceive" -> "发送与接收"
    "sendonly" -> "仅发送"
    "receiveonly" -> "仅接收"
    "receiveencrypted" -> "接收加密数据"
    else -> type.ifBlank { "未知" }
}

private fun String.toIntWithDefaultForEmpty( default: Int ): Int? {
    return if ( isBlank() ) default else toIntOrNull()
}
