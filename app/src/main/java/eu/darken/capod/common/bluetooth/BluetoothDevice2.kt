package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import java.time.Instant

data class BluetoothDevice2(
    val address: BluetoothAddress,
    val name: String?,
    val seenFirstAt: Instant,
    internal val internal: BluetoothDevice? = null,
)
