package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import java.time.Instant

data class BluetoothDevice2(
    internal val internal: BluetoothDevice,
    val seenFirstAt: Instant,
) {
    val address: BluetoothAddress
        get() = internal.address

    val name: String?
        get() = internal.name
}
