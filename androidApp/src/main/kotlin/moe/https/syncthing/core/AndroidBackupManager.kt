package moe.https.syncthing.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.https.syncthing.BuildConfig
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

class AndroidBackupManager(
    context: Context,
    private val runtime: CoreRuntime,
    private val coreController: AndroidCoreController,
) : BackupController {
    private val applicationContext = context.applicationContext
    private val operationMutex = Mutex()
    private val preferences: SharedPreferences = applicationContext.getSharedPreferences(
        CORE_RUNTIME_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override suspend fun exportBackup(
        destinationUri: String,
        password: String?,
    ): Unit = operationMutex.withLock {
        withCoreStopped {
            withContext(Dispatchers.IO) {
                val homeDirectory = File(applicationContext.filesDir, CORE_HOME_DIRECTORY)
                val requiredFiles = REQUIRED_CORE_FILES.associateWith { File(homeDirectory, it) }
                requiredFiles.forEach { (name, file) ->
                    if (!file.isFile) throw IOException("核心备份缺少必需文件：$name")
                }

                val temporaryZip = File.createTempFile("syncthing-backup-", ".zip", applicationContext.cacheDir)
                try {
                    createCurrentBackup(temporaryZip, homeDirectory, password)
                    val uri = Uri.parse(destinationUri)
                    applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        temporaryZip.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("无法打开备份目标文件")
                } finally {
                    temporaryZip.delete()
                }
                Unit
            }
        }
    }

    override suspend fun importBackup(
        sourceUri: String,
        password: String?,
        format: BackupImportFormat,
    ) = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val temporaryZip = File.createTempFile("syncthing-import-", ".zip", applicationContext.cacheDir)
            val stagingDirectory = File(
                applicationContext.filesDir,
                ".backup-staging-${UUID.randomUUID()}",
            )
            try {
                val uri = Uri.parse(sourceUri)
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    temporaryZip.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            if (totalBytes > MAX_ARCHIVE_SIZE_BYTES) {
                                throw IOException("备份文件超过允许的大小")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw IOException("无法打开备份文件")

                val preparedBackup = prepareImport(
                    archive = temporaryZip,
                    password = password,
                    format = format,
                    stagingDirectory = stagingDirectory,
                )
                withCoreStopped {
                    applyPreparedBackup(preparedBackup)
                }
            } finally {
                temporaryZip.delete()
                stagingDirectory.deleteRecursively()
            }
        }
    }

    private fun createCurrentBackup(
        target: File,
        homeDirectory: File,
        password: String?,
    ) {
        val passwordChars = password?.toCharArray()
        try {
            val zipFile = if (passwordChars == null) ZipFile(target) else ZipFile(target, passwordChars)
            addBytes(
                zipFile,
                MANIFEST_PATH,
                backupJson.encodeToString(createManifest()).toByteArray(Charsets.UTF_8),
                encrypted = passwordChars != null,
            )
            addBytes(
                zipFile,
                APP_SETTINGS_PATH,
                exportAppSettings().toString(2).toByteArray(Charsets.UTF_8),
                encrypted = passwordChars != null,
            )
            (REQUIRED_CORE_FILES + OPTIONAL_CORE_FILES).forEach { fileName ->
                val source = File(homeDirectory, fileName)
                if (source.isFile) {
                    addBytes(
                        zipFile,
                        "$CORE_PATH_PREFIX$fileName",
                        source.readBytes(),
                        encrypted = passwordChars != null,
                    )
                }
            }
        } finally {
            passwordChars?.fill('\u0000')
        }
    }

    private fun createManifest(): BackupManifest {
        val snapshot = runtime.snapshot.value
        val selectedCore = snapshot.availableCores.firstOrNull { it.id == snapshot.selectedCoreId }
        val isBuiltIn = selectedCore?.source == CoreSource.BUILT_IN ||
            snapshot.selectedCoreId == CoreRegistry.BUILT_IN_ID
        return BackupManifest(
            formatVersion = CURRENT_FORMAT_VERSION,
            createdAt = Instant.now().toString(),
            app = BackupManifest.AppInfo(
                applicationId = BuildConfig.APPLICATION_ID,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                versionName = BuildConfig.VERSION_NAME,
            ),
            core = BackupManifest.CoreInfo(
                id = snapshot.selectedCoreId,
                source = selectedCore?.source?.name
                    ?: CoreSource.BUILT_IN.name.takeIf {
                        snapshot.selectedCoreId == CoreRegistry.BUILT_IN_ID
                    },
                version = selectedCore?.version
                    ?: snapshot.version
                    ?: if (isBuiltIn) BuildConfig.SYNCTHING_VERSION else "未知",
                commit = BuildConfig.SYNCTHING_COMMIT.takeIf { isBuiltIn },
            )
        )
    }

    private fun exportAppSettings(): JSONObject {
        val values = JSONObject()
        APP_SETTING_KEYS.forEach { key ->
            if (!preferences.contains(key)) return@forEach
            val value = preferences.all[key] ?: return@forEach
            val encoded = when (value) {
                is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                is Int -> JSONObject().put("type", "int").put("value", value)
                is Long -> JSONObject().put("type", "long").put("value", value)
                is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                is String -> JSONObject().put("type", "string").put("value", value)
                is Set<*> -> JSONObject()
                    .put("type", "stringSet")
                    .put("value", JSONArray(value.filterIsInstance<String>().sorted()))
                else -> return@forEach
            }
            values.put(key, encoded)
        }
        return JSONObject().put("values", values)
    }

    private fun prepareImport(
        archive: File,
        password: String?,
        format: BackupImportFormat,
        stagingDirectory: File,
    ): PreparedBackup {
        val passwordChars = password?.toCharArray()
        try {
            val zipFile = if (passwordChars == null) ZipFile(archive) else ZipFile(archive, passwordChars)
            if (!zipFile.isValidZipFile) throw IOException("所选文件不是有效的 ZIP 备份")
            if (zipFile.isEncrypted && passwordChars == null) {
                throw IOException("该备份已加密，请输入备份密码")
            }
            validateArchiveHeaders(zipFile, format)
            stagingDirectory.mkdirs()
            val stagedHome = File(stagingDirectory, CORE_HOME_DIRECTORY).apply { mkdirs() }
            return when (format) {
                BackupImportFormat.CURRENT -> prepareCurrentImport(zipFile, stagedHome)
                BackupImportFormat.LEGACY -> prepareLegacyImport(zipFile, stagedHome)
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("无法读取备份：${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            passwordChars?.fill('\u0000')
        }
    }

    private fun prepareCurrentImport(zipFile: ZipFile, stagedHome: File): PreparedBackup {
        val manifest = backupJson.decodeFromString<BackupManifest>(readEntryText(zipFile, MANIFEST_PATH))
        validateManifest(manifest)
        val settings = JSONObject(readEntryText(zipFile, APP_SETTINGS_PATH))
        val appSettings = decodeAppSettings(settings)
        REQUIRED_CORE_FILES.forEach { fileName ->
            extractEntry(zipFile, "$CORE_PATH_PREFIX$fileName", File(stagedHome, fileName), required = true)
        }
        OPTIONAL_CORE_FILES.forEach { fileName ->
            extractEntry(zipFile, "$CORE_PATH_PREFIX$fileName", File(stagedHome, fileName), required = false)
        }
        validateStagedCore(stagedHome)
        return PreparedBackup(stagedHome, appSettings)
    }

    private fun prepareLegacyImport(zipFile: ZipFile, stagedHome: File): PreparedBackup {
        if (zipFile.getFileHeader(MANIFEST_PATH) != null) {
            throw IOException("新版备份不能通过旧版导入入口导入")
        }
        REQUIRED_CORE_FILES.forEach { fileName ->
            extractEntry(zipFile, fileName, File(stagedHome, fileName), required = true)
        }
        OPTIONAL_CORE_FILES.forEach { fileName ->
            extractEntry(zipFile, fileName, File(stagedHome, fileName), required = false)
        }
        validateStagedCore(stagedHome)
        return PreparedBackup(stagedHome, appSettings = null)
    }

    private fun validateManifest(manifest: BackupManifest) {
        try {
            manifest.validateForImport(
                supportedFormatVersion = CURRENT_FORMAT_VERSION,
                currentApplicationId = BuildConfig.APPLICATION_ID,
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
            )
        } catch (error: IllegalArgumentException) {
            throw IOException(error.message, error)
        }
    }

    private fun validateArchiveHeaders(zipFile: ZipFile, format: BackupImportFormat) {
        val maximumEntries = if (format == BackupImportFormat.CURRENT) {
            MAX_CURRENT_ARCHIVE_ENTRIES
        } else {
            MAX_LEGACY_ARCHIVE_ENTRIES
        }
        if (zipFile.fileHeaders.size > maximumEntries) {
            throw IOException("备份包含过多文件")
        }
        val seenNames = mutableSetOf<String>()
        zipFile.fileHeaders.forEach { header ->
            val name = header.fileName.replace('\\', '/')
            val segments = name.split('/')
            if (
                name.isBlank() ||
                name.startsWith('/') ||
                segments.any { it == "." || it == ".." } ||
                !seenNames.add(name)
            ) {
                throw IOException("备份包含不安全或重复的文件路径：$name")
            }
        }
    }

    private fun validateStagedCore(stagedHome: File) {
        REQUIRED_CORE_FILES.forEach { fileName ->
            val file = File(stagedHome, fileName)
            if (!file.isFile || file.length() == 0L) throw IOException("备份缺少有效的 $fileName")
        }
        SyncthingConfigFile(File(stagedHome, CONFIG_FILE_NAME)).validate()
    }

    private fun applyPreparedBackup(preparedBackup: PreparedBackup) {
        val homeDirectory = File(applicationContext.filesDir, CORE_HOME_DIRECTORY)
        val previousHome = File(
            applicationContext.filesDir,
            ".backup-previous-${UUID.randomUUID()}",
        )
        val previousSettings = snapshotAppSettings()
        var homeMoved = false
        try {
            if (homeDirectory.exists()) {
                moveDirectory(homeDirectory, previousHome)
                homeMoved = true
            }
            moveDirectory(preparedBackup.homeDirectory, homeDirectory)
            preparedBackup.appSettings?.let(::restoreAppSettings)
            runtime.reconcileImportedConfiguration()
        } catch (error: Throwable) {
            val rollbackError = runCatching {
                homeDirectory.deleteRecursively()
                if (homeMoved && previousHome.exists()) moveDirectory(previousHome, homeDirectory)
                restoreAppSettings(previousSettings)
                runtime.reconcileImportedConfiguration()
            }.exceptionOrNull()
            val message = if (rollbackError == null) {
                "导入失败，已恢复原配置"
            } else {
                "导入失败且自动恢复未完成，原配置保留在 ${previousHome.path}"
            }
            throw IOException(
                "$message：${error.message ?: error.javaClass.simpleName}",
                error,
            ).also { rollbackError?.let(it::addSuppressed) }
        }
        previousHome.deleteRecursively()
    }

    private suspend fun <T> withCoreStopped(operation: suspend () -> T): T {
        val shouldRestart = SyncthingCoreService.isDesiredRunning(applicationContext)
        if (!shouldRestart && runtime.snapshot.value.state in setOf(
                CoreState.STARTING,
                CoreState.RUNNING,
                CoreState.STOPPING,
            )
        ) {
            throw IOException("核心仍在运行，无法执行备份操作")
        }

        return try {
            if (shouldRestart) {
                coreController.stop()
                val stopped = withTimeoutOrNull(CORE_STOP_TIMEOUT_MILLIS) {
                    while (
                        SyncthingCoreService.isDesiredRunning(applicationContext) ||
                        runtime.snapshot.value.state in setOf(
                            CoreState.STARTING,
                            CoreState.RUNNING,
                            CoreState.STOPPING,
                        )
                    ) {
                        delay(CORE_STOP_POLL_MILLIS)
                    }
                    true
                }
                if (stopped != true) throw IOException("等待核心停止超时，备份操作已取消")
            }
            operation()
        } finally {
            if (shouldRestart) coreController.start()
        }
    }

    private fun addBytes(zipFile: ZipFile, path: String, bytes: ByteArray, encrypted: Boolean) {
        val parameters = ZipParameters().apply {
            fileNameInZip = path
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
            isEncryptFiles = encrypted
            if (encrypted) {
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        ByteArrayInputStream(bytes).use { input -> zipFile.addStream(input, parameters) }
    }

    private fun readEntryText(zipFile: ZipFile, path: String): String {
        val header = zipFile.getFileHeader(path) ?: throw IOException("备份缺少 $path")
        if (header.isDirectory || header.uncompressedSize > MAX_ENTRY_SIZE_BYTES) {
            throw IOException("备份中的 $path 无效")
        }
        val bytes = zipFile.getInputStream(header).use { input ->
            input.readBytesWithLimit(MAX_ENTRY_SIZE_BYTES, path)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun extractEntry(
        zipFile: ZipFile,
        path: String,
        target: File,
        required: Boolean,
    ) {
        val header = zipFile.getFileHeader(path)
        if (header == null) {
            if (required) throw IOException("备份缺少 $path")
            return
        }
        if (header.isDirectory || header.uncompressedSize > MAX_ENTRY_SIZE_BYTES) {
            throw IOException("备份中的 $path 无效")
        }
        target.parentFile?.mkdirs()
        zipFile.getInputStream(header).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > MAX_ENTRY_SIZE_BYTES) {
                        throw IOException("备份中的 $path 解压后过大")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun decodeAppSettings(root: JSONObject): Map<String, Any> {
        val values = root.optJSONObject("values") ?: throw IOException("应用设置格式无效")
        return buildMap {
            APP_SETTING_KEYS.forEach { key ->
                val encoded = values.optJSONObject(key) ?: return@forEach
                val value: Any = when (encoded.optString("type")) {
                    "boolean" -> encoded.getBoolean("value")
                    "int" -> encoded.getInt("value")
                    "long" -> encoded.getLong("value")
                    "float" -> encoded.getDouble("value").toFloat()
                    "string" -> encoded.getString("value")
                    "stringSet" -> encoded.getJSONArray("value").let { array ->
                        buildSet {
                            repeat(array.length()) { index -> add(array.getString(index)) }
                        }
                    }
                    else -> throw IOException("应用设置 $key 的类型无效")
                }
                put(key, value)
            }
        }
    }

    private fun snapshotAppSettings(): Map<String, Any> = buildMap {
        APP_SETTING_KEYS.forEach { key -> preferences.all[key]?.let { put(key, it) } }
    }

    private fun restoreAppSettings(values: Map<String, Any>) {
        val editor = preferences.edit()
        APP_SETTING_KEYS.forEach(editor::remove)
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (!editor.commit()) throw IOException("无法写入 App 设置")
    }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun java.io.InputStream.readBytesWithLimit(limit: Long, path: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > limit) throw IOException("备份中的 $path 解压后过大")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class PreparedBackup(
        val homeDirectory: File,
        val appSettings: Map<String, Any>?,
    )

    private companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val CORE_HOME_DIRECTORY = "syncthing-home"
        const val CONFIG_FILE_NAME = "config.xml"
        const val CORE_RUNTIME_PREFERENCES = "core_runtime"
        const val MANIFEST_PATH = "manifest.json"
        const val APP_SETTINGS_PATH = "app/settings.json"
        const val CORE_PATH_PREFIX = "core/"
        const val MAX_CURRENT_ARCHIVE_ENTRIES = 32
        const val MAX_LEGACY_ARCHIVE_ENTRIES = 100_000
        const val MAX_ARCHIVE_SIZE_BYTES = 512L * 1024L * 1024L
        const val MAX_ENTRY_SIZE_BYTES = 16L * 1024L * 1024L
        const val CORE_STOP_TIMEOUT_MILLIS = 15_000L
        const val CORE_STOP_POLL_MILLIS = 50L

        val REQUIRED_CORE_FILES = listOf("config.xml", "cert.pem", "key.pem")
        val OPTIONAL_CORE_FILES = listOf("https-cert.pem", "https-key.pem")
        val APP_SETTING_KEYS = setOf(
            "developer_mode",
            "auto_start_mode",
            "auto_start_condition",
            "protocol_stack",
            "listen_prefer",
            "discovery_prefer",
            "bottom_bar_pages",
            "bottom_bar_default_page",
        )
        val backupJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
