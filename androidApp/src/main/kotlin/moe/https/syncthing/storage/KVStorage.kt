package moe.https.syncthing.storage

import com.tencent.mmkv.MMKV

class MmkvAppSettingsStorage(
    private val mmkv: MMKV = requireNotNull(
        MMKV.mmkvWithID("app_settings"),
    ),
) : AppSettingPrivateStorage {
    override var appDeveloperMode: Boolean
        get() = mmkv.decodeBool(
            "APP_DEVELOPER_MODE",
            false,
        )
        set(value) {
            mmkv.encode(
                "APP_DEVELOPER_MODE",
                value,
            )
        }

    override var appProtocolStack: ProtocolStack
        get() = mmkv.decodeString(KEY_PROTOCOL_STACK)
            ?.let { storedValue ->
                ProtocolStack.entries.firstOrNull { it.name == storedValue }
            }
            ?: ProtocolStack.DUAL
        set(value) {
            mmkv.encode(KEY_PROTOCOL_STACK, value.name)
        }

    private companion object {
        const val KEY_PROTOCOL_STACK = "APP_PROTOCOL_STACK"
    }
}
