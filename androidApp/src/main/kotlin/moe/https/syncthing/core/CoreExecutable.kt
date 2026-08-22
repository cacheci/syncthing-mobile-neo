package moe.https.syncthing.core

import java.io.File

internal sealed interface CoreExecutable {
    val id: String
    val version: String
    val file: File
    val source: CoreSource
}

internal data class BuiltInCoreExecutable(
    override val version: String,
    override val file: File,
) : CoreExecutable {
    override val id: String = CoreRegistry.BUILT_IN_ID
    override val source: CoreSource = CoreSource.BUILT_IN
}

internal data class ExternalCoreExecutable(
    override val id: String,
    override val version: String,
    override val file: File,
    val importedAtMillis: Long,
    val sha256: String,
) : CoreExecutable {
    override val source: CoreSource = CoreSource.EXTERNAL
}
