package eu.darken.capod.monitor.core.receiver

import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BootCompletedReceiverTest : BaseTest() {
    @Test
    fun `new path waits for Bluetooth events while legacy profiles retain boot monitoring`() = runTest {
        val address = "test-device"
        var profile = AppleDeviceProfile(label = "Pods", address = address, audioConnectOnAcl = true)
        var connected = emptyList<BluetoothDevice2>()
        val control = mockk<MonitorControl>(relaxed = true)
        val receiver = BootCompletedReceiver().apply {
            monitorControl = control
            profilesRepo = mockk { every { profiles } answers { flowOf(listOf(profile)) } }
            bluetoothManager = mockk { every { connectedDevices } answers { flowOf(connected) } }
        }
        receiver.startMonitorIfNeeded()
        verify(exactly = 0) { control.startMonitor(any()) }
        connected = listOf(mockk { every { this@mockk.address } returns address })
        receiver.startMonitorIfNeeded()
        verify(exactly = 1) { control.startMonitor(false) }
        connected = emptyList()
        profile = profile.copy(audioConnectOnAcl = false)
        receiver.startMonitorIfNeeded()
        verify(exactly = 2) { control.startMonitor(false) }
        profile = profile.copy(audioConnectOnAcl = true, autoConnect = true)
        receiver.startMonitorIfNeeded()
        verify(exactly = 3) { control.startMonitor(false) }
    }
}
