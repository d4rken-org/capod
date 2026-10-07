package eu.darken.capod.monitor.core.receiver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import eu.darken.capod.common.BuildWrap
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAttemptResult
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
    fun `ACL request is opt in and only Android 17 needs companion approval`() = runTest {
        mockkObject(BuildWrap.VersionWrap)
        try {
            every { BuildWrap.VersionWrap.SDK_INT } returns 37
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
                aapManager = mockk(relaxed = true)
                monitorControl = mockk(relaxed = true)
            }
            var profile = AppleDeviceProfile(label = "Pods", address = device.address, autoConnect = false)
            every { repo.profiles } answers { flowOf(listOf(profile)) }
            receiver.connectAudioIfEnabled(device) shouldBe false
            coVerify(exactly = 0) { bluetooth.connectAudio(any()) }

            profile = profile.copy(autoConnect = true)
            every { bluetooth.isCompanionAssociated(any()) } returns false
            receiver.connectAudioIfEnabled(device) shouldBe true
            coVerify(exactly = 0) { bluetooth.connectAudio(any()) }

            every { bluetooth.isCompanionAssociated(any()) } returns true
            coEvery { bluetooth.connectAudio(any()) } returns NudgeAttemptResult.Accepted
            receiver.connectAudioIfEnabled(device) shouldBe true
            coVerify(exactly = 1) { bluetooth.connectAudio(device) }
            verify(exactly = 2) { bluetooth.markDeviceConnected(targetAddress) }

            every { BuildWrap.VersionWrap.SDK_INT } returns 26
            every { bluetooth.isCompanionAssociated(any()) } returns false
            receiver.connectAudioIfEnabled(device) shouldBe true
            coVerify(exactly = 2) { bluetooth.connectAudio(device) }

            profile = profile.copy(autoConnectCondition = AutoConnectCondition.IN_EAR)
            receiver.connectAudioIfEnabled(device) shouldBe true
            coVerify(exactly = 1) { receiver.aapManager.connect(targetAddress, device, profile.model) }
            coVerify(exactly = 2) { bluetooth.connectAudio(any()) }

            profile = profile.copy(address = "other-device")
            receiver.connectAudioIfEnabled(device) shouldBe false
            coVerify(exactly = 2) { bluetooth.connectAudio(any()) }
        } finally {
            unmockkObject(BuildWrap.VersionWrap)
        }
    }
}
