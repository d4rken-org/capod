package eu.darken.cap.monitor.core.worker

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import eu.darken.cap.common.bluetooth.BluetoothManager2
import eu.darken.cap.monitor.core.MonitorComponent
import eu.darken.cap.monitor.core.PodMonitor

@InstallIn(MonitorComponent::class)
@EntryPoint
interface MonitorWorkerEntryPoint {
    fun podMonitor(): PodMonitor
    fun bluetoothManager2(): BluetoothManager2
}