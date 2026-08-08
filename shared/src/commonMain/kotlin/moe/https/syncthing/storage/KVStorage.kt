package moe.https.syncthing.storage

interface AppSettingPrivateStorage {
    var appDeveloperMode: Boolean
    var appProtocolStack: ProtocolStack
}

enum class ProtocolStack(
    val displayName: String,
    val guiListenAddress: String,
) {
    IPV4("IPv4", "127.0.0.1"),
    IPV6("IPv6", "::1"),
    DUAL("双栈", "localhost"),
}
