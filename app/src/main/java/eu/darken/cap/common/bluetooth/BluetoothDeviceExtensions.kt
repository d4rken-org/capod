package eu.darken.cap.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid

fun BluetoothDevice.hasFeature(uuid: ParcelUuid): Boolean {
    return uuids?.contains(uuid) ?: false
}