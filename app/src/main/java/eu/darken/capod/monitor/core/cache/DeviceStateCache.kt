package eu.darken.capod.monitor.core.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStateCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @SerializationCapod private val json: Json,
) {
    private val cacheDir by lazy {
        File(context.filesDir, "device_state_cache").apply { mkdirs() }
    }
    private val lock = Mutex()

    private val _cachedStates = MutableStateFlow<Map<ProfileId, CachedDeviceState>>(emptyMap())
    val cachedStates: StateFlow<Map<ProfileId, CachedDeviceState>> = _cachedStates

    init {
        appScope.launch { loadAll() }
    }

    private suspend fun loadAll() = withContext(dispatcherProvider.IO) {
        lock.withLock {
            val dir = cacheDir
            if (!dir.exists()) return@withLock

            val loaded = mutableMapOf<ProfileId, CachedDeviceState>()
            dir.listFiles()?.filter { it.name.startsWith("profile_") && it.name.endsWith(".json") }?.forEach { file ->
                val profileId = file.name.removePrefix("profile_").removeSuffix(".json")
                try {
                    val state = json.decodeFromString<CachedDeviceState>(file.readText())
                    loaded[profileId] = state
                } catch (e: Exception) {
                    log(
                        TAG,
                        Logging.Priority.ERROR
                    ) { "Failed to load cached state from ${file.name}: ${e.asLog()}, deleting" }
                    file.delete()
                }
            }
            log(TAG, Logging.Priority.VERBOSE) { "loadAll(): loaded ${loaded.size} entries" }
            _cachedStates.value = loaded
        }
    }

    private fun ProfileId.toCacheFile(): File = File(cacheDir, "profile_${this}.json")

    suspend fun save(id: ProfileId, state: CachedDeviceState) = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "save(id=$id)" }
            val file = id.toCacheFile()
            try {
                file.writeText(json.encodeToString(CachedDeviceState.serializer(), state))
                _cachedStates.value += (id to state)
            } catch (e: Exception) {
                log(TAG, Logging.Priority.ERROR) { "Failed to save state for $id: ${e.asLog()}" }
                file.delete()
            }
        }
    }

    suspend fun load(id: ProfileId): CachedDeviceState? = withContext(dispatcherProvider.IO) {
        lock.withLock {
            val cached = _cachedStates.value[id]
            if (cached != null) return@withContext cached

            val file = id.toCacheFile()
            if (!file.exists()) return@withContext null
            try {
                json.decodeFromString<CachedDeviceState>(file.readText())
            } catch (e: Exception) {
                log(TAG, Logging.Priority.ERROR) { "Failed to load state for $id: ${e.asLog()}, deleting" }
                file.delete()
                null
            }
        }
    }

    suspend fun delete(id: ProfileId) = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "delete(id=$id)" }
            id.toCacheFile().delete()
            _cachedStates.value -= id
        }
    }

    suspend fun deleteAll() = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "deleteAll()" }
            cacheDir.listFiles()?.forEach { it.delete() }
            _cachedStates.value = emptyMap()
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "DeviceStateCache")
    }
}