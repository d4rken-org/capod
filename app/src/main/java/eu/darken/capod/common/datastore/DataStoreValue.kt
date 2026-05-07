package eu.darken.capod.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class DataStoreValue<T>(
    private val dataStore: DataStore<Preferences>,
    val key: Preferences.Key<*>,
    private val reader: (Any?) -> T,
    private val writer: (T) -> Any?,
) {
    val keyName: String get() = key.name

    val flow: Flow<T> = dataStore.data
        .map { prefs -> prefs[key] }
        .distinctUntilChanged()
        .map { raw -> reader(raw) }

    data class Updated<T>(val old: T, val new: T)

    suspend fun update(transform: (T) -> T): Updated<T> {
        var old: T? = null
        var new: T? = null
        dataStore.edit { prefs ->
            old = reader(prefs[key])
            new = transform(old as T)
            val raw = writer(new as T)
            if (raw == null) {
                prefs.remove(key)
            } else {
                @Suppress("UNCHECKED_CAST")
                prefs[key as Preferences.Key<Any>] = raw
            }
        }
        log(VERBOSE) { "DataStoreValue($keyName) updated from $old to $new" }
        @Suppress("UNCHECKED_CAST")
        return Updated(old as T, new as T)
    }
}

suspend fun <T> DataStoreValue<T>.value(): T = flow.first()

suspend fun <T> DataStoreValue<T>.value(value: T) = update { value }

var <T> DataStoreValue<T>.valueBlocking: T
    get() = runBlocking { flow.first() }
    set(value) = runBlocking { update { value } }

inline fun <reified T> basicKey(key: String, defaultValue: T): Preferences.Key<*> = when (defaultValue) {
    is Boolean -> booleanPreferencesKey(key)
    is String -> stringPreferencesKey(key)
    is Int -> intPreferencesKey(key)
    is Long -> longPreferencesKey(key)
    is Float -> floatPreferencesKey(key)
    else -> throw IllegalArgumentException("Unsupported type for basicKey: ${T::class}")
}

inline fun <reified T> basicReader(defaultValue: T): (Any?) -> T = { raw ->
    @Suppress("UNCHECKED_CAST")
    (raw as? T) ?: defaultValue
}

inline fun <reified T> basicWriter(): (T) -> Any? = { value -> value }

inline fun <reified T> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T,
): DataStoreValue<T> = DataStoreValue(
    dataStore = this,
    key = basicKey(key, defaultValue),
    reader = basicReader(defaultValue),
    writer = basicWriter(),
)

fun <T> DataStore<Preferences>.createValue(
    key: Preferences.Key<*>,
    reader: (Any?) -> T,
    writer: (T) -> Any?,
): DataStoreValue<T> = DataStoreValue(
    dataStore = this,
    key = key,
    reader = reader,
    writer = writer,
)
