package eu.darken.capod.monitor.core.receiver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAttemptResult
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BluetoothEventReceiverTest : BaseTest() {
    @Test
    fun `only established profile connections can start monitoring`() {
        for (action in listOf(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
            shouldHandleBluetoothEvent(action, BluetoothProfile.STATE_CONNECTED) shouldBe true
            for (state in listOf(-1, BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED)) {
                shouldHandleBluetoothEvent(action, state) shouldBe false
            }
        }
        shouldHandleBluetoothEvent(BluetoothDevice.ACTION_ACL_CONNECTED, -1) shouldBe true
        shouldHandleBluetoothEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED, -1) shouldBe true
    }

    @Test
    fun `ACL request is opt in associated and independent of scan auto connect`() = runTest {
        val targetAddress = "test-device"
        val device = mockk<BluetoothDevice> {
            every { address } returns targetAddress
            every { name } returns "Pods"
        }
        val repo = mockk<DeviceProfilesRepo>()
        val bluetooth = mockk<BluetoothManager2>(relaxed = true)
        val receiver = BluetoothEventReceiver().apply {
            profilesRepo = repo
            bluetoothManager = bluetooth
        }
        var profile = AppleDeviceProfile(label = "Pods", address = device.address, autoConnect = true)
        every { repo.profiles } answers { flowOf(listOf(profile)) }
        receiver.connectAudioIfEnabled(device) shouldBe false
        verify(exactly = 0) { bluetooth.connectAudio(any()) }

        profile = profile.copy(autoConnect = false, audioConnectOnAcl = true)
        every { bluetooth.isCompanionAssociated(any()) } returns false
        receiver.connectAudioIfEnabled(device) shouldBe true
        verify(exactly = 0) { bluetooth.connectAudio(any()) }

        every { bluetooth.isCompanionAssociated(any()) } returns true
        every { bluetooth.connectAudio(any()) } returns NudgeAttemptResult.Accepted
        receiver.connectAudioIfEnabled(device) shouldBe true
        verify(exactly = 1) { bluetooth.connectAudio(device) }
        verify(exactly = 2) { bluetooth.markDeviceConnected(targetAddress) }

        profile = profile.copy(address = "other-device")
        receiver.connectAudioIfEnabled(device) shouldBe false
        verify(exactly = 1) { bluetooth.connectAudio(any()) }
    }
}
