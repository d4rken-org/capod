package eu.darken.capod.pods.core.airpods

import android.bluetooth.le.ScanResult
import dagger.Reusable
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
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
import javax.inject.Inject

@Reusable
class AirPodsFactory @Inject constructor(
    private val continuityProtocolDecoder: ContinuityProtocol.Decoder,
    private val proximityPairingDecoder: ProximityPairing.Decoder
) {

    data class Message(
        // 0x07 ; AirPods message1 byte
        // 25; Length 1byte
        // 0x01
        val type: UByte,
        val deviceModel: UShort,
        val status: UByte,
        val batteryIndicator: UShort,
        val lidCount: UByte,
        val deviceColor: UByte,
        val encryptedData: UShort,
    )

    suspend fun create(scanResult: ScanResult): PodDevice? {
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
                ((proximityMessage.data[1].toInt() and 255) shl 8) or (proximityMessage.data[2].toInt() and 255)
                ).toUShort()
        val dmDirty = proximityMessage.data[1]

        return when {
            dm == 0x0220.toUShort() || dmDirty == 2.toUByte() -> AirPodsGen1(scanResult, proximityMessage)
            dm == 0x0F20.toUShort() || dmDirty == 15.toUByte() -> AirPodsGen2(scanResult, proximityMessage)
            dm == 0x0e20.toUShort() || dmDirty == 14.toUByte() -> AirPodsPro(scanResult, proximityMessage)
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
                Bugs.report(IllegalArgumentException("Unknown ProximityMessage: $proximityMessage"))
                UnknownAppleDevice(scanResult, proximityMessage)
            }
        }
    }

    companion object {
        private val TAG = logTag("Pod", "AirPods", "Factory")
    }
}