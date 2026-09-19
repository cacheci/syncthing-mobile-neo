package moe.https.syncthing.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.https.syncthing.storage.AppSettingPrivateStorage
import moe.https.syncthing.ui.util.SettingProtocolStack
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.milliseconds

@RequiresApi(Build.VERSION_CODES.R)
class CoreRuntime(
    context: Context,
    private val coreRegistry: CoreRegistry,
    private val appSettingsStorage: AppSettingPrivateStorage,
) : DevicesController, FoldersController, RecentChangesController, SettingController {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val processMutex = Mutex()
    private val homeDirectory = File(applicationContext.filesDir, SYNCTHING_HOME_DIRECTORY)
    private val configFile = SyncthingConfigFile(File(homeDirectory, CONFIG_FILE_NAME))
    @Volatile
    private var managedGuiCredentials = loadOrCreateGuiCredentials()
    @Volatile
    private var managedGuiAuthenticationEnabled = loadGuiAuthenticationEnabled()
    @Volatile
    private var activeGuiHost = loadProtocolStack().guiListenAddress
    @Volatile
    private var activeGuiPort = initialGuiPort()
    @Volatile
    private var activeGuiUseTls = configuredGuiUseTls()
    @Volatile
    private var activeGuiCertificate: ByteArray? = null
    private val restClient = SyncthingRestClient(
        apiKey = loadOrCreateApiKey(),
        baseUrl = { formatGuiBaseUrl(activeGuiHost, activeGuiPort, activeGuiUseTls) },
        openConnection = ::openGuiConnection,
        onHttpError = { error ->
            logError(
                "REST request failed: ${error.message}",
                error,
            )
        },
    )

    private val mutableSnapshot = MutableStateFlow(idleSnapshot())
    val snapshot: StateFlow<CoreSnapshot> = mutableSnapshot.asStateFlow()

    private fun idleSnapshot(
        state: CoreState? = null,
        operationMessage: String? = null,
    ): CoreSnapshot {
        val options = coreRegistry.availableOptions()
        val selected = coreRegistry.selectedOption()
        val effectiveState = state ?: if (selected.availability == CoreAvailability.AVAILABLE) {
            CoreState.STOPPED
        } else {
            CoreState.NOT_INSTALLED
        }
        return CoreSnapshot(
            state = effectiveState,
            version = selected.version,
            deviceName = configuredDeviceName(),
            operationMessage = operationMessage,
            selectedCoreId = selected.id,
            selectedCoreSource = selected.source,
            availableCores = options,
            canSelectCore = !SyncthingCoreService.isDesiredRunning(applicationContext) &&
                effectiveState in setOf(
                    CoreState.NOT_INSTALLED,
                    CoreState.STOPPED,
                    CoreState.FAILED,
                ),
        )
    }

    suspend fun selectCore(id: String) = withContext(Dispatchers.IO) {
        processMutex.withLock {
            if (
                SyncthingCoreService.isDesiredRunning(applicationContext) ||
                process?.isAlive == true ||
                currentPid() != null ||
                restClient.ping()
            ) {
                mutableSnapshot.update {
                    it.copy(operationMessage = "请先停止核心，再切换核心")
                }
                return@withLock
            }
            runCatching { coreRegistry.select(id) }
                .onSuccess {
                    mutableSnapshot.value = idleSnapshot()
                }
                .onFailure { error ->
                    mutableSnapshot.update {
                        it.copy(operationMessage = error.userMessage())
                    }
                }
        }
    }

    suspend fun deleteCore(id: String) = withContext(Dispatchers.IO) {
        processMutex.withLock {
            if (
                SyncthingCoreService.isDesiredRunning(applicationContext) ||
                process?.isAlive == true ||
                currentPid() != null ||
                restClient.ping()
            ) {
                mutableSnapshot.update {
                    it.copy(operationMessage = "请先停止核心，再删除外置核心")
                }
                return@withLock
            }
            runCatching { coreRegistry.deleteExternal(id) }
                .onSuccess { core ->
                    mutableSnapshot.value = idleSnapshot(
                        operationMessage = "已删除外置核心 ${core.version}",
                    )
                }
                .onFailure { error ->
                    mutableSnapshot.update {
                        it.copy(operationMessage = error.userMessage())
                    }
                }
        }
    }

    fun guiUrl(): String = formatGuiBaseUrl(activeGuiHost, activeGuiPort, activeGuiUseTls)

    fun isGuiCertificateTrusted(encodedCertificate: ByteArray): Boolean = runCatching {
        MessageDigest.isEqual(activeGuiCertificateBytes(), encodedCertificate)
    }.getOrDefault(false)

    fun guiCredentials(): Pair<String, String>? =
        if (managedGuiAuthenticationEnabled) {
            managedGuiCredentials.username to managedGuiCredentials.password
        } else {
            null
        }

    override suspend fun loadDevices(): DevicesSnapshot = withContext(Dispatchers.IO) {
        val status = restClient.status()
        rememberLocalDeviceId(status.myId)
        val connections = restClient.connections()
        val discoveryCache = restClient.discoveryCache()
        val devices = restClient.configuredDevices().map { device ->
            val connection = connections[device.id]
            SyncthingDevice(
                id = device.id,
                name = device.name,
                addresses = device.addresses,
                connected = device.id == status.myId || connection?.connected == true,
                connectionAddress = connection?.address,
                clientVersion = connection?.clientVersion,
                lastConnectionAt = connection?.lastConnectionAt,
                paused = device.paused,
                isLocal = device.id == status.myId ||
                    (status.myId == null && device.name == "localhost"),
                discoveredAddresses = discoveryCache[device.id].orEmpty(),
                group = device.group,
                introducer = device.introducer,
                autoAcceptFolders = device.autoAcceptFolders,
                compression = device.compression,
                numConnections = device.numConnections,
                maxSendKiBPerSecond = device.maxSendKiBPerSecond,
                maxReceiveKiBPerSecond = device.maxReceiveKiBPerSecond,
                untrusted = device.untrusted,
            )
        }
        DevicesSnapshot(
            devices = devices,
            pendingDevices = restClient.pendingDevices(),
            localInfo = SyncthingLocalInfo(
                discoveryEnabled = status.discoveryEnabled,
                discoveryStatus = status.discoveryStatus.map { discoveryStatus ->
                    SyncthingDiscoveryStatus(
                        method = discoveryStatus.method,
                        error = discoveryStatus.error,
                    )
                },
                listenAddresses = status.listenAddresses.map { listenAddress ->
                    SyncthingListenAddress(
                        address = listenAddress.address,
                        error = listenAddress.error,
                    )
                },
            ),
        )
    }

    override suspend fun loadPendingDevices(): List<SyncthingPendingDevice> = withContext(Dispatchers.IO) {
        restClient.pendingDevices()
    }

    override suspend fun addDevice(
        configuration: NewDeviceConfiguration,
    ) = withContext(Dispatchers.IO) {
        restClient.addDevice(configuration)
    }

    override suspend fun updateDevice(configuration: NewDeviceConfiguration) = withContext(Dispatchers.IO) {
        restClient.updateDevice(configuration)
    }

    override suspend fun deleteDevice(deviceId: String) = withContext(Dispatchers.IO) {
        restClient.deleteDevice(deviceId)
    }

    override suspend fun setDevicePaused(
        deviceId: String,
        paused: Boolean,
    ) = withContext(Dispatchers.IO) {
        restClient.setDevicePaused(deviceId, paused)
    }

    override suspend fun dismissPendingDevice(deviceId: String) = withContext(Dispatchers.IO) {
        restClient.dismissPendingDevice(deviceId)
    }

    override suspend fun ignorePendingDevice(device: SyncthingPendingDevice) = withContext(Dispatchers.IO) {
        restClient.ignorePendingDevice(device)
    }

    override suspend fun loadFolders(): FoldersSnapshot = withContext(Dispatchers.IO) {
        FoldersSnapshot(
            folders = restClient.configuredFolders().map { folder ->
                val status = restClient.folderStatus(folder.id)
                val ignores = restClient.folderIgnores(folder.id)
                SyncthingFolder(
                    id = folder.id,
                    label = folder.label,
                    group = folder.group,
                    path = folder.path,
                    type = folder.type,
                    paused = folder.paused,
                    fsWatcherEnabled = folder.fsWatcherEnabled,
                    rescanIntervalSeconds = folder.rescanIntervalSeconds,
                    pullOrder = folder.pullOrder,
                    blockIndexing = folder.blockIndexing,
                    versioning = folder.versioning.type,
                    versioningSupported = folder.versioning.supported,
                    versioningFsPath = folder.versioning.fsPath,
                    versioningCleanoutDays = folder.versioning.cleanoutDays,
                    versioningKeep = folder.versioning.keep,
                    versioningCleanupIntervalSeconds = folder.versioning.cleanupIntervalSeconds,
                    versioningExternalCommand = folder.versioning.externalCommand,
                    ignorePatterns = ignores.patterns,
                    ignoreError = ignores.error,
                    devices = folder.devices.map { device ->
                        device.copy(
                            remoteFolderState = runCatching {
                                restClient.remoteFolderState(folder.id, device.deviceId)
                            }.getOrDefault(RemoteFolderState.UNKNOWN),
                        )
                    },
                    state = status.state,
                    localFiles = status.localFiles,
                    localBytes = status.localBytes,
                    needFiles = status.needFiles,
                    needBytes = status.needBytes,
                    pullErrors = status.pullErrors,
                )
            },
            pendingFolders = restClient.pendingFolders(),
        )
    }

    override suspend fun loadPendingFolders(): List<SyncthingPendingFolder> =
        withContext(Dispatchers.IO) {
            restClient.pendingFolders()
        }

    override suspend fun loadRecentChanges(): List<SyncthingRecentChange> =
        withContext(Dispatchers.IO) {
            restClient.recentChanges().map { change ->
                SyncthingRecentChange(
                    id = change.id,
                    time = change.time,
                    source = change.source,
                    action = change.action,
                    itemType = change.itemType,
                    folderId = change.folderId,
                    folderLabel = change.folderLabel,
                    path = change.path,
                    modifiedBy = change.modifiedBy,
                )
            }
        }

    override suspend fun addFolder(
        configuration: NewFolderConfiguration,
    ) = withContext(Dispatchers.IO) {
        restClient.addFolder(configuration)
        if (configuration.updateIgnorePatterns) {
            restClient.updateFolderIgnores(configuration.folderId, configuration.ignorePatterns)
        }
    }

    override suspend fun updateFolder(
        configuration: NewFolderConfiguration,
    ) = withContext(Dispatchers.IO) {
        restClient.updateFolder(configuration)
        if (configuration.updateIgnorePatterns) {
            restClient.updateFolderIgnores(configuration.folderId, configuration.ignorePatterns)
        }
    }

    override suspend fun deleteFolder(
        folderId: String,
        deleteLocalFiles: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (!deleteLocalFiles) {
            restClient.deleteFolder(folderId)
            return@withContext
        }

        val configuredFolders = restClient.configuredFolders()
        val folder = configuredFolders.firstOrNull { it.id == folderId }
            ?: throw IOException("找不到要删除的文件夹配置：$folderId")
        val directory = resolveFolderDirectory(folder.path)
        validateFolderDeletionTarget(
            directory = directory,
            otherFolderPaths = configuredFolders
                .filterNot { it.id == folderId }
                .map { it.path },
        )

        restClient.deleteFolder(folderId)
        try {
            deleteDirectoryWithoutFollowingLinks(directory)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw IOException(
                "已移除 Syncthing 文件夹配置，但删除本地文件失败：${error.message ?: directory.path}",
                error,
            )
        }
    }

    override suspend fun setFolderPaused(
        folderId: String,
        paused: Boolean,
    ) = withContext(Dispatchers.IO) {
        restClient.setFolderPaused(folderId, paused)
    }

    override suspend fun dismissPendingFolder(
        folder: SyncthingPendingFolder,
    ) = withContext(Dispatchers.IO) {
        restClient.dismissPendingFolder(folder)
    }

    override suspend fun ignorePendingFolder(
        folder: SyncthingPendingFolder,
    ) = withContext(Dispatchers.IO) {
        restClient.ignorePendingFolder(folder)
    }

    override suspend fun loadSetting(): SettingSnapshot = withContext(Dispatchers.IO) {
        val portConflictBehavior = loadGuiPortConflictBehavior()
        val snapshot = when {
            restClient.ping() -> {
                val localDeviceId = requireLocalDeviceId()
                SettingSnapshot(
                    configuration = restClient.setting(portConflictBehavior, localDeviceId),
                    accessMode = SettingAccessMode.REST,
                )
            }
            process?.isAlive == true || currentPid() != null -> {
                throw IOException("Syncthing 核心进程仍在运行，REST 接口就绪后才能修改设置")
            }
            configFile.exists -> SettingSnapshot(
                configuration = configFile.read(portConflictBehavior, rememberedLocalDeviceId()),
                accessMode = SettingAccessMode.CONFIG_FILE,
            )
            else -> SettingSnapshot(
                configuration = startupSetting(portConflictBehavior),
                accessMode = SettingAccessMode.STARTUP_ONLY,
            )
        }
        snapshot.copy(
            configuration = snapshot.configuration.copy(
                guiListenAddress = loadProtocolStack().guiListenAddress,
                guiAuthenticationEnabled = managedGuiAuthenticationEnabled,
                guiUser = managedGuiCredentials.username,
                guiPasswordConfigured = true,
                newGuiPassword = "",
            ),
        )
    }

    override suspend fun pingDiscoveryServer(address: String): Long = withContext(Dispatchers.IO) {
        val url = URL(address.trim())
        val host = url.host.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Discovery 地址缺少主机名")
        val port = when {
            url.port > 0 -> url.port
            url.defaultPort > 0 -> url.defaultPort
            else -> throw IllegalArgumentException("Discovery 地址缺少有效端口")
        }
        Socket().use { socket ->
            val startedAt = SystemClock.elapsedRealtimeNanos()
            socket.connect(InetSocketAddress(host, port), DISCOVERY_PING_TIMEOUT_MILLIS)
            val elapsedNanos = SystemClock.elapsedRealtimeNanos() - startedAt
            (elapsedNanos + NANOSECONDS_PER_MILLISECOND - 1) / NANOSECONDS_PER_MILLISECOND
        }
    }

    override suspend fun saveSetting(
        configuration: SettingConfiguration,
        guiTlsFiles: Map<GuiTlsFile, ByteArray>,
    ): SettingSaveResult = withContext(Dispatchers.IO) {
        processMutex.withLock {
            guiTlsFiles.forEach { (type, content) -> validateGuiTlsFile(type, content) }
            val desiredGuiAuthenticationEnabled = configuration.guiAuthenticationEnabled
            val desiredGuiCredentials = ManagedGuiCredentials(
                username = configuration.guiUser.trim().ifBlank { managedGuiCredentials.username },
                password = configuration.newGuiPassword.takeIf(String::isNotBlank)
                    ?: managedGuiCredentials.password,
            )
            val effectiveConfiguration = configuration.copy(
                guiListenAddress = loadProtocolStack().guiListenAddress,
                guiAuthenticationEnabled = desiredGuiAuthenticationEnabled,
                guiUser = desiredGuiCredentials.username,
                guiPasswordConfigured = desiredGuiAuthenticationEnabled,
                newGuiPassword = "",
            )
            val previousGuiPort = activeGuiPort
            val previousGuiUseTls = activeGuiUseTls
            val previousPortConflictBehavior = loadGuiPortConflictBehavior()
            val savedResult = when {
                restClient.ping() -> {
                    val localDeviceId = requireLocalDeviceId()
                    restClient.updateSetting(
                        configuration = effectiveConfiguration,
                        localDeviceId = localDeviceId,
                        managedGuiPassword = desiredGuiCredentials.password,
                    )
                }
                process?.isAlive == true || currentPid() != null -> {
                    throw IOException("Syncthing 核心进程仍在运行，不能同时写入配置文件")
                }
                configFile.exists -> {
                    configFile.write(effectiveConfiguration, rememberedLocalDeviceId())
                    configFile.ensureGuiAuthentication(
                        enabled = desiredGuiAuthenticationEnabled,
                        username = desiredGuiCredentials.username,
                        password = desiredGuiCredentials.password,
                    )
                    SettingSaveResult(
                        restartRequired = true,
                        accessMode = SettingAccessMode.CONFIG_FILE,
                    )
                }
                else -> SettingSaveResult(
                    restartRequired = true,
                    accessMode = SettingAccessMode.STARTUP_ONLY,
                )
            }
            saveGuiAuthentication(
                enabled = desiredGuiAuthenticationEnabled,
                credentials = desiredGuiCredentials,
            )
            val result = savedResult.copy(
                restartRequired = savedResult.restartRequired ||
                    effectiveConfiguration.guiPortConflictBehavior != previousPortConflictBehavior ||
                    guiTlsFiles.isNotEmpty(),
            )
            saveStartupSetting(effectiveConfiguration)
            if (
                result.accessMode == SettingAccessMode.REST &&
                (
                    effectiveConfiguration.guiPort != previousGuiPort ||
                        effectiveConfiguration.guiUseTls != previousGuiUseTls
                )
            ) {
                activeGuiPort = effectiveConfiguration.guiPort
                activeGuiUseTls = effectiveConfiguration.guiUseTls
                if (restClient.ping()) {
                    preferences.edit { putInt(KEY_ACTIVE_GUI_PORT, activeGuiPort) }
                } else {
                    activeGuiPort = previousGuiPort
                    activeGuiUseTls = previousGuiUseTls
                }
            } else {
                activeGuiPort = previousGuiPort
                activeGuiUseTls = previousGuiUseTls
            }
            replaceGuiTlsFiles(guiTlsFiles)
            val guiTlsRestartNeeded = result.accessMode == SettingAccessMode.REST &&
                (guiTlsFiles.isNotEmpty() || savedResult.guiTlsChanged)
            val restartInitiated = guiTlsRestartNeeded &&
                SyncthingCoreService.requestRestart(applicationContext)
            mutableSnapshot.update { it.copy(deviceName = effectiveConfiguration.deviceName) }
            if (restartInitiated) {
                result.copy(restartRequired = false, restartInitiated = true)
            } else {
                result
            }
        }
    }

    private fun replaceGuiTlsFiles(files: Map<GuiTlsFile, ByteArray>) {
        if (files.isEmpty()) return
        homeDirectory.mkdirs()
        val preparedFiles = mutableListOf<Pair<File, File>>()
        try {
            files.forEach { (type, content) ->
                val target = File(homeDirectory, type.fileName)
                val temporary = File.createTempFile("${type.fileName}.", ".tmp", homeDirectory)
                preparedFiles += temporary to target
                temporary.outputStream().use { output ->
                    output.write(content)
                    output.flush()
                    (output as? java.io.FileOutputStream)?.fd?.sync()
                }
                temporary.setReadable(false, false)
                temporary.setReadable(true, true)
                temporary.setWritable(false, false)
                temporary.setWritable(true, true)
            }
            preparedFiles.forEach { (temporary, target) ->
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }
        } finally {
            preparedFiles.forEach { (temporary, _) -> temporary.delete() }
        }
    }

    @Volatile
    private var process: Process? = null

    suspend fun refreshInstallation() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true || currentPid() != null || restClient.ping()) {
            val status = runCatching { restClient.status() }
                .onFailure { error ->
                    logConnectionFailure(
                        context = "Failed to refresh core runtime status",
                        error = error,
                        terminal = false,
                    )
                }
                .getOrNull()
            rememberLocalDeviceId(status?.myId)
            val transferTotals = runCatching { restClient.connectionTotals() }.getOrNull()
            mutableSnapshot.value = idleSnapshot(state = CoreState.RUNNING).copy(
                deviceName = status?.myId?.let { deviceId ->
                    runCatching { restClient.deviceName(deviceId) }.getOrNull()
                } ?: configuredDeviceName(),
                uptimeSeconds = status?.uptimeSeconds,
                downloadBytesPerSecond = transferTotals?.receivedBytes?.let { 0L },
                uploadBytesPerSecond = transferTotals?.sentBytes?.let { 0L },
                downloadedBytes = transferTotals?.receivedBytes,
                uploadedBytes = transferTotals?.sentBytes,
                totalFileSizeBytes = runCatching { restClient.totalFileSizeBytes() }.getOrNull(),
                rssBytes = readRssBytes(currentPid()),
                allocatedBytes = status?.allocatedBytes,
                systemBytes = status?.systemBytes,
                goroutines = status?.goroutines,
            )
            return@withContext
        }
        mutableSnapshot.value = idleSnapshot()
    }

    suspend fun importCore(uri: Uri) = withContext(Dispatchers.IO) {
        processMutex.withLock {
            if (
                SyncthingCoreService.isDesiredRunning(applicationContext) ||
                process?.isAlive == true ||
                currentPid() != null ||
                restClient.ping()
            ) {
                mutableSnapshot.update {
                    it.copy(operationMessage = "请先停止核心，再导入外置核心")
                }
                return@withLock
            }
            if (!supportsCoreAbi()) {
                mutableSnapshot.update {
                    it.copy(operationMessage = "当前设备没有受支持的核心架构，无法使用外置核心")
                }
                return@withLock
            }

            mutableSnapshot.update {
                it.copy(
                    state = CoreState.INSTALLING,
                    lastError = null,
                    operationMessage = null,
                    canSelectCore = false,
                )
            }
            runCatching { coreRegistry.importAndSelect(uri) }
                .onSuccess { core ->
                    mutableSnapshot.value = idleSnapshot(
                        operationMessage = "已导入并选中外置核心 ${core.version}",
                    )
                    if (core.version.contains("linux-", ignoreCase = true)) {
                        logWarning(
                            "Imported a Linux core; DNS, network interfaces, discovery, and relay features may not work correctly on Android. Prefer an Android core built for the matching architecture",
                        )
                    } else {
                        logInfo("External core imported successfully: ${redact(core.version)}")
                    }
                }
                .onFailure { error ->
                    logError("Failed to import external core", error)
                    mutableSnapshot.value = idleSnapshot(
                        operationMessage = error.userMessage(),
                    )
                }
        }
    }

    suspend fun runSession(): SessionResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (!supportsCoreAbi()) {
            fail(
                message = "当前设备没有受支持的核心架构",
                logMessage = "The current device has no supported core architecture",
            )
            return@withContext SessionResult(started = false, runtimeMillis = 0, exitCode = null)
        }

        if (restClient.ping()) {
            restClient.ensureGuiAuthentication(
                enabled = managedGuiAuthenticationEnabled,
                username = managedGuiCredentials.username,
                password = managedGuiCredentials.password,
            )
            mutableSnapshot.update {
                it.copy(
                    state = CoreState.RUNNING,
                    lastError = null,
                    operationMessage = null,
                    canSelectCore = false,
                )
            }
            monitorSession(process = null)
            return@withContext SessionResult(
                started = true,
                runtimeMillis = System.currentTimeMillis() - startedAt,
                exitCode = null,
            )
        }

        val (launchedProcess, executable) = processMutex.withLock {
            process?.takeIf { it.isAlive }?.let { running ->
                val selected = coreRegistry.resolveSelected()
                running to selected
            } ?: coreRegistry.resolveSelected().let { selected ->
                val launched = launchProcess(selected)
                process = launched
                rememberProcess(selected)
                launched to selected
            }
        }
        executable.version
            .takeIf { executable.source == CoreSource.EXTERNAL && it.contains("linux-arm64", ignoreCase = true) }
            ?.let {
                logWarning(
                    "The current external core is a linux-arm64 build; DNS, network interfaces, discovery, and relay connections may be unavailable on Android",
                )
            }
        logInfo("Core process created; connecting to REST API: ${restApiAddress()}")

        mutableSnapshot.update {
            it.copy(
                state = CoreState.STARTING,
                lastError = null,
                operationMessage = null,
                canSelectCore = false,
                deviceName = configuredDeviceName(),
                uptimeSeconds = null,
                downloadBytesPerSecond = null,
                uploadBytesPerSecond = null,
                downloadedBytes = null,
                uploadedBytes = null,
                totalFileSizeBytes = null,
                rssBytes = null,
                allocatedBytes = null,
                systemBytes = null,
                goroutines = null,
            )
        }

        val apiWaitResult = waitForApi(launchedProcess)
        if (!apiWaitResult.ready) {
            val exitCode = launchedProcess.exitCodeOrNull()
            val message = if (exitCode == null) {
                    "核心已启动，但 REST 接口未在规定时间内就绪" +
                        apiWaitResult.lastError?.let { "；最后错误：${connectionErrorSummary(it)}" }.orEmpty()
                } else {
                    "核心启动失败，退出码 $exitCode"
                }
            fail(
                message = message,
                logMessage = if (exitCode == null) {
                    "Core started, but the REST API did not become ready within the timeout" +
                        apiWaitResult.lastError?.let { "; last error: ${connectionErrorLogSummary(it)}" }.orEmpty()
                } else {
                    "Core failed to start with exit code $exitCode"
                },
                includeCoreLogs = true,
            )
            launchedProcess.destroyForcibly()
            process = null
            clearProcessRecord()
            return@withContext SessionResult(
                started = false,
                runtimeMillis = System.currentTimeMillis() - startedAt,
                exitCode = exitCode,
            )
        }

        mutableSnapshot.update { it.copy(state = CoreState.RUNNING, lastError = null) }
        logInfo("Core REST API is ready: ${restApiAddress()}")
        currentPid()
        monitorSession(launchedProcess)
        val exitCode = launchedProcess.exitCodeOrNull()
        process = null
        clearProcessRecord()

        if (mutableSnapshot.value.state != CoreState.STOPPING) {
            fail(
                message = "核心意外退出${exitCode?.let { code -> "，退出码 $code" } ?: ""}",
                logMessage = "Core exited unexpectedly${exitCode?.let { code -> " with exit code $code" } ?: ""}",
                includeCoreLogs = true,
            )
        }

        SessionResult(
            started = true,
            runtimeMillis = System.currentTimeMillis() - startedAt,
            exitCode = exitCode,
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        mutableSnapshot.update { it.copy(state = CoreState.STOPPING, lastError = null) }
        logInfo("Requesting core shutdown")
        runCatching { restClient.shutdown() }
            .onFailure { error ->
                logConnectionFailure(
                    context = "Graceful REST shutdown request failed; attempting to terminate the process",
                    error = error,
                    terminal = false,
                )
            }

        val currentProcess = process
        if (currentProcess != null) {
            if (!currentProcess.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                currentProcess.destroy()
            }
            if (currentProcess.isAlive && !currentProcess.waitFor(FORCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                currentProcess.destroyForcibly()
            }
        } else {
            for (attempt in 0 until STOP_POLL_COUNT) {
                if (!restClient.ping()) break
                delay(STOP_POLL_INTERVAL_MILLIS.milliseconds)
            }
            if (restClient.ping()) {
                killRememberedProcessIfOwned()
            }
        }

        process = null
        clearProcessRecord()
        mutableSnapshot.value = idleSnapshot()
        logInfo("Core stopped")
    }

    fun fail(
        message: String,
        logMessage: String = message,
        error: Throwable? = null,
        includeCoreLogs: Boolean = false,
    ) {
        logError(logMessage, error)
        if (includeCoreLogs) {
            logCoreLogTail()
        }
        mutableSnapshot.update {
            it.copy(
                state = CoreState.FAILED,
                deviceName = configuredDeviceName(),
                uptimeSeconds = null,
                downloadBytesPerSecond = null,
                uploadBytesPerSecond = null,
                downloadedBytes = null,
                uploadedBytes = null,
                totalFileSizeBytes = null,
                rssBytes = null,
                allocatedBytes = null,
                systemBytes = null,
                goroutines = null,
                lastError = message,
                canSelectCore = !SyncthingCoreService.isDesiredRunning(applicationContext),
            )
        }
    }

    private fun launchProcess(executable: CoreExecutable): Process {
        val home = homeDirectory.apply { mkdirs() }
        val logs = File(applicationContext.filesDir, "logs").apply { mkdirs() }
        val apiKey = preferences.getString(KEY_API_KEY, null)
            ?: throw IOException("REST API 密钥不存在")
        val portConflictBehavior = loadGuiPortConflictBehavior()
        val configuredGuiAddress = formatGuiAddress(
            loadProtocolStack().guiListenAddress,
            configuredGuiPort(portConflictBehavior),
        )
        val configuredGuiUseTls = configuredGuiUseTls()
        ensureManagedGuiAuthentication(
            executable,
            configuredGuiAddress,
            configuredGuiUseTls,
            logs,
        )
        val guiAddress = resolveLaunchGuiAddress(configuredGuiAddress, portConflictBehavior)
        activeGuiHost = parseGuiHost(guiAddress)
        activeGuiPort = parseGuiPort(guiAddress)
        activeGuiUseTls = configuredGuiUseTls
        activeGuiCertificate = null
        preferences.edit { putInt(KEY_ACTIVE_GUI_PORT, activeGuiPort) }

        val arguments = mutableListOf(
            executable.file.absolutePath,
            "serve",
            "--home=${home.absolutePath}",
            "--gui-address=$guiAddress",
            "--gui-apikey=$apiKey",
            "--no-browser",
            "--no-restart",
            "--no-upgrade",
            "--log-file=${File(logs, "syncthing.log").absolutePath}",
            "--log-max-size=1048576",
            "--log-max-old-files=1",
        )
        if (portConflictBehavior == SettingConfiguration.GuiPortConflictBehavior.FAIL) {
            arguments += "--no-port-probing"
        }

        return ProcessBuilder(arguments).apply {
            environment()["HOME"] = applicationContext.filesDir.absolutePath
            environment()["STNOUPGRADE"] = "1"
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.to(File(logs, "launcher.log")))
        }.start()
    }

    private fun ensureManagedGuiAuthentication(
        executable: CoreExecutable,
        configuredGuiAddress: String,
        guiUseTls: Boolean,
        logs: File,
    ) {
        if (!configFile.exists) {
            val arguments = listOf(
                executable.file.absolutePath,
                "generate",
                "--home=${homeDirectory.absolutePath}",
                "--gui-user=${managedGuiCredentials.username}",
                "--gui-password=-",
                "--no-port-probing",
            )
            val generateProcess = ProcessBuilder(arguments).apply {
                environment()["HOME"] = applicationContext.filesDir.absolutePath
                redirectErrorStream(true)
                redirectOutput(ProcessBuilder.Redirect.to(File(logs, "generate.log")))
            }.start()
            generateProcess.outputStream.bufferedWriter().use { writer ->
                writer.write(managedGuiCredentials.password)
                writer.newLine()
            }
            if (!generateProcess.waitFor(GENERATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                generateProcess.destroyForcibly()
                throw IOException("生成 Syncthing 初始配置超时")
            }
            val exitCode = generateProcess.exitValue()
            if (exitCode != 0 || !configFile.exists) {
                throw IOException("生成 Syncthing 初始配置失败，退出码 $exitCode")
            }
        }
        configFile.ensureGuiAuthentication(
            enabled = managedGuiAuthenticationEnabled,
            username = managedGuiCredentials.username,
            password = managedGuiCredentials.password,
            guiAddress = configuredGuiAddress,
        )
        configFile.ensureGuiUseTls(guiUseTls)
    }

    private suspend fun waitForApi(currentProcess: Process): ApiWaitResult {
        val startedAt = SystemClock.elapsedRealtime()
        var lastError: Throwable? = null
        var lastSignature: String? = null
        repeat(API_READY_POLL_COUNT) { index ->
            val attempt = index + 1
            if (!currentProcess.isAlive) {
                logError("Core process exited before the REST API became ready after $attempt attempts")
                return ApiWaitResult(ready = false, lastError = lastError)
            }

            val result = runCatching { restClient.pingChecked() }
            if (result.isSuccess) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                logInfo("Connected to REST API after $attempt attempts in ${elapsed}ms")
                return ApiWaitResult(ready = true, lastError = null)
            }

            val error = result.exceptionOrNull() ?: IOException("Unknown REST connection error")
            lastError = error
            val signature = connectionErrorSignature(error)
            if (attempt == 1 || attempt % CONNECTION_RETRY_LOG_INTERVAL == 0 || signature != lastSignature) {
                logConnectionFailure(
                    context = "REST API is not ready (attempt $attempt/$API_READY_POLL_COUNT)",
                    error = error,
                    terminal = false,
                )
            }
            lastSignature = signature
            delay(API_READY_POLL_INTERVAL_MILLIS.milliseconds)
        }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        logConnectionFailure(
            context = "REST API connection timed out after $API_READY_POLL_COUNT attempts in ${elapsed}ms",
            error = lastError ?: IOException("No REST response received"),
            terminal = true,
        )
        return ApiWaitResult(ready = false, lastError = lastError)
    }

    private suspend fun monitorSession(process: Process?) {
        var consecutiveFailures = 0
        var lastSignature: String? = null
        var previousTransferSample: TransferSample? = null
        while (currentCoroutineContext().isActive) {
            if (process != null && !process.isAlive) break

            runCatching { restClient.status() }
                .onSuccess { status ->
                    rememberLocalDeviceId(status.myId)
                    val deviceName = status.myId?.let { deviceId ->
                        runCatching { restClient.deviceName(deviceId) }.getOrNull()
                    }
                    val transferTotals = runCatching { restClient.connectionTotals() }.getOrNull()
                    val currentTransferSample = transferTotals?.let {
                        TransferSample(
                            receivedBytes = it.receivedBytes,
                            sentBytes = it.sentBytes,
                            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                        )
                    }
                    val downloadBytesPerSecond = transferRate(
                        previous = previousTransferSample?.receivedBytes,
                        current = currentTransferSample?.receivedBytes,
                        elapsedMillis = currentTransferSample?.elapsedRealtimeMillis?.minus(
                            previousTransferSample?.elapsedRealtimeMillis ?: 0L,
                        ),
                    )
                    val uploadBytesPerSecond = transferRate(
                        previous = previousTransferSample?.sentBytes,
                        current = currentTransferSample?.sentBytes,
                        elapsedMillis = currentTransferSample?.elapsedRealtimeMillis?.minus(
                            previousTransferSample?.elapsedRealtimeMillis ?: 0L,
                        ),
                    )
                    if (currentTransferSample != null) {
                        previousTransferSample = currentTransferSample
                    }
                    val totalFileSizeBytes = runCatching {
                        restClient.totalFileSizeBytes()
                    }.getOrNull()
                    if (consecutiveFailures > 0) {
                        logInfo("REST status connection recovered after $consecutiveFailures consecutive failures")
                    }
                    consecutiveFailures = 0
                    lastSignature = null
                    mutableSnapshot.update {
                        it.copy(
                            state = CoreState.RUNNING,
                            deviceName = deviceName ?: configuredDeviceName(),
                            uptimeSeconds = status.uptimeSeconds,
                            downloadBytesPerSecond = downloadBytesPerSecond,
                            uploadBytesPerSecond = uploadBytesPerSecond,
                            downloadedBytes = currentTransferSample?.receivedBytes,
                            uploadedBytes = currentTransferSample?.sentBytes,
                            totalFileSizeBytes = totalFileSizeBytes ?: it.totalFileSizeBytes,
                            rssBytes = readRssBytes(currentPid()),
                            allocatedBytes = status.allocatedBytes,
                            systemBytes = status.systemBytes,
                            goroutines = status.goroutines,
                        )
                    }
                }
                .onFailure { error ->
                    consecutiveFailures += 1
                    val signature = connectionErrorSignature(error)
                    if (
                        consecutiveFailures == 1 ||
                        consecutiveFailures % STATUS_FAILURE_LOG_INTERVAL == 0 ||
                        signature != lastSignature
                    ) {
                        logConnectionFailure(
                            context = "REST status polling failed ($consecutiveFailures consecutive failures)",
                            error = error,
                            terminal = false,
                        )
                    }
                    lastSignature = signature
                }
            if (process == null && consecutiveFailures > 0) break
            delay(STATUS_POLL_INTERVAL_MILLIS.milliseconds)
        }
    }

    private fun transferRate(
        previous: Long?,
        current: Long?,
        elapsedMillis: Long?,
    ): Long? {
        if (current == null) return null
        if (previous == null || elapsedMillis == null || elapsedMillis <= 0L) return 0L
        if (current < previous) return 0L
        return ((current - previous).toDouble() * 1_000.0 / elapsedMillis).toLong()
    }

    private data class TransferSample(
        val receivedBytes: Long?,
        val sentBytes: Long?,
        val elapsedRealtimeMillis: Long,
    )

    private fun currentPid(): Long? {
        val rememberedPid = preferences.getLong(KEY_PID, -1L).takeIf { it > 0 }
        val rememberedPath = preferences.getString(KEY_EXECUTABLE_PATH, null)
        if (rememberedPid != null && isCoreProcess(rememberedPid, rememberedPath)) {
            return rememberedPid
        }
        return findCorePid()?.also { pid ->
            val path = processCommand(pid)
            preferences.edit {
                putLong(KEY_PID, pid)
                putString(KEY_EXECUTABLE_PATH, path)
            }
        }
    }

    private fun findCorePid(): Long? = runCatching {
        val knownPaths = coreRegistry.knownExecutablePaths()
        File("/proc").listFiles()
            ?.asSequence()
            ?.filter { entry -> entry.isDirectory && entry.name.all(Char::isDigit) }
            ?.mapNotNull { entry -> entry.name.toLongOrNull() }
            ?.firstOrNull { pid -> processCommand(pid) in knownPaths }
    }.getOrNull()

    private fun processCommand(pid: Long): String? = runCatching {
        File("/proc/$pid/cmdline")
            .readText()
            .substringBefore('\u0000')
            .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun isCoreProcess(pid: Long, expectedPath: String?): Boolean {
        val command = processCommand(pid) ?: return false
        return if (expectedPath != null) {
            command == expectedPath
        } else {
            command in coreRegistry.knownExecutablePaths()
        }
    }

    private fun readRssBytes(pid: Long?): Long? {
        if (pid == null) return null
        return runCatching {
            FileReader("/proc/$pid/status").buffered().useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?.times(1024)
            }
        }.getOrNull()
    }

    private fun killRememberedProcessIfOwned() {
        val pid = currentPid() ?: return
        val expectedPath = preferences.getString(KEY_EXECUTABLE_PATH, null)
        if (isCoreProcess(pid, expectedPath)) {
            android.os.Process.killProcess(pid.toInt())
        }
    }

    private fun rememberProcess(executable: CoreExecutable) {
        preferences.edit {
            putString(KEY_EXECUTABLE_PATH, executable.file.absolutePath)
            putString(KEY_RUNNING_CORE_ID, executable.id)
        }
    }

    private fun clearProcessRecord() {
        preferences.edit {
            remove(KEY_PID)
            remove(KEY_EXECUTABLE_PATH)
            remove(KEY_RUNNING_CORE_ID)
        }
    }

    private fun loadOrCreateApiKey(): String {
        preferences.getString(KEY_API_KEY, null)?.let { return it }
        val key = UUID.randomUUID().toString().replace("-", "")
        preferences.edit(commit = true) { putString(KEY_API_KEY, key) }
        return key
    }

    private fun loadOrCreateGuiCredentials(): ManagedGuiCredentials {
        val storedUsername = preferences.getString(KEY_GUI_USERNAME, null)
            ?.takeIf(String::isNotBlank)
        val storedPassword = preferences.getString(KEY_GUI_PASSWORD, null)
            ?.takeIf(String::isNotBlank)
        if (storedUsername != null && storedPassword != null) {
            return ManagedGuiCredentials(storedUsername, storedPassword)
        }

        val credentials = ManagedGuiCredentials(
            username = "app-${UUID.randomUUID().toString().replace("-", "").take(16)}",
            password = buildString {
                repeat(2) { append(UUID.randomUUID().toString().replace("-", "")) }
            },
        )
        preferences.edit(commit = true) {
            putString(KEY_GUI_USERNAME, credentials.username)
            putString(KEY_GUI_PASSWORD, credentials.password)
        }
        return credentials
    }

    private fun loadGuiAuthenticationEnabled(): Boolean {
        if (preferences.contains(KEY_GUI_AUTHENTICATION_ENABLED)) {
            return preferences.getBoolean(KEY_GUI_AUTHENTICATION_ENABLED, true)
        }
        val enabled = if (configFile.exists) {
            runCatching {
                configFile.read(loadGuiPortConflictBehavior(), rememberedLocalDeviceId())
                    .guiAuthenticationEnabled
            }.getOrDefault(true)
        } else {
            true
        }
        preferences.edit(commit = true) {
            putBoolean(KEY_GUI_AUTHENTICATION_ENABLED, enabled)
        }
        return enabled
    }

    private fun saveGuiAuthentication(
        enabled: Boolean,
        credentials: ManagedGuiCredentials,
    ) {
        preferences.edit(commit = true) {
            putBoolean(KEY_GUI_AUTHENTICATION_ENABLED, enabled)
            putString(KEY_GUI_USERNAME, credentials.username)
            putString(KEY_GUI_PASSWORD, credentials.password)
        }
        managedGuiAuthenticationEnabled = enabled
        managedGuiCredentials = credentials
    }

    private fun supportsCoreAbi(): Boolean =
        Build.SUPPORTED_ABIS.any { it in SUPPORTED_CORE_ABIS }

    private fun logError(message: String, error: Throwable? = null) {
        writeControllerLog("ERROR", message, error)
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
    }

    private fun logInfo(message: String) {
        writeControllerLog("INFO", message)
        Log.i(TAG, message)
    }

    private fun logWarning(message: String, error: Throwable? = null) {
        writeControllerLog("WARN", message, error)
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    private fun logConnectionFailure(
        context: String,
        error: Throwable,
        terminal: Boolean,
    ) {
        val summary = "$context; ${connectionErrorLogSummary(error)}"
        if (terminal || isCriticalConnectionError(error)) {
            logError(summary, error)
        } else {
            logWarning(summary, error)
        }
    }

    private fun connectionErrorSummary(error: Throwable): String {
        val category = when (error) {
            is UnknownServiceException, is SecurityException -> "Android 网络安全策略拒绝连接"
            is SyncthingRestException -> when (error.responseCode) {
                401, 403 -> "REST API 鉴权失败（HTTP ${error.responseCode}）"
                else -> "REST API 返回异常状态（HTTP ${error.responseCode}）"
            }
            is SocketTimeoutException -> "REST API 连接或读取超时"
            is ConnectException -> "REST API 拒绝连接"
            is NoRouteToHostException -> "REST API 地址不可达"
            is UnknownHostException -> "REST API 地址解析失败"
            is SocketException -> "REST API 套接字连接失败"
            is IOException -> "REST API 通信失败"
            else -> "REST API 请求失败"
        }
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$category，异常=${error.javaClass.simpleName}，详情=${redact(detail)}，地址=${restApiAddress()}"
    }

    private fun connectionErrorLogSummary(error: Throwable): String {
        val category = when (error) {
            is UnknownServiceException, is SecurityException -> "Connection rejected by Android network security policy"
            is SyncthingRestException -> when (error.responseCode) {
                401, 403 -> "REST API authentication failed (HTTP ${error.responseCode})"
                else -> "REST API returned an unexpected status (HTTP ${error.responseCode})"
            }
            is SocketTimeoutException -> "REST API connection or read timed out"
            is ConnectException -> "REST API refused the connection"
            is NoRouteToHostException -> "REST API address is unreachable"
            is UnknownHostException -> "Failed to resolve REST API address"
            is SocketException -> "REST API socket connection failed"
            is IOException -> "REST API communication failed"
            else -> "REST API request failed"
        }
        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$category, exception=${error.javaClass.simpleName}, detail=${redact(detail)}, address=${restApiAddress()}"
    }

    private fun connectionErrorSignature(error: Throwable): String = when (error) {
        is SyncthingRestException -> "${error.javaClass.name}:${error.responseCode}"
        else -> "${error.javaClass.name}:${error.message}"
    }

    private fun isCriticalConnectionError(error: Throwable): Boolean =
        error is UnknownServiceException ||
            error is SecurityException ||
            error is SyncthingRestException ||
            (
                error is IOException &&
                    error !is SocketException &&
                    error !is SocketTimeoutException &&
                    error !is UnknownHostException
            )

    private fun writeControllerLog(level: String, message: String, error: Throwable? = null) {
        val detail = error?.message
            ?.takeIf { it.isNotBlank() && !message.contains(it) }
            ?.let { " | ${error.javaClass.simpleName}: ${redact(it)}" }
            .orEmpty()
        val line = "${controllerTimestamp()} [$level] ${redact(message)}$detail\n"
        runCatching {
            synchronized(CONTROLLER_LOG_LOCK) {
                val directory = File(applicationContext.filesDir, "logs").apply { mkdirs() }
                val file = File(directory, CONTROLLER_LOG_FILE)
                if (file.length() >= MAX_CONTROLLER_LOG_BYTES) {
                    val oldFile = File(directory, "$CONTROLLER_LOG_FILE.1")
                    oldFile.delete()
                    file.renameTo(oldFile)
                }
                file.appendText(line)
            }
        }.onFailure { logError ->
            Log.w(TAG, "Failed to write controller log", logError)
        }
    }

    private fun controllerTimestamp(): String =
        SimpleDateFormat(CONTROLLER_TIME_FORMAT, Locale.US).format(Date())

    private fun redact(value: String): String {
        val apiKey = preferences.getString(KEY_API_KEY, null)
        return (if (apiKey.isNullOrEmpty()) value else value.replace(apiKey, REDACTED_VALUE))
            .replace(Regex("X-API-Key[=:]\\s*\\S+", RegexOption.IGNORE_CASE), "X-API-Key=$REDACTED_VALUE")
            .replace(Regex("--gui-apikey=\\S+"), "--gui-apikey=$REDACTED_VALUE")
    }

    private fun logCoreLogTail() {
        val logDirectory = File(applicationContext.filesDir, "logs")
        val apiKey = preferences.getString(KEY_API_KEY, null)
        val rawReport = listOf(
            "launcher.log" to File(logDirectory, "launcher.log"),
            "syncthing.log" to File(logDirectory, "syncthing.log"),
        ).mapNotNull { (name, file) ->
            readLogTail(file)?.let { tail -> "===== $name =====\n$tail" }
        }.joinToString("\n")
        val report = (if (apiKey.isNullOrEmpty()) {
            rawReport
        } else {
            rawReport.replace(apiKey, REDACTED_VALUE)
        })
            .replace(Regex("--gui-apikey=\\S+"), "--gui-apikey=$REDACTED_VALUE")

        if (report.isBlank()) {
            Log.e(TAG, "Core log files do not exist or are empty")
            return
        }
        logCoreConnectionDiagnostics(report)
        report.takeLast(MAX_CORE_LOG_CHARS)
            .chunked(LOGCAT_CHUNK_CHARS)
            .forEachIndexed { index, chunk ->
                Log.e(TAG, "Core log tail ${index + 1}:\n$chunk")
            }
    }

    private fun logCoreConnectionDiagnostics(report: String) {
        val warnings = report.lineSequence()
            .filter { line -> " WRN " in line || " ERR " in line }
            .mapNotNull { line ->
                when {
                    "lookup " in line && ":53" in line ->
                        "Core DNS resolution failed: the core attempted to resolve through the local loopback DNS server ([::1]:53); discovery or relay services may be unavailable"
                    "Failed to list network interfaces" in line && "permission denied" in line ->
                        "Core cannot read network interfaces; UPnP/NAT detection may be unavailable"
                    "relays.syncthing.net" in line && "Service failed" in line ->
                        "Core relay service connection failed; check DNS and internet connectivity"
                    "discover" in line && ("failed" in line.lowercase(Locale.US) || "error=" in line) ->
                        "Core discovery service connection failed; check DNS, network permissions, and internet connectivity"
                    "api" in line && ("failed" in line.lowercase(Locale.US) || "error=" in line) ->
                        "Core REST/GUI listener error: ${line.substringAfter("error=", line)}"
                    else -> null
                }
            }
            .map(::redact)
            .distinct()
            .take(MAX_CORE_DIAGNOSTICS)
            .toList()

        warnings.forEach { diagnostic ->
            if (diagnostic.startsWith("Core DNS") || diagnostic.startsWith("Core REST")) {
                logError(diagnostic)
            } else {
                logWarning(diagnostic)
            }
        }
    }

    private fun readLogTail(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        file.useLines { lines ->
            lines.toList()
                .takeLast(MAX_CORE_LOG_LINES)
                .joinToString("\n")
                .ifBlank { null }
        }
    }.onFailure { error ->
        Log.w(TAG, "Failed to read core log: ${file.name}", error)
    }.getOrNull()

    data class SessionResult(
        val started: Boolean,
        val runtimeMillis: Long,
        val exitCode: Int?,
    )

    private data class ApiWaitResult(
        val ready: Boolean,
        val lastError: Throwable?,
    )

    companion object {
        private const val TAG = "SyncthingCore"
        private const val DEFAULT_GUI_PORT = 8384
        private const val SYNCTHING_HOME_DIRECTORY = "syncthing-home"
        private const val CONFIG_FILE_NAME = "config.xml"
        private const val PREFERENCES = "core_runtime"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_GUI_USERNAME = "gui_username"
        private const val KEY_GUI_PASSWORD = "gui_password"
        private const val KEY_GUI_AUTHENTICATION_ENABLED = "gui_authentication_enabled"
        private const val KEY_PID = "pid"
        private const val KEY_EXECUTABLE_PATH = "executable_path"
        private const val KEY_RUNNING_CORE_ID = "running_core_id"
        private const val KEY_GUI_PORT = "gui_port"
        private const val KEY_ACTIVE_GUI_PORT = "active_gui_port"
        private const val KEY_GUI_USE_TLS = "gui_use_tls"
        private const val KEY_GUI_PORT_CONFLICT_BEHAVIOR = "gui_port_conflict_behavior"
        private const val KEY_LOCAL_DEVICE_ID = "local_device_id"
        private const val GUI_PORT_PROBE_LIMIT = 100
        private const val API_READY_POLL_COUNT = 30
        private const val API_READY_POLL_INTERVAL_MILLIS = 500L
        private const val CONNECTION_RETRY_LOG_INTERVAL = 10
        private const val STATUS_POLL_INTERVAL_MILLIS = 2_000L
        private const val STATUS_FAILURE_LOG_INTERVAL = 15
        private const val DISCOVERY_PING_TIMEOUT_MILLIS = 1_000
        private const val NANOSECONDS_PER_MILLISECOND = 1_000_000L
        private const val STOP_TIMEOUT_SECONDS = 5L
        private const val GENERATE_TIMEOUT_SECONDS = 15L
        private const val FORCE_TIMEOUT_SECONDS = 2L
        private const val STOP_POLL_COUNT = 10
        private const val STOP_POLL_INTERVAL_MILLIS = 500L
        private const val MAX_CORE_LOG_LINES = 40
        private const val MAX_CORE_LOG_CHARS = 8_000
        private const val MAX_GUI_TLS_FILE_BYTES = 1024 * 1024
        private const val LOGCAT_CHUNK_CHARS = 3_000
        private const val REDACTED_VALUE = "[REDACTED]"
        private const val CONTROLLER_LOG_FILE = "controller.log"
        private const val CONTROLLER_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val MAX_CONTROLLER_LOG_BYTES = 1024L * 1024L
        private const val MAX_CORE_DIAGNOSTICS = 12
        private val SUPPORTED_CORE_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        private val CONTROLLER_LOG_LOCK = Any()
    }

    private fun formatGuiAddress(address: String, port: Int): String {
        val normalizedAddress = address.trim().removePrefix("[").removeSuffix("]")
        return if (':' in normalizedAddress) "[$normalizedAddress]:$port" else "$normalizedAddress:$port"
    }

    private fun formatGuiBaseUrl(address: String, port: Int, useTls: Boolean): String {
        val normalizedAddress = address.trim().removePrefix("[").removeSuffix("]")
        val urlHost = if (':' in normalizedAddress) "[$normalizedAddress]" else normalizedAddress
        val scheme = if (useTls) "https" else "http"
        return "$scheme://$urlHost:$port"
    }

    private fun openGuiConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        if (connection !is HttpsURLConnection) return connection

        val certificate = ByteArrayInputStream(activeGuiCertificateBytes()).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("syncthing-gui", certificate)
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm(),
        ).apply {
            init(keyStore)
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }
        connection.sslSocketFactory = sslContext.socketFactory
        // 连接只信任应用私有目录中的固定证书，因此无需依赖自签名证书的主机名。
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        return connection
    }

    private fun activeGuiCertificateBytes(): ByteArray {
        activeGuiCertificate?.let { return it }
        val certificateFile = File(homeDirectory, GuiTlsFile.CERTIFICATE.fileName)
        if (!certificateFile.isFile) {
            throw IOException("HTTPS 证书不存在：${certificateFile.name}")
        }
        val loadedCertificate = certificateFile.inputStream().use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }.apply { checkValidity() }.encoded
        return synchronized(this) {
            activeGuiCertificate ?: loadedCertificate.also { activeGuiCertificate = it }
        }
    }

    private fun validateGuiTlsFile(type: GuiTlsFile, content: ByteArray) {
        if (content.isEmpty()) throw IOException("${type.displayName}文件为空")
        if (content.size > MAX_GUI_TLS_FILE_BYTES) {
            throw IOException("${type.displayName}文件不能超过 1 MiB")
        }
        val pem = content.toString(Charsets.US_ASCII)
        when (type) {
            GuiTlsFile.CERTIFICATE -> {
                if (!pem.contains("-----BEGIN CERTIFICATE-----")) {
                    throw IOException("所选文件不是 PEM 格式的 X.509 证书")
                }
                try {
                    val certificates = ByteArrayInputStream(content).use { input ->
                        CertificateFactory.getInstance("X.509").generateCertificates(input)
                    }
                    if (certificates.isEmpty()) throw IOException("证书文件中没有有效证书")
                    (certificates.first() as X509Certificate).checkValidity()
                } catch (error: IOException) {
                    throw error
                } catch (error: Throwable) {
                    throw IOException("无法解析 X.509 证书", error)
                }
            }

            GuiTlsFile.PRIVATE_KEY -> {
                val supportedHeaders = listOf(
                    "PRIVATE KEY",
                    "RSA PRIVATE KEY",
                    "EC PRIVATE KEY",
                )
                val keyType = supportedHeaders.firstOrNull { keyType ->
                    pem.contains("-----BEGIN $keyType-----") &&
                        pem.contains("-----END $keyType-----")
                }
                if (keyType == null) {
                    throw IOException("所选文件不是受支持的 PEM 私钥")
                }
            }
        }
    }

    private fun parseGuiPort(address: String): Int = address
        .substringAfterLast(':', DEFAULT_GUI_PORT.toString())
        .toIntOrNull()
        ?.takeIf { it in 1..65535 }
        ?: DEFAULT_GUI_PORT

    private fun parseGuiHost(address: String): String {
        val normalizedAddress = address.trim()
        if (normalizedAddress.startsWith("[")) {
            val closingBracket = normalizedAddress.indexOf(']')
            if (closingBracket > 1) return normalizedAddress.substring(1, closingBracket)
        }
        val separatorIndex = normalizedAddress.lastIndexOf(':')
        return if (separatorIndex > 0) normalizedAddress.substring(0, separatorIndex) else "127.0.0.1"
    }

    private fun resolveLaunchGuiAddress(
        configuredAddress: String,
        behavior: SettingConfiguration.GuiPortConflictBehavior,
    ): String {
        if (behavior == SettingConfiguration.GuiPortConflictBehavior.FAIL) return configuredAddress
        val host = parseGuiHost(configuredAddress)
        val configuredPort = parseGuiPort(configuredAddress)
        val lastPort = minOf(65535, configuredPort + GUI_PORT_PROBE_LIMIT)
        val selectedPort = (configuredPort..lastPort).firstOrNull { port ->
            isPortAvailable(host, port)
        } ?: throw IOException("GUI 端口 $configuredPort 及其后 $GUI_PORT_PROBE_LIMIT 个端口均不可用")
        if (selectedPort != configuredPort) {
            logWarning("GUI port $configuredPort is in use; temporarily using port $selectedPort for this session")
        }
        return formatGuiAddress(host, selectedPort)
    }

    private fun isPortAvailable(host: String, port: Int): Boolean = runCatching {
        val bindAddress = InetAddress.getByName(host)
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(bindAddress, port))
        }
    }.isSuccess

    private fun loadGuiPortConflictBehavior(): SettingConfiguration.GuiPortConflictBehavior =
        preferences.getString(KEY_GUI_PORT_CONFLICT_BEHAVIOR, null)
            ?.let { storedValue ->
                SettingConfiguration.GuiPortConflictBehavior.entries
                    .firstOrNull { it.name == storedValue }
            }
            ?: SettingConfiguration.GuiPortConflictBehavior.FAIL

    private fun startupSetting(
        portConflictBehavior: SettingConfiguration.GuiPortConflictBehavior,
    ): SettingConfiguration = SettingConfiguration.startupDefaults(
        guiListenAddress = loadProtocolStack().guiListenAddress,
        guiPort = preferences.getInt(KEY_GUI_PORT, DEFAULT_GUI_PORT),
        guiPortConflictBehavior = portConflictBehavior,
        guiUseTls = preferences.getBoolean(KEY_GUI_USE_TLS, false),
    )

    private fun configuredGuiPort(
        portConflictBehavior: SettingConfiguration.GuiPortConflictBehavior,
    ): Int = if (configFile.exists) {
        runCatching {
            configFile.read(portConflictBehavior, rememberedLocalDeviceId()).guiPort
        }.getOrElse {
            preferences.getInt(KEY_GUI_PORT, DEFAULT_GUI_PORT)
        }
    } else {
        preferences.getInt(KEY_GUI_PORT, DEFAULT_GUI_PORT)
    }

    private fun configuredGuiUseTls(): Boolean = if (configFile.exists) {
        runCatching {
            configFile.read(loadGuiPortConflictBehavior(), rememberedLocalDeviceId()).guiUseTls
        }.getOrElse {
            preferences.getBoolean(KEY_GUI_USE_TLS, false)
        }
    } else {
        preferences.getBoolean(KEY_GUI_USE_TLS, false)
    }

    private fun configuredDeviceName(): String = if (configFile.exists) {
        runCatching {
            configFile.read(loadGuiPortConflictBehavior(), rememberedLocalDeviceId()).deviceName
        }.getOrDefault("Syncthing")
    } else {
        startupSetting(loadGuiPortConflictBehavior()).deviceName
    }

    private fun initialGuiPort(): Int {
        val configuredPort = configuredGuiPort(loadGuiPortConflictBehavior())
        return preferences.getInt(KEY_ACTIVE_GUI_PORT, configuredPort)
    }

    private fun saveStartupSetting(configuration: SettingConfiguration) {
        preferences.edit {
            putInt(KEY_GUI_PORT, configuration.guiPort)
                .putBoolean(KEY_GUI_USE_TLS, configuration.guiUseTls)
                .putString(
                    KEY_GUI_PORT_CONFLICT_BEHAVIOR,
                    configuration.guiPortConflictBehavior.name,
                )
        }
    }

    private fun loadProtocolStack(): SettingProtocolStack =
        appSettingsStorage.getString(AppSettingPrivateStorage.KEY_PROTOCOL_STACK)
            ?.let { storedValue ->
                SettingProtocolStack.entries.firstOrNull { it.name == storedValue }
            }
            ?: SettingProtocolStack.DUAL

    private fun rememberedLocalDeviceId(): String? =
        preferences.getString(KEY_LOCAL_DEVICE_ID, null)?.takeIf(String::isNotBlank)

    private fun rememberLocalDeviceId(deviceId: String?) {
        deviceId?.takeIf(String::isNotBlank)?.let { id ->
            preferences.edit { putString(KEY_LOCAL_DEVICE_ID, id) }
        }
    }

    private fun requireLocalDeviceId(): String = restClient.status().myId
        ?.also(::rememberLocalDeviceId)
        ?: throw IOException("Syncthing REST 状态中缺少本机设备 ID")

    internal fun reconcileImportedConfiguration() {
        activeGuiHost = loadProtocolStack().guiListenAddress
        activeGuiPort = initialGuiPort()
        activeGuiUseTls = configuredGuiUseTls()
        activeGuiCertificate = null
        configFile.ensureGuiAuthentication(
            enabled = managedGuiAuthenticationEnabled,
            username = managedGuiCredentials.username,
            password = managedGuiCredentials.password,
            guiAddress = formatGuiAddress(activeGuiHost, activeGuiPort),
            apiKey = loadOrCreateApiKey(),
        )
        preferences.edit { remove(KEY_LOCAL_DEVICE_ID) }
    }

    private fun resolveFolderDirectory(configuredPath: String): File {
        val path = configuredPath.trim()
        if (path.isBlank()) throw IOException("文件夹路径为空，拒绝删除本地文件")

        val unresolvedDirectory = when {
            path == "~" -> applicationContext.filesDir
            path.startsWith("~/") -> File(applicationContext.filesDir, path.removePrefix("~/"))
            path.startsWith("~") -> throw IOException("无法解析文件夹路径：$configuredPath")
            File(path).isAbsolute -> File(path)
            else -> throw IOException("文件夹路径不是绝对路径：$configuredPath")
        }
        val normalizedPath = unresolvedDirectory.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(normalizedPath)) {
            throw IOException("文件夹路径是符号链接，拒绝删除本地文件：$configuredPath")
        }
        return normalizedPath.toFile().canonicalFile
    }

    private fun validateFolderDeletionTarget(
        directory: File,
        otherFolderPaths: List<String>,
    ) {
        val targetPath = directory.toPath()
        val filesRoot = applicationContext.filesDir.canonicalFile.toPath()
        val coreHome = homeDirectory.canonicalFile.toPath()
        val storageRoot = File("/storage").canonicalFile.toPath()

        val isSafeInternalPath = targetPath.startsWith(filesRoot) &&
            targetPath != filesRoot &&
            !targetPath.startsWith(coreHome)
        val isSafeStoragePath = if (targetPath.startsWith(storageRoot)) {
            val relativePath = storageRoot.relativize(targetPath)
            val minimumDepth = if (relativePath.firstOrNull()?.toString() == "emulated") 3 else 2
            relativePath.nameCount >= minimumDepth
        } else {
            false
        }
        if (!isSafeInternalPath && !isSafeStoragePath) {
            throw IOException("文件夹路径不在允许删除的目录范围内：${directory.path}")
        }

        val externalStorageRoot = Environment.getExternalStorageDirectory().canonicalFile.toPath()
        if (targetPath == externalStorageRoot) {
            throw IOException("拒绝删除设备公共存储根目录：${directory.path}")
        }

        otherFolderPaths.forEach { otherConfiguredPath ->
            val otherPath = resolveFolderDirectory(otherConfiguredPath).toPath()
            if (targetPath.startsWith(otherPath) || otherPath.startsWith(targetPath)) {
                throw IOException("该路径与其他同步文件夹重叠，拒绝删除本地文件：${directory.path}")
            }
        }
    }

    private fun deleteDirectoryWithoutFollowingLinks(directory: File) {
        val targetPath = directory.toPath()
        if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) return
        if (!Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("文件夹路径不是目录：${directory.path}")
        }

        Files.walkFileTree(targetPath, object : SimpleFileVisitor<Path>() {
            override fun visitFile(
                file: Path,
                attributes: BasicFileAttributes,
            ): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(
                directory: Path,
                error: IOException?,
            ): FileVisitResult {
                if (error != null) throw error
                Files.delete(directory)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun restApiAddress(): String {
        val scheme = if (activeGuiUseTls) "https" else "http"
        return "$scheme://localhost:$activeGuiPort"
    }
}

private fun Process.exitCodeOrNull(): Int? =
    if (isAlive) null else runCatching { exitValue() }.getOrNull()

private fun Throwable.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

private data class ManagedGuiCredentials(
    val username: String,
    val password: String,
)
