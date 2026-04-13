package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanResult
import android.os.Parcelable
import androidx.core.util.forEach
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.common.serialization.MapIntByteArrayBase64Serializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Parcelize
@Serializable
data class BleScanResult(
    @SerialName("receivedAt") @Serializable(with = InstantEpochMillisSerializer::class) val receivedAt: Instant,
    @SerialName("address") val address: String,
    @SerialName("rssi") val rssi: Int,
    @SerialName("generatedAtNanos") val generatedAtNanos: Long,
    @SerialName("manufacturerSpecificData") @Serializable(with = MapIntByteArrayBase64Serializer::class) val manufacturerSpecificData: Map<Int, ByteArray>
) : Parcelable {

    fun getManufacturerSpecificData(id: Int): ByteArray? = manufacturerSpecificData[id]

    override fun toString(): String {
        val sb = StringBuilder()
        manufacturerSpecificData.forEach { (key, value) ->
            sb.append("$key: ${value.joinToString(separator = " ") { String.format("%02X", it) }}")
        }
        return "BleScanResult($rssi, $address, $generatedAtNanos, $sb"
    }

    companion object {
        fun fromScanResult(
            scanResult: ScanResult,
            timeSource: TimeSource = SystemTimeSource,
        ) = BleScanResult(
            receivedAt = timeSource.now(),
            address = scanResult.device.address,
            rssi = scanResult.rssi,
            generatedAtNanos = scanResult.timestampNanos,
            manufacturerSpecificData = mutableMapOf<Int, ByteArray>().apply {
                scanResult.scanRecord?.manufacturerSpecificData?.forEach { key, value ->
                    this[key] = value
                }
            }
        )
    }
}
