package eu.darken.capod.common.bluetooth

import android.bluetooth.le.ScanResult
import androidx.core.util.forEach

fun BluetoothAddress.redactedForLogs(): String {
    val parts = split(':')
    if (parts.size < 2) return takeLast(5)
    return "XX:XX:XX:XX:${parts.takeLast(2).joinToString(":")}"
}

fun BleScanResult.logSummary(): String {
    val payloadSummary = manufacturerSpecificData.entries
        .sortedBy { it.key }
        .joinToString(separator = ",") { (manufacturerId, data) -> "$manufacturerId:${data.size}B" }
        .ifEmpty { "-" }
    return "addr=${address.redactedForLogs()}, rssi=$rssi, payloads=[$payloadSummary]"
}

@JvmName("logBleScanResultCollectionSummary")
fun Collection<BleScanResult>.logSummary(limit: Int = 3): String {
    if (isEmpty()) return "count=0"
    val sample = take(limit).joinToString(separator = ", ") { it.logSummary() }
    val suffix = if (size > limit) ", ..." else ""
    return "count=$size, sample=[$sample$suffix]"
}

fun ScanResult.logSummary(): String {
    val payloadSummary = buildList {
        scanRecord?.manufacturerSpecificData?.forEach { manufacturerId, data ->
            add("$manufacturerId:${data.size}B")
        }
    }
        .sorted()
        .joinToString(separator = ",")
        .ifEmpty { "-" }
    return "addr=${device.address.redactedForLogs()}, rssi=$rssi, payloads=[$payloadSummary]"
}

@JvmName("logFrameworkScanResultCollectionSummary")
fun Collection<ScanResult>.logSummary(limit: Int = 3): String {
    if (isEmpty()) return "count=0"
    val sample = take(limit).joinToString(separator = ", ") { it.logSummary() }
    val suffix = if (size > limit) ", ..." else ""
    return "count=$size, sample=[$sample$suffix]"
}
