package moe.https.syncthing.core

data class SyncthingFolder(
    val id: String,
    val label: String?,
    val group: String,
    val path: String,
    val type: String,
    val paused: Boolean,
    val fsWatcherEnabled: Boolean,
    val rescanIntervalSeconds: Int,
    val versioning: NewFolderConfiguration.Versioning,
    val versioningSupported: Boolean,
    val versioningFsPath: String,
    val versioningCleanoutDays: Int,
    val versioningKeep: Int,
    val versioningCleanupIntervalSeconds: Int,
    val ignorePatterns: List<String>,
    val ignoreError: String?,
    val devices: List<FolderDeviceConfiguration>,
    val state: String,
    val localFiles: Long,
    val localBytes: Long,
    val needFiles: Long,
    val needBytes: Long,
    val pullErrors: Long,
)

data class SyncthingPendingFolder(
    val id: String,
    val name: String,
    val source: String,
    val sourceName: String,
    val detectedAt: String?,
)

data class FolderDeviceConfiguration(
    val deviceId: String,
    val encryptionPassword: String,
)

data class NewFolderConfiguration(
    val folderId: String,
    val label: String,
    val group: String,
    val path: String,
    val versioning: Versioning,
    val updateVersioning: Boolean,
    val versioningFsPath: String,
    val versioningCleanoutDays: Int,
    val versioningKeep: Int,
    val versioningCleanupIntervalSeconds: Int,
    val ignorePatterns: List<String>,
    val updateIgnorePatterns: Boolean,
    val fsWatcherEnabled: Boolean,
    val rescanIntervalSeconds: Int,
    val type: Type,
    val devices: List<FolderDeviceConfiguration>,
    val availableDeviceIds: Set<String>,
) {
    enum class Versioning {
        NONE,
        TRASHCAN,
        SIMPLE,
    }

    enum class Type {
        SEND_RECEIVE,
        RECEIVE_ONLY,
        SEND_ONLY,
    }
}

data class FoldersSnapshot(
    val folders: List<SyncthingFolder>,
    val pendingFolders: List<SyncthingPendingFolder>,
)

fun defaultFolderPath(folderId: String): String =
    "~/syncfolders/${encodeFolderIdForPath(folderId)}"

fun encodeFolderIdForPath(folderId: String): String = buildString {
    folderId.forEach { character ->
        if (
            character in 'A'..'Z' ||
            character in 'a'..'z' ||
            character in '0'..'9' ||
            character == '_' ||
            character == '-'
        ) {
            append(character)
        } else {
            append("=u")
            append(character.code.toString(16).uppercase().padStart(4, '0'))
        }
    }
}
