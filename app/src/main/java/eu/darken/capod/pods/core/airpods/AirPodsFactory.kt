package eu.darken.capod.pods.core.airpods

import android.bluetooth.le.ScanResult
import android.os.SystemClock
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.airpods.models.AirPodsGen1
import eu.darken.capod.pods.core.airpods.models.AirPodsGen2
import eu.darken.capod.pods.core.airpods.models.AirPodsPro
import eu.darken.capod.pods.core.airpods.models.UnknownAppleDevice
import eu.darken.capod.pods.core.airpods.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirPodsFactory @Inject constructor(
    private val continuityProtocolDecoder: ContinuityProtocol.Decoder,
    private val proximityPairingDecoder: ProximityPairing.Decoder
) {

    data class KnownDevice(
        val identifier: UUID,
        val scanResult: ScanResult
    ) {
        val address: String
            get() = scanResult.device.address

        val rssi: Int
            get() = scanResult.rssi

        val timestampNanos: Duration
            get() = Duration.ofNanos(scanResult.timestampNanos)

        fun isOlderThan(age: Duration): Boolean {
            val now = Duration.ofNanos(SystemClock.elapsedRealtimeNanos())
            return now - timestampNanos > age
        }
    }

    private val knownDevs = mutableMapOf<UUID, KnownDevice>()
    private val mutex = Mutex()

    private suspend fun getMessage(scanResult: ScanResult): ProximityPairing.Message? {
        val messages = try {
            continuityProtocolDecoder.decode(scanResult)
        } catch (e: Exception) {
            log(TAG, WARN) { "Data wasn't continuity protocol conform:\n${e.asLog()}" }
            return null
        }
        if (messages.isEmpty()) {
            log(TAG, WARN) { "Data contained no continuity messages: $scanResult" }
            return null
        }

        if (messages.size > 1) {
            log(TAG, WARN) { "Decoded multiple continuity messages, picking first: $messages" }
        }

        val proximityMessage = proximityPairingDecoder.decode(messages.first())
        if (proximityMessage == null) {
            log(TAG) { "Not a proximity pairing message: $messages" }
            return null
        }

        return proximityMessage
    }

    private suspend fun recognizeDevice(scanResult: ScanResult): UUID {
        val address = scanResult.device.address

        var identifier: UUID? = null

        knownDevs.values.firstOrNull { it.address == address }?.let {
            log(TAG, VERBOSE) { "recognizeDevice: Recovered previous ID via address: $it" }
            knownDevs[it.identifier] = it.copy(scanResult = scanResult)
            identifier = it.identifier
        }

        if (identifier == null) {
            knownDevs.values
                .firstOrNull {
                    it.rssi > -60 && !it.isOlderThan(Duration.ofSeconds(10))
                }
                ?.let {
                    log(TAG, VERBOSE) { "recognizeDevice: Close match based on RSSI and timestamp." }
                    knownDevs[it.identifier] = it.copy(scanResult = scanResult)
                    identifier = it.identifier
                }
        }


        if (identifier == null) {
            log(TAG, VERBOSE) { "recognizeDevice: Mapping as new device" }
            identifier = UUID.randomUUID()
        }

        knownDevs[identifier!!] = KnownDevice(
            identifier = identifier!!,
            scanResult = scanResult
        )

        knownDevs.values.toList().forEach { knownDevice ->
            if (knownDevice.isOlderThan(Duration.ofSeconds(20))) {
                log(TAG, VERBOSE) { "recognizeDevice: Removing stale known device: $knownDevice" }
                knownDevs.remove(knownDevice.identifier)
            }
        }

        return identifier!!
    }

    suspend fun create(scanResult: ScanResult): PodDevice? = mutex.withLock {
        val pm = getMessage(scanResult) ?: return@withLock null

        val identifier = recognizeDevice(scanResult)

        log(TAG, INFO) {
            val data = scanResult.scanRecord!!.getManufacturerSpecificData(
                ContinuityProtocol.APPLE_COMPANY_IDENTIFIER
            )!!
            val dataHex = data.joinToString(separator = " ") {
                String.format("%02X", it)
            }
            "Decoding (MAC=${scanResult.device.address}, nanos=${scanResult.timestampNanos}, rssi=${scanResult.rssi}): $dataHex"
        }

        val dm = (
                ((pm.data[1].toInt() and 255) shl 8) or (pm.data[2].toInt() and 255)
                ).toUShort()
        val dmDirty = pm.data[1]

        return when {
            dm == 0x0220.toUShort() || dmDirty == 2.toUByte() -> AirPodsGen1(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dm == 0x0F20.toUShort() || dmDirty == 15.toUByte() -> AirPodsGen2(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dm == 0x0e20.toUShort() || dmDirty == 14.toUByte() -> AirPodsPro(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
//            dmDirty == 10.toUByte() -> {
//                TODO("Airpods Max")
//            }
//            dmDirty == 11.toUByte() -> {
//                TODO("PowerBeatsPro")
//            }
//            dm == 0x0520.toUShort() || dmDirty == 5.toUByte() -> {
//                TODO("BeatsX")
//            }
//            dmDirty == 0.toUByte() -> {
//                TODO("BeatsFlex")
//            }
//            dm == 0x0620.toUShort() || dmDirty == 6.toUByte() -> {
//                TODO("Beats Solo 3")
//            }
//            dmDirty == 9.toUByte() -> {
//                TODO("Beats Studio 3")
//            }
//            dm == 0x0320.toUShort() || dmDirty == 3.toUByte() -> {
//                TODO("Power Beats 3")
//            }
            else -> {
                log(TAG, WARN) { "Unknown proximity message type" }
                Bugs.report(IllegalArgumentException("Unknown ProximityMessage: $pm"))
                UnknownAppleDevice(
                    identifier = identifier,
                    scanResult = scanResult,
                    proximityMessage = pm
                )
            }
        }
    }

    companion object {
        private val TAG = logTag("Pod", "AirPods", "Factory")
    }
}