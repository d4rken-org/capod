package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.airpods.*
import eu.darken.capod.pods.core.apple.beats.*
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleFactory @Inject constructor(
    private val continuityProtocolDecoder: ContinuityProtocol.Decoder,
    private val proximityPairingDecoder: ProximityPairing.Decoder,
) {

    data class KnownDevice(
        val identifier: PodDevice.Id,
        val scanResult: BleScanResult,
        val message: ProximityPairing.Message,
    ) {
        val address: String
            get() = scanResult.address

        val rssi: Int
            get() = scanResult.rssi

        val timestampNanos: Duration
            get() = Duration.ofNanos(scanResult.generatedAtNanos)

        fun isOlderThan(age: Duration): Boolean {
            val now = Duration.ofNanos(SystemClockWrap.elapsedRealtimeNanos)
            return now - timestampNanos > age
        }
    }

    private val lock = Mutex()
    private val knownDevs = mutableMapOf<PodDevice.Id, KnownDevice>()

    data class ValueCache(
        val caseBatteryPercentage: Float?
    )

    private val cachedValues = mutableMapOf<PodDevice.Id, ValueCache>()

    private suspend fun getMessage(scanResult: BleScanResult): ProximityPairing.Message? {
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

    private suspend fun recognizeDevice(scanResult: BleScanResult, message: ProximityPairing.Message): PodDevice.Id {
        val address = scanResult.address

        var identifier: PodDevice.Id? = null

        knownDevs.values.firstOrNull { it.address == address }?.let {
            log(TAG, VERBOSE) { "recognizeDevice: Recovered previous ID via address: $it" }
            knownDevs[it.identifier] = it.copy(
                scanResult = scanResult,
                message = message,
            )
            identifier = it.identifier
        }

        if (identifier == null) {
            val currentMarkers = message.getRecogMarkers()
            knownDevs.values
                .firstOrNull { it.message.getRecogMarkers() == currentMarkers }
                ?.let {
                    log(TAG, DEBUG) { "recognizeDevice: Close match based similarity markers." }
                    log(TAG, DEBUG) { "recognizeDevice: Old marker: ${it.message.getRecogMarkers()}" }
                    log(TAG, DEBUG) { "recognizeDevice: New marker: $currentMarkers" }
                    knownDevs[it.identifier] = it.copy(
                        scanResult = scanResult,
                        message = message,
                    )
                    identifier = it.identifier
                }
        }

        if (identifier == null) {
            log(TAG, VERBOSE) { "recognizeDevice: Mapping as new device" }
            identifier = PodDevice.Id()
        }

        knownDevs[identifier!!] = KnownDevice(
            identifier = identifier!!,
            scanResult = scanResult,
            message = message
        )

        knownDevs.values.toList().forEach { knownDevice ->
            if (knownDevice.isOlderThan(Duration.ofSeconds(20))) {
                log(TAG, VERBOSE) { "recognizeDevice: Removing stale known device: $knownDevice" }
                knownDevs.remove(knownDevice.identifier)
            }
        }

        return identifier!!
    }

    suspend fun create(scanResult: BleScanResult): PodDevice? = lock.withLock {
        val pm = getMessage(scanResult) ?: return@withLock null

        val identifier = recognizeDevice(scanResult, pm)

        log(TAG, INFO) { "Decoding $scanResult" }

        return createSpecificDevice(scanResult, pm, identifier)
    }

    private fun createSpecificDevice(
        scanResult: BleScanResult,
        pm: ProximityPairing.Message,
        identifier: PodDevice.Id
    ): ApplePods {

        val cachedCaseBattery = cachedValues[identifier]?.caseBatteryPercentage

        val dm = (((pm.data[1].toInt() and 255) shl 8) or (pm.data[2].toInt() and 255)).toUShort()
        val dmDirty = pm.data[1]

        val specificDevice = when {
            dm == AirPodsGen1.DEVICE_CODE -> AirPodsGen1(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
                cachedBatteryPercentage = cachedCaseBattery,
            )
            dm == AirPodsGen2.DEVICE_CODE -> AirPodsGen2(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
                cachedBatteryPercentage = cachedCaseBattery,
            )
            dm == AirPodsGen3.DEVICE_CODE -> AirPodsGen3(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
                cachedBatteryPercentage = cachedCaseBattery,
            )
            dm == AirPodsPro.DEVICE_CODE -> AirPodsPro(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
                cachedBatteryPercentage = cachedCaseBattery,
            )
            dmDirty == PowerBeatsPro.DEVICE_CODE_DIRTY -> PowerBeatsPro(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
                cachedBatteryPercentage = cachedCaseBattery,
            )
            dmDirty == AirPodsMax.DEVICE_CODE_DIRTY -> AirPodsMax(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm,
            )
            dm == PowerBeats3.DEVICE_CODE -> PowerBeats3(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dm == BeatsSolo3.DEVICE_CODE -> BeatsSolo3(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dmDirty == BeatsStudio3.DEVICE_CODE_DIRTY -> BeatsStudio3(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dm == BeatsX.DEVICE_CODE -> BeatsX(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )
            dm == BeatsFlex.DEVICE_CODE -> BeatsFlex(
                identifier = identifier,
                scanResult = scanResult,
                proximityMessage = pm
            )

            else -> {
                Bugs.report(
                    tag = TAG,
                    message = "Unknown proximity message type",
                    exception = IllegalArgumentException("Unknown ProximityMessage: $pm")
                )
                UnknownAppleDevice(
                    identifier = identifier,
                    scanResult = scanResult,
                    proximityMessage = pm
                )
            }
        }

        cachedValues[identifier] = ValueCache(
            caseBatteryPercentage = (specificDevice as? HasCase)?.batteryCasePercent
        )

        return specificDevice
    }

    companion object {
        private val TAG = logTag("Pod", "AirPods", "Factory")
    }
}