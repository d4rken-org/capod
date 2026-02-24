package eu.darken.capod.common.preferences

import android.content.SharedPreferences
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

inline fun <reified T> moshiReader(
    moshi: Moshi,
    defaultValue: T,
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> T {
    val adapter = moshi.adapter(T::class.java)
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                adapter.fromJson(rawValue) ?: defaultValue
            } catch (e: JsonDataException) {
                log(logTag("FlowPreference"), WARN) { "Failed to decode, fallback to default: ${e.message}" }
                defaultValue
            } catch (e: JsonEncodingException) {
                log(logTag("FlowPreference"), WARN) { "Failed to decode, fallback to default: ${e.message}" }
                defaultValue
            }
        } else {
            adapter.fromJson(rawValue) ?: defaultValue
        }
    }
}

inline fun <reified T> moshiWriter(
    moshi: Moshi,
): (T) -> Any? {
    val adapter = moshi.adapter(T::class.java)
    return { newValue: T ->
        newValue?.let { adapter.toJson(it) }
    }
}

inline fun <reified T : Any?> SharedPreferences.createFlowPreference(
    key: String,
    defaultValue: T = null as T,
    moshi: Moshi,
    onErrorFallbackToDefault: Boolean = false,
) = FlowPreference(
    preferences = this,
    key = key,
    rawReader = moshiReader(moshi, defaultValue, onErrorFallbackToDefault),
    rawWriter = moshiWriter(moshi)
)
