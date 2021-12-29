package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid

fun BluetoothDevice.hasFeature(uuid: ParcelUuid): Boolean {
    return uuids?.contains(uuid) ?: false
}