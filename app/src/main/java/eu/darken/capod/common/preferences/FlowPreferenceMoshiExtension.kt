package eu.darken.capod.common.preferences

import android.content.SharedPreferences
import com.squareup.moshi.Moshi

inline fun <reified T> moshiReader(
    moshi: Moshi,
    defaultValue: T,
): (Any?) -> T {
    val adapter = moshi.adapter(T::class.java)
    return { rawValue ->
        rawValue as String?
        rawValue?.let { adapter.fromJson(it) } ?: defaultValue
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
) = FlowPreference(
    preferences = this,
    key = key,
    rawReader = moshiReader(moshi, defaultValue),
    rawWriter = moshiWriter(moshi)
)