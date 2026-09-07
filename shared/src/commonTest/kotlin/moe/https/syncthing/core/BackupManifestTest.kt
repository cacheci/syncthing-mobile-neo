package moe.https.syncthing.core

import kotlin.test.Test
import kotlin.test.assertFailsWith

class BackupManifestTest {
    @Test
    fun rejectsBackupCreatedByNewerApp() {
        val manifest = manifest(versionCode = 11)

        assertFailsWith<IllegalArgumentException> {
            manifest.validateForImport(
                supportedFormatVersion = 1,
                currentApplicationId = "moe.https.syncthing",
                currentVersionCode = 10,
            )
        }
    }

    @Test
    fun rejectsUnsupportedFormatVersion() {
        val manifest = manifest(versionCode = 10).copy(formatVersion = 2)

        assertFailsWith<IllegalArgumentException> {
            manifest.validateForImport(
                supportedFormatVersion = 1,
                currentApplicationId = "moe.https.syncthing",
                currentVersionCode = 10,
            )
        }
    }

    private fun manifest(versionCode: Long) = BackupManifest(
        formatVersion = 1,
        createdAt = "2026-09-07T00:00:00Z",
        app = BackupManifest.AppInfo(
            applicationId = "moe.https.syncthing",
            versionCode = versionCode,
            versionName = "1.0",
        ),
        core = BackupManifest.CoreInfo(
            id = "builtin",
            source = "BUILT_IN",
            version = "syncthing v2.1.3",
            commit = "946e2b8",
        ),
    )
}
