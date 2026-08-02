package moe.https.syncthing.core

enum class CoreLogSource {
    SYNCTHING,
    LAUNCHER,
    CONTROLLER,
}

data class CoreLogContent(
    val text: String,
    val refreshedAt: String,
)

interface CoreLogReader {
    suspend fun read(source: CoreLogSource): CoreLogContent
}
