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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class PodDeviceCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    moshi: Moshi,
) {
    private val cacheDir by lazy {
        File(context.cacheDir, "device_cache").apply { mkdirs() }
    }
    private val mainDeviceCacheFile = File(cacheDir, "main_device.raw")
    private val jsonAdapter = moshi.adapter<BleScanResult>()
    private val lock = Mutex()

    suspend fun saveMainDevice(device: BleScanResult?) = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "saveMainDevice(device=$device)" }
        lock.withLock {
            try {
                if (device == null) {
                    mainDeviceCacheFile.delete()
                } else {
                    val json = jsonAdapter.toJson(device)
                    mainDeviceCacheFile.writeText(json)
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to save $device:${e.asLog()}" }
            }
        }
    }

    suspend fun loadMainDevice(): BleScanResult? = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "loadMainDevice()" }
        lock.withLock {
            if (!mainDeviceCacheFile.exists()) return@withLock null
            try {
                val raw = mainDeviceCacheFile.readText()
                jsonAdapter.fromJson(raw)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read main-device:${e.asLog()}" }
                null
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor", "Cache")
    }
}

