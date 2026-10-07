package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ConnectionPreferenceInitializationTest : BaseTest() {
    @Test
    fun `retries on a new ready connection and never overrides saved Off`() = runTest {
        val automatic = AapSetting.ConnectionPreference.Mode.AUTOMATIC
        val fresh = AppleDeviceProfile(id = "new", label = "New", address = "new-device", model = PodModel.AIRPODS_GEN2, autoConnect = true)
        val manual = fresh.copy(id = "manual", address = "manual-device", lastRequestedConnectionPreference = AapSetting.ConnectionPreference.Mode.OFF)
        val profiles = MutableStateFlow<List<DeviceProfile>>(listOf(fresh, manual))
        val states = MutableStateFlow<Map<String, AapPodState>>(emptyMap())
        val manager = mockk<AapConnectionManager>(relaxed = true) {
            every { allStates } returns states
        }
        var failFirstSend = true
        coEvery { manager.sendCommand("new-device", any()) } coAnswers {
            if (failFirstSend) {
                failFirstSend = false
                throw java.io.IOException("test send failure")
            }
        }
        val repo = mockk<DeviceProfilesRepo> {
            every { this@mockk.profiles } returns profiles
        }
        coEvery { repo.updateAppleProfile(any(), any()) } coAnswers {
            val id = firstArg<String>()
            val transform = secondArg<(AppleDeviceProfile) -> AppleDeviceProfile>()
            profiles.value = profiles.value.map { if (it.id == id) transform(it as AppleDeviceProfile) else it }
        }
        val sender = AapAutoConnect(manager, repo, mockk<BluetoothManager2>(), mockk<BlePodMonitor>())
        backgroundScope.launch { sender.initializeConnectionPreference().collect {} }
        runCurrent()
        coVerify(exactly = 0) { manager.sendCommand(any(), any()) }
        val ready = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        states.value = mapOf("new-device" to ready, "manual-device" to ready)
        runCurrent()
        (profiles.value.first() as AppleDeviceProfile).lastRequestedConnectionPreference shouldBe null
        states.value = emptyMap()
        runCurrent()
        states.value = mapOf("new-device" to ready, "manual-device" to ready)
        runCurrent()
        coVerify(exactly = 2) { manager.sendCommand("new-device", AapCommand.SetConnectionPreference(automatic)) }
        coVerify { manager.sendCommand("manual-device", AapCommand.SetConnectionPreference(AapSetting.ConnectionPreference.Mode.OFF)) }
        (profiles.value.first() as AppleDeviceProfile).lastRequestedConnectionPreference shouldBe automatic
        (profiles.value.last() as AppleDeviceProfile).lastRequestedConnectionPreference shouldBe AapSetting.ConnectionPreference.Mode.OFF
        states.value = emptyMap()
        runCurrent()
        states.value = mapOf("new-device" to ready)
        runCurrent()
        coVerify(exactly = 3) { manager.sendCommand("new-device", AapCommand.SetConnectionPreference(automatic)) }
    }
}
