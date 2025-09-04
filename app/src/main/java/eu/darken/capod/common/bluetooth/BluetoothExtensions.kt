package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.os.ParcelUuid
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log

fun BluetoothDevice.hasFeature(uuid: ParcelUuid): Boolean {
    return uuids?.contains(uuid) ?: false
}

/**
 * java.lang.NullPointerException: Attempt to invoke virtual method 'java.lang.Object android.util.SparseArray.get(int)' on a null object reference
 *  at android.bluetooth.le.ScanRecord.getManufacturerSpecificData(ScanRecord.java:118)
 *  at android.bluetooth.le.ScanFilter.matches(ScanFilter.java:369)
 * ZenFone Max Pro M1 (ZB602KL) (WW) / Max Pro M1 (ZB601KL) (IN) (ZB602KL), Android 9, PKQ1.WW_Phone-16.2017.2009.087-20200826
 * Intel Gemini Lake Chromebook (octopus), Android 9, R99-14469.59.0 release-keys
 */
fun ScanFilter.matchesSafe(scanResult: ScanResult): Boolean = try {
    matches(scanResult)
} catch (e: NullPointerException) {
    log { "AOSP error: ${e.asLog()}" }
    false
}