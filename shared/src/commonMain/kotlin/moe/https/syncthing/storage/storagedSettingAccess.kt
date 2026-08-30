package moe.https.syncthing.storage

interface AppSettingPrivateStorage {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)

    fun getFloat(key: String, defaultValue: Float): Float
    fun putFloat(key: String, value: Float)

    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String?)

    fun getStringSet(key: String, defaultValue: Set<String>? = null): Set<String>?
    fun putStringSet(key: String, value: Set<String>?)

    fun contains(key: String): Boolean
    fun remove(key: String)

    companion object {
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val KEY_AUTO_START_MODE = "auto_start_mode"
        const val KEY_AUTO_START_CONDITION = "auto_start_condition"
        const val KEY_PROTOCOL_STACK = "protocol_stack"
        const val KEY_LISTEN_PREFERENCE = "listen_prefer"
        const val KEY_DISCOVERY_PREFERENCE = "discovery_prefer"
    }
}
