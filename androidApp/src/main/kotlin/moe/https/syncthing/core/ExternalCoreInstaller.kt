package moe.https.syncthing.core

import android.content.Context
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class ExternalCoreInstaller(context: Context) {
    private val applicationContext = context.applicationContext
    private val externalDirectory: File
        get() = File(applicationContext.filesDir, "core/external")
    private val importDirectory: File
        get() = File(applicationContext.cacheDir, "core-import")

    fun installedCores(): List<ExternalCoreExecutable> = externalDirectory
        .listFiles { file -> file.isDirectory }
        .orEmpty()
        .mapNotNull(::readInstalledCore)
        .sortedWith(
            compareByDescending<ExternalCoreExecutable> { it.importedAtMillis }
                .thenBy { it.id },
        )

    fun install(uri: Uri): ExternalCoreExecutable {
        val temporaryDirectory = importDirectory.apply {
            if (!exists() && !mkdirs()) throw IOException("无法创建核心导入临时目录")
        }
        val candidate = File(temporaryDirectory, "candidate-${UUID.randomUUID()}")
        try {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                candidate.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("无法读取所选核心文件")

            validateArm64Elf(candidate)
            setExecutable(candidate)
            val version = readVersion(candidate)
            val id = UUID.randomUUID().toString()
            val importedAt = System.currentTimeMillis()
            val sha256 = sha256(candidate)
            val targetDirectory = File(externalDirectory, id)
            if (!targetDirectory.mkdirs()) throw IOException("无法创建外置核心目录")
            val target = File(targetDirectory, BINARY_FILE_NAME)
            try {
                move(candidate, target)
                setExecutable(target)
                File(targetDirectory, METADATA_FILE_NAME).writeText(
                    JSONObject()
                        .put("id", id)
                        .put("version", version)
                        .put("importedAtMillis", importedAt)
                        .put("sha256", sha256)
                        .toString(),
                )
            } catch (error: Exception) {
                target.delete()
                File(targetDirectory, METADATA_FILE_NAME).delete()
                targetDirectory.delete()
                throw error
            }
            return ExternalCoreExecutable(id, version, target, importedAt, sha256)
        } finally {
            candidate.delete()
        }
    }

    fun delete(id: String): ExternalCoreExecutable {
        val core = installedCores().firstOrNull { installed -> installed.id == id }
            ?: throw IOException("所选外置核心不存在")
        val root = externalDirectory.canonicalFile
        val directory = core.file.parentFile?.canonicalFile
            ?: throw IOException("外置核心目录无效")
        if (directory.parentFile != root || directory.name != core.id) {
            throw IOException("拒绝删除外置核心目录之外的文件")
        }

        val entries = directory.listFiles()
            ?: throw IOException("无法读取外置核心目录")
        val allowedNames = setOf(BINARY_FILE_NAME, METADATA_FILE_NAME)
        if (entries.any { entry -> !entry.isFile || entry.name !in allowedNames }) {
            throw IOException("外置核心目录包含未知文件，已取消删除")
        }
        entries.forEach { entry ->
            if (!entry.delete()) throw IOException("无法删除外置核心文件：${entry.name}")
        }
        if (!directory.delete()) throw IOException("无法删除外置核心目录")
        return core
    }

    private fun readInstalledCore(directory: File): ExternalCoreExecutable? = runCatching {
        val metadata = JSONObject(File(directory, METADATA_FILE_NAME).readText())
        val id = metadata.getString("id")
        if (directory.name != id) return@runCatching null
        val file = File(directory, BINARY_FILE_NAME)
        if (!file.isFile) return@runCatching null
        ExternalCoreExecutable(
            id = id,
            version = metadata.getString("version"),
            file = file,
            importedAtMillis = metadata.getLong("importedAtMillis"),
            sha256 = metadata.getString("sha256"),
        )
    }.getOrNull()

    private fun validateArm64Elf(file: File) {
        val header = ByteArray(20)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset < header.size) throw IOException("核心文件过短，不是有效的 ELF 文件")
        }
        val hasElfMagic =
            header[0] == 0x7F.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        if (!hasElfMagic) throw IOException("所选文件不是 ELF 可执行文件")
        if (header[4].toInt() != ELF_CLASS_64 || header[5].toInt() != ELF_LITTLE_ENDIAN) {
            throw IOException("核心必须是 64 位小端 ELF 文件")
        }
        val machine =
            (header[18].toInt() and 0xFF) or
                ((header[19].toInt() and 0xFF) shl 8)
        if (machine != ELF_MACHINE_AARCH64) throw IOException("核心架构不是 arm64-v8a")
    }

    private fun setExecutable(file: File) {
        try {
            Os.chmod(file.absolutePath, EXECUTABLE_MODE)
        } catch (error: ErrnoException) {
            throw IOException("无法设置外置核心执行权限", error)
        }
    }

    private fun readVersion(file: File): String {
        val outputFile = File(importDirectory, "version-${UUID.randomUUID()}.txt")
        val process = try {
            ProcessBuilder(file.absolutePath, "--version")
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .apply {
                    environment()["HOME"] = applicationContext.filesDir.absolutePath
                    environment()["STNOUPGRADE"] = "1"
                }
                .start()
        } catch (error: IOException) {
            if (error.message.orEmpty().contains("Permission denied", ignoreCase = true)) {
                throw IOException(
                    "当前 Android 系统不允许执行应用私有目录中的外部核心，请使用内置核心",
                    error,
                )
            }
            throw error
        }
        try {
            if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("外置核心版本检查超时")
            }
            val buffer = ByteArray(MAX_VERSION_LENGTH + 1)
            val count = outputFile.inputStream().use { it.read(buffer) }.coerceAtLeast(0)
            val output = buffer.decodeToString(0, count)
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .trim()
                .take(MAX_VERSION_LENGTH)
            if (process.exitValue() != 0 || output.isBlank()) {
                throw IOException("外置核心无法运行：${output.ifBlank { "未知错误" }}")
            }
            if (!output.contains("syncthing", ignoreCase = true)) {
                throw IOException("所选文件未返回有效的 Syncthing 版本信息")
            }
            return output
        } finally {
            outputFile.delete()
        }
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val BINARY_FILE_NAME = "syncthing"
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val EXECUTABLE_MODE = 448 // 0700
        private const val ELF_CLASS_64 = 2
        private const val ELF_LITTLE_ENDIAN = 1
        private const val ELF_MACHINE_AARCH64 = 183
        private const val VERSION_TIMEOUT_SECONDS = 5L
        private const val MAX_VERSION_LENGTH = 160
    }
}
