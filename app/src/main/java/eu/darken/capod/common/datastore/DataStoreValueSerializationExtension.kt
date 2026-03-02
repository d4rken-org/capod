package eu.darken.capod.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@PublishedApi internal val TAG = logTag("DataStoreValue")

inline fun <reified T> serializationReader(
    json: Json,
    defaultValue: T,
    onErrorFallbackToDefault: Boolean = false,
): (Any?) -> T {
    val serializer: KSerializer<T> = serializer()
    return { rawValue ->
        val raw = rawValue as String?
        if (raw == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                json.decodeFromString(serializer, raw)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to decode, fallback to default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString(serializer, raw)
        }
    }
}

inline fun <reified T> serializationWriter(json: Json): (T) -> Any? {
    val serializer: KSerializer<T> = serializer()
    return { value: T ->
        value?.let { json.encodeToString(serializer, it) }
    }
}

inline fun <reified T> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T,
    json: Json,
    onErrorFallbackToDefault: Boolean = false,
): DataStoreValue<T> = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = serializationReader(json, defaultValue, onErrorFallbackToDefault),
    writer = serializationWriter(json),
)

fun <T> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T,
    json: Json,
    serializer: KSerializer<T>,
    onErrorFallbackToDefault: Boolean = false,
): DataStoreValue<T> = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = { rawValue ->
        val raw = rawValue as String?
        if (raw == null) {
            defaultValue
        } else if (onErrorFallbackToDefault) {
            try {
                json.decodeFromString(serializer, raw)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to decode, fallback to default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString(serializer, raw)
        }
    },
    writer = { value: T ->
        value?.let { json.encodeToString(serializer, it) }
    },
)
