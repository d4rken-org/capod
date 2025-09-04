package eu.darken.capod.common.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FlowPreference<T>(
    private val preferences: SharedPreferences,
    val key: String,
    val rawReader: (Any?) -> T,
    val rawWriter: (T) -> Any?
) {

    private val flowInternal = MutableStateFlow(value)
    val flow: Flow<T> = flowInternal

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, changedKey ->
            if (changedKey != key) return@OnSharedPreferenceChangeListener

            val newValue = rawReader(changedPrefs.all[key])

            val currentValue = flowInternal.value
            if (currentValue != newValue && flowInternal.compareAndSet(currentValue, newValue)) {
                log(VERBOSE) { "$changedPrefs:$changedKey changed to $newValue" }
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    var value: T
        get() = rawReader(valueRaw)
        set(newVal) {
            valueRaw = rawWriter(newVal)
        }

    var valueRaw: Any?
        get() = preferences.all[key] ?: rawWriter(rawReader(null))
        set(value) {
            preferences.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    null -> remove(key)
                    else -> throw NotImplementedError()
                }

            }
            flowInternal.value = rawReader(value)
        }

    fun update(update: (T) -> T) {
        value = update(value)
    }
}
