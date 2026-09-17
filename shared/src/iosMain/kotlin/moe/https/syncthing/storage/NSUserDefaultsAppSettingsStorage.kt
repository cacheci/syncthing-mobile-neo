package moe.https.syncthing.storage

import platform.Foundation.NSUserDefaults

class NSUserDefaultsAppSettingsStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppSettingPrivateStorage {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else defaultValue

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)

    override fun getInt(key: String, defaultValue: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else defaultValue

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)

    override fun getLong(key: String, defaultValue: Long): Long =
        if (contains(key)) defaults.objectForKey(key)?.toString()?.toLongOrNull() ?: defaultValue else defaultValue

    override fun putLong(key: String, value: Long) = defaults.setObject(value.toString(), key)

    override fun getFloat(key: String, defaultValue: Float): Float =
        if (contains(key)) defaults.floatForKey(key) else defaultValue

    override fun putFloat(key: String, value: Float) = defaults.setFloat(value, key)

    override fun getString(key: String, defaultValue: String?): String? =
        defaults.stringForKey(key) ?: defaultValue

    override fun putString(key: String, value: String?) {
        if (value == null) remove(key) else defaults.setObject(value, key)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        defaults.stringArrayForKey(key)?.filterIsInstance<String>()?.toSet() ?: defaultValue

    override fun putStringSet(key: String, value: Set<String>?) {
        if (value == null) remove(key) else defaults.setObject(value.toList(), key)
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}
