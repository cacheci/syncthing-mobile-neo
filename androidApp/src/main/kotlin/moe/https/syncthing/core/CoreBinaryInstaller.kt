package moe.https.syncthing.core

import android.content.Context
import android.net.Uri
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class CoreBinaryInstaller(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val binaryFile: File
        get() = File(applicationContext.filesDir, "core/syncthing")

    val installedVersion: String?
        get() = preferences.getString(KEY_VERSION, null)

    suspend fun install(uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = binaryFile.parentFile
            ?: throw IOException("无法创建核心目录")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建核心目录")
        }

        validateArm64Elf(uri)

        val candidate = File(directory, "syncthing.candidate")
        candidate.delete()
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            candidate.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法读取所选核心文件")

        try {
            Os.chmod(candidate.absolutePath, EXECUTABLE_MODE)
            val version = readVersion(candidate)
            try {
                Files.move(
                    candidate.toPath(),
                    binaryFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    candidate.toPath(),
                    binaryFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            Os.chmod(binaryFile.absolutePath, EXECUTABLE_MODE)
            preferences.edit { putString(KEY_VERSION, version) }
            version
        } catch (error: Exception) {
            candidate.delete()
            throw error
        }
    }

    private fun validateArm64Elf(uri: Uri) {
        val header = ByteArray(20)
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset < header.size) {
                throw IOException("核心文件过短，不是有效的 ELF 文件")
            }
        } ?: throw IOException("无法读取所选核心文件")

        val hasElfMagic =
            header[0] == 0x7F.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        if (!hasElfMagic) {
            throw IOException("所选文件不是 ELF 可执行文件")
        }
        if (header[4].toInt() != ELF_CLASS_64 || header[5].toInt() != ELF_LITTLE_ENDIAN) {
            throw IOException("核心必须是 64 位小端 ELF 文件")
        }

        val machine =
            (header[18].toInt() and 0xFF) or
                ((header[19].toInt() and 0xFF) shl 8)
        if (machine != ELF_MACHINE_AARCH64) {
            throw IOException("核心架构不是 arm64-v8a")
        }
    }

    private fun readVersion(file: File): String {
        val process = ProcessBuilder(file.absolutePath, "--version")
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = applicationContext.filesDir.absolutePath
                environment()["STNOUPGRADE"] = "1"
            }
            .start()
        if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("核心版本检查超时")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.exitValue() != 0 || output.isBlank()) {
            throw IOException("核心无法运行：${output.ifBlank { "未知错误" }}")
        }
        return output.lineSequence().first().take(MAX_VERSION_LENGTH)
    }

    companion object {
        private const val PREFERENCES = "core_binary"
        private const val KEY_VERSION = "version"
        private const val EXECUTABLE_MODE = 448 // 0700
        private const val ELF_CLASS_64 = 2
        private const val ELF_LITTLE_ENDIAN = 1
        private const val ELF_MACHINE_AARCH64 = 183
        private const val VERSION_TIMEOUT_SECONDS = 5L
        private const val MAX_VERSION_LENGTH = 160
    }
}
