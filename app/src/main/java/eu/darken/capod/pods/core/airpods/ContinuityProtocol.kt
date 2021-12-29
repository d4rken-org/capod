package eu.darken.capod.pods.core.airpods

import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import dagger.Reusable
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject

object ContinuityProtocol {

    data class Message(
        val type: UByte,
        val length: Int,
        val data: UByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Message) return false

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    @Reusable
    class Decoder @Inject constructor() {
        fun decode(scanResult: ScanResult): List<Message> = scanResult.scanRecord
            ?.getManufacturerSpecificData(APPLE_COMPANY_IDENTIFIER)
            ?.let { data ->
                val messages = mutableListOf<Message>()

                var remainingData = data.asUByteArray()
                while (remainingData.size >= 2) {
                    val dataLength = remainingData[1].toInt()
                    val dataStart = 2
                    val dataEnd = dataStart + dataLength
                    Message(
                        type = remainingData[0],
                        length = dataLength,
                        data = remainingData.copyOfRange(dataStart, dataEnd)
                    ).run { messages.add(this) }

                    remainingData = remainingData.copyOfRange(dataEnd, remainingData.size)
                }
                if (remainingData.isNotEmpty()) {
                    log(TAG, WARN) { "Data contained malformed protocol message $remainingData" }
                }
                messages.toList()
            } ?: emptyList()
    }

    const val APPLE_COMPANY_IDENTIFIER = 0x004C

    // Continuity protocol data is in these vendor specific data sets
    val BLE_FEATURE_UUIDS = setOf(
        ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"),
        ParcelUuid.fromString("2a72e02b-7b99-778f-014d-ad0b7221ec74")
    )

    val TAG = logTag("ContinuityProtocol", "Decoder")
}