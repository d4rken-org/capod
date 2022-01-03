package eu.darken.capod.common.preferences

import androidx.preference.PreferenceDataStore

abstract class PreferenceStoreMapper : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        throw NotImplementedError("getBoolean(key=$key, defValue=$defValue)")
    }

    override fun putBoolean(key: String, value: Boolean) {
        throw NotImplementedError("putBoolean(key=$key, defValue=$value)")
    }

    override fun getString(key: String, defValue: String?): String? {
        throw NotImplementedError("getString(key=$key, defValue=$defValue)")
    }

    override fun putString(key: String, value: String?) {
        throw NotImplementedError("putString(key=$key, defValue=$value)")
    }

    override fun putInt(key: String?, value: Int) {
        throw NotImplementedError("putInt(key=$key, defValue=$value)")
    }

    override fun getInt(key: String?, defValue: Int): Int {
        throw NotImplementedError("getInt(key=$key, defValue=$defValue)")
    }

    override fun putLong(key: String?, value: Long) {
        throw NotImplementedError("putLong(key=$key, defValue=$value)")
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        throw NotImplementedError("putStringSet(key=$key, defValue=$values)")
    }

    override fun getLong(key: String?, defValue: Long): Long {
        throw NotImplementedError("getLong(key=$key, defValue=$defValue)")
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        throw NotImplementedError("getFloat(key=$key, defValue=$defValue)")
    }

    override fun putFloat(key: String?, value: Float) {
        throw NotImplementedError("putFloat(key=$key, defValue=$value)")
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> {
        throw NotImplementedError("getStringSet(key=$key, defValue=$defValues)")
    }
}