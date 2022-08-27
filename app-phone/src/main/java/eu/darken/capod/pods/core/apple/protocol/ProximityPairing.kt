package eu.darken.capod.pods.core.apple.protocol

import android.bluetooth.le.ScanFilter
import dagger.Reusable
import eu.darken.capod.common.debug.logging.log
import javax.inject.Inject


object ProximityPairing {

    data class Message(
        val type: UByte,
        val length: Int,
        val data: UByteArray
    ) {

        override fun toString(): String {
            val dataHex = data.joinToString(separator = " ") { String.format("%02X", it.toByte()) }
            return "ProximityPairing.Message(type=$type, length=$length, data=$dataHex)"
        }
    }

    @Reusable
    class Decoder @Inject constructor() {
        fun decode(message: ContinuityProtocol.Message): Message? {
            if (message.type != CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING) {
                log { "Not a proximity pairing message: $this" }
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
            this[1] = PAIRING_MESSAGE_LENGTH.toByte()
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
    internal val CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING = 0x07.toUByte()

    // This is the default message length among official Apple devices, clones may have different length
    internal const val PAIRING_MESSAGE_LENGTH = 25
}
