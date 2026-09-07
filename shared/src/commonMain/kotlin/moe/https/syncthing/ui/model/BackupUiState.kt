package moe.https.syncthing.ui.model

import moe.https.syncthing.core.BackupImportFormat

data class PendingBackupImport(
    val sourceUri: String,
    val format: BackupImportFormat,
)

data class BackupUiState(
    val isWorking: Boolean = false,
    val pendingImport: PendingBackupImport? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface BackupUiEffect {
    data class CreateDocument(val suggestedFileName: String) : BackupUiEffect
    data class OpenDocument(val format: BackupImportFormat) : BackupUiEffect
}
