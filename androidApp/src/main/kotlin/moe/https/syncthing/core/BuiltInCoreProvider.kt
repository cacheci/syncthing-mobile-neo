package moe.https.syncthing.core

import android.content.Context
import moe.https.syncthing.BuildConfig
import java.io.File
import java.io.IOException

internal class BuiltInCoreProvider(context: Context) {
    private val applicationContext = context.applicationContext

    private val binaryFile: File
        get() = File(
            applicationContext.applicationInfo.nativeLibraryDir,
            BINARY_FILE_NAME,
        )

    fun resolve(): BuiltInCoreExecutable {
        val file = binaryFile
        if (!file.isFile) {
            throw IOException("APK 安装目录中未找到内置 Syncthing 核心：${file.absolutePath}")
        }
        if (!file.canExecute()) {
            throw IOException("APK 安装目录中的内置 Syncthing 核心不可执行")
        }
        return BuiltInCoreExecutable(
            version = BuildConfig.SYNCTHING_VERSION,
            file = file,
        )
    }

    fun option(): CoreOption {
        val file = binaryFile
        val availability = when {
            !file.isFile -> CoreAvailability.MISSING
            !file.canExecute() -> CoreAvailability.EXECUTION_UNSUPPORTED
            else -> CoreAvailability.AVAILABLE
        }
        return CoreOption(
            id = CoreRegistry.BUILT_IN_ID,
            internal = true,
            version = BuildConfig.SYNCTHING_VERSION,
            source = CoreSource.BUILT_IN,
            availability = availability,
            unavailableReason = when (availability) {
                CoreAvailability.AVAILABLE -> null
                CoreAvailability.MISSING -> "APK 安装目录中缺少内置核心"
                CoreAvailability.EXECUTION_UNSUPPORTED -> "APK 安装目录中的核心不可执行"
            },
        )
    }

    companion object {
        private const val BINARY_FILE_NAME = "libsyncthingnative.so"
    }
}
