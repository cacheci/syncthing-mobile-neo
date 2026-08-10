package moe.https.syncthing.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesAppSettingsStorage(
    context: Context,
) : AppSettingPrivateStorage {
    private val coreRuntimePreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(CORE_RUNTIME_PREFERENCES, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        coreRuntimePreferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        coreRuntimePreferences.edit { putBoolean(key, value) }
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        coreRuntimePreferences.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        coreRuntimePreferences.edit { putInt(key, value) }
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        coreRuntimePreferences.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        coreRuntimePreferences.edit { putLong(key, value) }
    }

    override fun getFloat(key: String, defaultValue: Float): Float =
        coreRuntimePreferences.getFloat(key, defaultValue)

    override fun putFloat(key: String, value: Float) {
        coreRuntimePreferences.edit { putFloat(key, value) }
    }

    override fun getString(key: String, defaultValue: String?): String? =
        coreRuntimePreferences.getString(key, defaultValue)

    override fun putString(key: String, value: String?) {
        coreRuntimePreferences.edit {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
        }
    }

    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        coreRuntimePreferences.getStringSet(key, defaultValue)?.toSet()

    override fun putStringSet(key: String, value: Set<String>?) {
        coreRuntimePreferences.edit {
            if (value == null) {
                remove(key)
            } else {
                putStringSet(key, value.toSet())
            }
        }
    }

    override fun contains(key: String): Boolean = coreRuntimePreferences.contains(key)

    override fun remove(key: String) {
        coreRuntimePreferences.edit { remove(key) }
    }

    private companion object {
        const val CORE_RUNTIME_PREFERENCES = "core_runtime"
    }
}
