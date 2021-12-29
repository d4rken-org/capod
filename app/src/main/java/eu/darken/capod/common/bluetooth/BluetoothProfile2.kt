package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothProfile
import java.util.concurrent.atomic.AtomicBoolean

data class BluetoothProfile2(
    private val profileType: Int,
    private val profileProxy: BluetoothProfile,
    private val isConnectedAtomic: AtomicBoolean,
) {

    val profile: BluetoothProfile
        get() {
            if (!isConnected) throw IllegalStateException("Proxy is not connected")
            return profileProxy
        }

    val connectedDevices: Set<BluetoothDevice2>
        get() = profile.connectedDevices.map { BluetoothDevice2(it) }.toSet()

    val isConnected: Boolean
        get() = isConnectedAtomic.get()
}
