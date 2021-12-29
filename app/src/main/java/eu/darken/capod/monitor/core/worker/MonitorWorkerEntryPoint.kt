package eu.darken.capod.monitor.core.worker

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.MonitorComponent
import eu.darken.capod.monitor.core.PodMonitor

@InstallIn(MonitorComponent::class)
@EntryPoint
interface MonitorWorkerEntryPoint {
    fun podMonitor(): PodMonitor
    fun bluetoothManager2(): BluetoothManager2
}