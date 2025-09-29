package eu.darken.capod.monitor.core

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodDeviceCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    moshi: Moshi,
) {
    private val cacheDir by lazy {
        File(context.cacheDir, "device_cache").apply { mkdirs() }
    }
    private val jsonAdapter = moshi.adapter<BleScanResult>()
    private val lock = Mutex()

    private fun ProfileId.toCacheFile(): File = File(cacheDir, "profile_${this}.json")

    suspend fun load(id: ProfileId): BleScanResult? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "load(id=$id)" }
        val cacheFile = id.toCacheFile()
        lock.withLock {
            if (!cacheFile.exists()) return@withLock null
            try {
                val raw = cacheFile.readText()
                jsonAdapter.fromJson(raw)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read profile $id device: ${e.asLog()}, deleting corrupted cache file" }
                cacheFile.delete()
                null
            }
        }
    }

    suspend fun saveAll(data: Map<ProfileId, BleScanResult>) {
        log(TAG, VERBOSE) { "saveAll(): ${data.size} entries" }
        lock.withLock {
            data.forEach { (id, device) ->
                log(TAG, VERBOSE) { "save(id=$id, device=$device)" }
                val cacheFile = id.toCacheFile()
                try {
                    val json = jsonAdapter.toJson(device)
                    cacheFile.writeText(json)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to save profile $id device $device: ${e.asLog()}" }
                    cacheFile.delete()
                }
            }
        }
    }

    suspend fun delete(id: ProfileId) {
        log(TAG, VERBOSE) { "delete(): profileId=$id" }
        lock.withLock {
            val cacheFile = id.toCacheFile()
            try {
                cacheFile.delete()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to delete profile for $id: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor", "Cache")
    }
}

