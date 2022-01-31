package eu.darken.capod.pods.core.unknown

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnknownDeviceFactory @Inject constructor() {

    private val lock = Mutex()

    suspend fun create(scanResult: BleScanResult): PodDevice? = lock.withLock {
        var basic = UnknownDevice(
            identifier = PodDevice.Id(),
            scanResult = scanResult,
        )
        val result = searchHistory(basic)

        if (result != null) basic = basic.copy(identifier = result.id)
        updateHistory(basic)

        if (result == null) return basic

        return basic.copy(
            identifier = result.id,
            seenFirstAt = result.seenFirstAt,
            seenCounter = result.seenCounter,
            confidence = result.confidence,
            rssiAverage = result.averageRssi(basic.rssi),
        )
    }

    data class KnownDevice(
        val id: PodDevice.Id,
        val seenFirstAt: Instant,
        val seenCounter: Int,
        val history: List<PodDevice>
    ) {

        val lastAddress: String
            get() = history.last().address

        val confidence: Float
            get() = 0.75f + (history.size / (MAX_HISTORY * 4f))

        fun averageRssi(latest: Int): Int =
            history.map { it.rssi }.plus(latest).takeLast(10).median()

        fun isOlderThan(age: Duration): Boolean {
            val now = Instant.now()
            return Duration.between(history.last().seenLastAt, now) > age
        }

        private fun List<Int>.median(): Int = this.sorted().let {
            if (it.size % 2 == 0)
                (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
            else
                it[it.size / 2]
        }

        override fun toString(): String = "KnownDevice(history=${history.size}, last=${history.last()})"

        companion object {
            const val MAX_HISTORY = 10
        }
    }

    private val knownDevices = mutableMapOf<PodDevice.Id, KnownDevice>()

    private fun searchHistory(current: PodDevice): KnownDevice? {
        val scanResult = current.scanResult

        knownDevices.values.toList().forEach { knownDevice ->
            if (knownDevice.isOlderThan(Duration.ofSeconds(20))) {
                log(TAG, VERBOSE) { "searchHistory1: Removing stale known device: $knownDevice" }
                knownDevices.remove(knownDevice.id)
            }
        }

        knownDevices.values
            .filter { it.history.size > KnownDevice.MAX_HISTORY }
            .toList()
            .forEach {
                knownDevices[it.id] = it.copy(history = it.history.takeLast(KnownDevice.MAX_HISTORY))
            }

        val recognizedDevice: KnownDevice? = knownDevices.values
            .firstOrNull { it.lastAddress == scanResult.address }
            ?.also { log(TAG, VERBOSE) { "searchHistory1: Recovered previous ID via address: $it" } }

        if (recognizedDevice == null) {
            log(TAG) { "searchHistory1: Didn't recognize: $current" }
        }

        return recognizedDevice
    }

    private fun updateHistory(device: PodDevice) {
        val existing = knownDevices[device.identifier]

        knownDevices[device.identifier] = when {
            existing != null -> {
                existing.copy(
                    seenCounter = existing.seenCounter + 1,
                    history = existing.history.plus(device)
                )
            }
            else -> {
                log(TAG) { "searchHistory1: Creating new history for $device" }
                KnownDevice(
                    id = device.identifier,
                    seenFirstAt = device.seenFirstAt,
                    seenCounter = 1,
                    history = listOf(device)
                )
            }
        }
    }

    companion object {
        private val TAG = logTag("Pod", "Unknown", "Factory")
    }
}