package moe.https.syncthing.core

data class SyncthingRecentChange(
    val id: Long,
    val time: String,
    val source: Source,
    val action: String,
    val itemType: String,
    val folderId: String,
    val folderLabel: String?,
    val path: String,
    val modifiedBy: String?,
) {
    enum class Source {
        LOCAL,
        REMOTE,
    }
}
