package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.BuildWrap
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.bluetooth.NudgeAttemptResult
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AutoConnectLogicTest : BaseTest() {
    @Test
    fun `In ear requests audio only from a live ear report and Off suppresses requests`() = runTest {
        mockkObject(BuildWrap.VersionWrap)
        try {
            every { BuildWrap.VersionWrap.SDK_INT } returns 26
            val address = "test-device"
            val profile = AppleDeviceProfile(label = "Pods", address = address, autoConnect = true,
                autoConnectCondition = AutoConnectCondition.IN_EAR)
            val profiles = MutableStateFlow<List<DeviceProfile>>(listOf(profile))
            val states = MutableStateFlow<Map<String, AapPodState>>(emptyMap())
            val device = mockk<android.bluetooth.BluetoothDevice>()
            val bluetooth = mockk<BluetoothManager2>(relaxed = true) {
                every { connectedDevices } returns flowOf(emptyList())
                every { bondedDevices() } returns flowOf(setOf(BluetoothDevice2(address, "Pods", java.time.Instant.EPOCH, device)))
            }
            coEvery { bluetooth.connectAudio(device) } returns NudgeAttemptResult.Accepted
            val aap = mockk<AapConnectionManager> { every { allStates } returns states }
            val repo = mockk<DeviceProfilesRepo> { every { this@mockk.profiles } returns profiles }
            val capabilities = mockk<NudgeCapabilityStore>(relaxed = true) {
                every { availability } returns MutableStateFlow(NudgeAvailability.AVAILABLE)
            }
            backgroundScope.launch { AutoConnect(bluetooth, aap, repo, capabilities).monitor().collect {} }
            runCurrent()
            states.value = mapOf(address to AapPodState(connectionState = AapPodState.ConnectionState.READY))
            runCurrent()
            coVerify(exactly = 0) { bluetooth.connectAudio(any()) }
            val inEar = states.value.getValue(address).withSetting(AapSetting.EarDetection::class,
                AapSetting.EarDetection(AapSetting.EarDetection.PodPlacement.IN_EAR, AapSetting.EarDetection.PodPlacement.IN_EAR))
            states.value = mapOf(address to inEar)
            runCurrent()
            coVerify(exactly = 1) { bluetooth.connectAudio(device) }
            profiles.value = listOf(profile.copy(lastRequestedConnectionPreference = AapSetting.ConnectionPreference.Mode.OFF))
            states.value = emptyMap()
            runCurrent()
            states.value = mapOf(address to inEar)
            runCurrent()
            coVerify(exactly = 1) { bluetooth.connectAudio(device) }
        } finally {
            unmockkObject(BuildWrap.VersionWrap)
        }
    }
}
