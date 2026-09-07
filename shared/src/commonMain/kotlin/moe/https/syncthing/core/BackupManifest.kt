package moe.https.syncthing.core

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val createdAt: String,
    val app: AppInfo,
    val core: CoreInfo,
) {
    @Serializable
    data class AppInfo(
        val applicationId: String,
        val versionCode: Long,
        val versionName: String,
    )

    @Serializable
    data class CoreInfo(
        val id: String,
        val source: String?,
        val version: String?,
        val commit: String?,
    )

    fun validateForImport(
        supportedFormatVersion: Int,
        currentApplicationId: String,
        currentVersionCode: Long,
    ) {
        require(formatVersion == supportedFormatVersion) {
            "不支持的备份格式版本：$formatVersion"
        }
        require(app.applicationId == currentApplicationId) {
            "备份属于其他应用：${app.applicationId.ifBlank { "未知" }}"
        }
        require(app.versionCode >= 0L) { "备份清单缺少 App 版本 ID" }
        require(app.versionCode <= currentVersionCode) {
            "该备份由更新版本的 App 创建，请先升级 App"
        }
        require(core.id.isNotBlank()) { "备份清单缺少内核版本信息" }
        require(!core.version.isNullOrBlank()) { "备份清单缺少内核版本信息" }
    }
}
