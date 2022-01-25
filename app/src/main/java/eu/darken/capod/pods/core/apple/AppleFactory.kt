package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.toBinaryString
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.airpods.*
import eu.darken.capod.pods.core.apple.beats.*
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleFactory @Inject constructor(
    private val continuityProtocolDecoder: ContinuityProtocol.Decoder,
    private val proximityPairingDecoder: ProximityPairing.Decoder,
    private val podFactories: @JvmSuppressWildcards Set<ApplePods.Factory>,
    private val unknownAppleFactory: UnknownAppleDevice.Factory,
) {

    private val lock = Mutex()

    private fun getMessage(scanResult: BleScanResult): ProximityPairing.Message? {
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

    suspend fun create(scanResult: BleScanResult): PodDevice? = lock.withLock {
        val pm = getMessage(scanResult) ?: return@withLock null

        log(TAG, INFO) { "Decoding $scanResult" }

        val factory = podFactories.firstOrNull { it.isResponsible(pm) }

        val device = (factory ?: unknownAppleFactory).create(
            scanResult = scanResult,
            proximityMessage = pm,
        )
        if (device is DualApplePods) {
            device.apply {
                log(
                    TAG,
                    WARN
                ) { "${rssi} ${scanResult.address} ${rawStatus.upperNibble.toBinaryString()} ${rawStatus.lowerNibble.toBinaryString()} primary=$primaryPod isCasePod=$isThisPodInThecase" }
            }
        }

        if (factory == null && !SILENCED_PMS.contains(device.rawDeviceModel) && scanResult.address != "6E:9E:D1:49:D2:6D") {
            SILENCED_PMS.add(device.rawDeviceModel)
            Bugs.report(
                tag = TAG,
                message = "Unknown proximity message type",
                exception = IllegalArgumentException("Unknown ProximityMessage: $pm")
            )
        }

        return@withLock device
    }

    companion object {
        private val SILENCED_PMS = mutableSetOf<UShort>()
        private val TAG = logTag("Pod", "Apple", "Factory")
    }
}