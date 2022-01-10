package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairedDevices @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val bluetoothManager2: BluetoothManager2,
) {

    fun connectedDevices(): Flow<List<BluetoothDevice2>> = bluetoothManager2
        .isBluetoothEnabled
        .flatMapLatest { bluetoothManager2.connectedDevices() }
        .map { devices ->
            devices.filter { device ->
                ContinuityProtocol.BLE_FEATURE_UUIDS.any { feature ->
                    device.hasFeature(feature)
                }
            }
        }
        .replayingShare(scope)
}