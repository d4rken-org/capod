package eu.darken.capod.common.preferences

import android.content.SharedPreferences


inline fun <reified T> basicReader(defaultValue: T): (rawValue: Any?) -> T =
    { rawValue ->
        (rawValue ?: defaultValue) as T
    }

inline fun <reified T> basicWriter(): (T) -> Any? =
    { value ->
        when (value) {
            is Boolean -> value
            is String -> value
            is Int -> value
            is Long -> value
            is Float -> value
            null -> null
            else -> throw NotImplementedError()
        }
    }

inline fun <reified T : Any?> SharedPreferences.createFlowPreference(
    key: String,
    defaultValue: T = null as T
) = FlowPreference(
    preferences = this,
    key = key,
    rawReader = basicReader(defaultValue),
    rawWriter = basicWriter()
)

inline fun <reified T : Any?> SharedPreferences.createFlowPreference(
    key: String,
    noinline reader: (rawValue: Any?) -> T,
    noinline writer: (value: T) -> Any?
) = FlowPreference(
    preferences = this,
    key = key,
    rawReader = reader,
    rawWriter = writer
)
