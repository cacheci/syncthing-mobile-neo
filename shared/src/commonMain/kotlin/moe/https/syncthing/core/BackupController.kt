package moe.https.syncthing.core

enum class BackupImportFormat {
    CURRENT,
    LEGACY,
}

interface BackupController {
    suspend fun exportBackup(destinationUri: String, password: String?)

    suspend fun importBackup(
        sourceUri: String,
        password: String?,
        format: BackupImportFormat,
    )
}
