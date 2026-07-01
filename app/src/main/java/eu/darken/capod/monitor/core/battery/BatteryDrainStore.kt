package eu.darken.capod.monitor.core.battery

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

/**
 * Persists learned battery drain rates per profile so the time-remaining estimate survives app
 * restarts and is available immediately on reconnect. Mirrors [eu.darken.capod.monitor.core.cache.DeviceStateCache]:
 * one small JSON file per profile, guarded by a [Mutex], read/written on IO, broadcast via a
 * [StateFlow]. Per-profile files mean one corrupt write can only lose a single device's history.
 */
@Singleton
class BatteryDrainStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @SerializationCapod private val json: Json,
) {
    private val storeDir by lazy {
        File(context.filesDir, "battery_drain_rates").apply { mkdirs() }
    }
    private val lock = Mutex()

    private val _profiles = MutableStateFlow<Map<ProfileId, DrainProfile>>(emptyMap())
    val profiles: StateFlow<Map<ProfileId, DrainProfile>> = _profiles

    init {
        appScope.launch { loadAll() }
    }

    private suspend fun loadAll() = withContext(dispatcherProvider.IO) {
        lock.withLock {
            val dir = storeDir
            if (!dir.exists()) return@withLock

            val loaded = mutableMapOf<ProfileId, DrainProfile>()
            dir.listFiles()
                ?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
                ?.forEach { file ->
                    val profileId = file.name.removePrefix(PREFIX).removeSuffix(SUFFIX)
                    try {
                        loaded[profileId] = json.decodeFromString<DrainProfile>(file.readText())
                    } catch (e: Exception) {
                        log(TAG, Logging.Priority.ERROR) { "Failed to load ${file.name}: ${e.asLog()}, deleting" }
                        file.delete()
                    }
                }
            log(TAG, Logging.Priority.VERBOSE) { "loadAll(): loaded ${loaded.size} entries" }
            _profiles.value = loaded
        }
    }

    private fun ProfileId.toFile(): File = File(storeDir, "$PREFIX$this$SUFFIX")

    suspend fun save(id: ProfileId, profile: DrainProfile) = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "save(id=$id, rates=${profile.rates.keys})" }
            val file = id.toFile()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            try {
                tmp.writeText(json.encodeToString(DrainProfile.serializer(), profile))
                if (!tmp.renameTo(file)) {
                    // renameTo can fail across some filesystems; fall back to a direct write.
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
                _profiles.value += (id to profile)
            } catch (e: Exception) {
                log(TAG, Logging.Priority.ERROR) { "Failed to save $id: ${e.asLog()}" }
                tmp.delete()
            }
        }
    }

    suspend fun delete(id: ProfileId) = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "delete(id=$id)" }
            val file = id.toFile()
            if (file.exists() && !file.delete()) {
                // In-memory is cleared regardless; warn so a failed delete (which would resurrect the
                // rate on the next loadAll()) is at least visible rather than silently undoing a reset.
                log(TAG, Logging.Priority.ERROR) { "delete($id): failed to remove $file" }
            }
            _profiles.value -= id
        }
    }

    suspend fun deleteAll() = withContext(dispatcherProvider.IO) {
        lock.withLock {
            log(TAG, Logging.Priority.VERBOSE) { "deleteAll()" }
            storeDir.listFiles()?.forEach { it.delete() }
            _profiles.value = emptyMap()
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "BatteryDrainStore")
        private const val PREFIX = "drainrate_"
        private const val SUFFIX = ".json"
    }
}
