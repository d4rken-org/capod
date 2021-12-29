package eu.darken.capod.pods.core.airpods

import android.bluetooth.le.ScanFilter
import dagger.Reusable
import eu.darken.capod.common.debug.logging.log
import javax.inject.Inject


object ProximityPairing {

    data class Message(
        val type: UByte,
        val length: Int,
        val data: UByteArray
    )

    @Reusable
    class Decoder @Inject constructor() {
        fun decode(message: ContinuityProtocol.Message): Message? {
            if (message.type != CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING) {
                log(ApplePods.TAG) { "Not a proximity pairing message: $this" }
                return null
            }
            if (message.length != PROXIMITY_PAIRING_MESSAGE_LENGTH) {
                log(ApplePods.TAG) { "Proximity pairing message has invalid length." }
                return null
            }

            return Message(
                type = message.type,
                length = message.length,
                data = message.data
            )
        }
    }

    fun getBleScanFilter(): Set<ScanFilter> {
        val manufacturerData = ByteArray(CONTINUITY_PROTOCOL_MESSAGE_LENGTH).apply {
            this[0] = CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING.toByte()
            this[1] = PROXIMITY_PAIRING_MESSAGE_LENGTH.toByte()
        }

        val manufacturerDataMask = ByteArray(CONTINUITY_PROTOCOL_MESSAGE_LENGTH).apply {
            this[0] = 1
            this[1] = 1
        }
        val builder = ScanFilter.Builder().apply {
            setManufacturerData(
                ContinuityProtocol.APPLE_COMPANY_IDENTIFIER,
                manufacturerData,
                manufacturerDataMask
            )
        }
        return setOf(builder.build())
    }

    private const val CONTINUITY_PROTOCOL_MESSAGE_LENGTH = 27
    private val CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING = 0x07.toUByte()
    private const val PROXIMITY_PAIRING_MESSAGE_LENGTH = 25
}
