package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile

data class BluetoothProfile2(
    internal val profileType: Int,
    private val profileProxy: BluetoothProfile,
) {

    val proxy: BluetoothProfile
        get() = profileProxy

    val connectedDevices: Set<BluetoothDevice>
        get() = proxy.connectedDevices.toSet()
}
