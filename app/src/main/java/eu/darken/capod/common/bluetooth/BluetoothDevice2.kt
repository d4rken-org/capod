package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BluetoothDevice2(
    val device: BluetoothDevice
) : Parcelable {

    val name: String?
        get() = device.name

    fun hasFeature(uuid: ParcelUuid): Boolean = device.hasFeature(uuid)
}