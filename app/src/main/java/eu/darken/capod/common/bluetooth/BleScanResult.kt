package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanResult
import android.os.Parcelable
import androidx.core.util.forEach
import kotlinx.parcelize.Parcelize

@Parcelize
data class BleScanResult(
    val address: String,
    val rssi: Int,
    val generatedAtNanos: Long,
    val manufacturerSpecificData: Map<Int, ByteArray>
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