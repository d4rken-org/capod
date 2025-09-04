package eu.darken.capod.common.preferences

import androidx.preference.PreferenceDataStore

open class PreferenceStoreMapper(
    private vararg val flowPreferences: FlowPreference<*>
) : PreferenceDataStore() {

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw as Boolean
        } ?: throw NotImplementedError("getBoolean(key=$key, defValue=$defValue)")
    }

    override fun putBoolean(key: String, value: Boolean) {
        flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw = value
        } ?: throw NotImplementedError("putBoolean(key=$key, defValue=$value)")
    }

    override fun getString(key: String, defValue: String?): String? {
        val pref = flowPreferences.singleOrNull { it.key == key }
            ?: throw NotImplementedError("getString(key=$key, defValue=$defValue)")

        return pref.let { flowPref ->
            flowPref.valueRaw as String?
        }
    }

    override fun putString(key: String, value: String?) {
        val pref = flowPreferences.singleOrNull { it.key == key }
            ?: throw NotImplementedError("putString(key=$key, defValue=$value)")
        pref.let { flowPref ->
            flowPref.valueRaw = value
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw as Int
        } ?: throw NotImplementedError("getInt(key=$key, defValue=$defValue)")
    }

    override fun putInt(key: String?, value: Int) {
        flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw = value
        } ?: throw NotImplementedError("putInt(key=$key, defValue=$value)")
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw as Long
        } ?: throw NotImplementedError("getLong(key=$key, defValue=$defValue)")
    }

    override fun putLong(key: String?, value: Long) {
        flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw = value
        } ?: throw NotImplementedError("putLong(key=$key, defValue=$value)")
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw as Float
        } ?: throw NotImplementedError("getFloat(key=$key, defValue=$defValue)")
    }

    override fun putFloat(key: String?, value: Float) {
        flowPreferences.singleOrNull { it.key == key }?.let { flowPref ->
            flowPref.valueRaw = value
        } ?: throw NotImplementedError("putFloat(key=$key, defValue=$value)")
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        throw NotImplementedError("putStringSet(key=$key, defValue=$values)")
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> {
        throw NotImplementedError("getStringSet(key=$key, defValue=$defValues)")
    }
}