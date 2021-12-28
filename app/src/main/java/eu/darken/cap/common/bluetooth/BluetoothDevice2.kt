package eu.darken.cap.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BluetoothDevice2(
    private val bluetoothDevice: BluetoothDevice
) : Parcelable {

    fun hasFeature(uuid: ParcelUuid): Boolean = bluetoothDevice.hasFeature(uuid)
}