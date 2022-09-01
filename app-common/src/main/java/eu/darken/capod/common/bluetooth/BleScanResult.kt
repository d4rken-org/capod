package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanResult
import android.os.Parcelable
import androidx.core.util.forEach
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class BleScanResult(
    @Json(name = "address") val address: String,
    @Json(name = "rssi") val rssi: Int,
    @Json(name = "generatedAtNanos") val generatedAtNanos: Long,
    @Json(name = "manufacturerSpecificData") val manufacturerSpecificData: Map<Int, ByteArray>
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
        fun fromScanResult(scanResult: ScanResult) = BleScanResult(
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