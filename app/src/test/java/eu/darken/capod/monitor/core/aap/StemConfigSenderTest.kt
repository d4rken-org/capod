package eu.darken.capod.monitor.core.aap

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StemConfigSenderTest : BaseTest() {

    private val addressA = "AA:AA:AA:AA:AA:AA"
    private val addressB = "BB:BB:BB:BB:BB:BB"

    private fun profile(address: String, stemActions: StemActionsConfig) = AppleDeviceProfile(
        label = "Test",
        model = PodModel.AIRPODS_PRO2,
        address = address,
        stemActions = stemActions,
    )

    @Test
    fun `sends per-profile mask to each AAP-ready device`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(
            mapOf(
                addressA to AapPodState(connectionState = AapPodState.ConnectionState.READY),
                addressB to AapPodState(connectionState = AapPodState.ConnectionState.READY),
            )
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        // A has SINGLE only → mask 0x01. B has LONG only → mask 0x08.
        val profilesFlow = MutableStateFlow<List<DeviceProfile>>(
            listOf(
                profile(addressA, StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)),
                profile(addressB, StemActionsConfig(rightLong = StemAction.VOLUME_UP)),
            )
        )
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { profiles } returns profilesFlow
        }
        val sender = StemConfigSender(aapManager, profilesRepo)

        val job = launch { sender.monitor().collect {} }
        advanceUntilIdle()

        coVerify(exactly = 1) { aapManager.sendCommand(addressA, AapCommand.SetStemConfig(0x01)) }
        coVerify(exactly = 1) { aapManager.sendCommand(addressB, AapCommand.SetStemConfig(0x08)) }

        job.cancel()
    }

    @Test
    fun `skips devices that are not AAP-ready`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(
            mapOf(
                addressA to AapPodState(connectionState = AapPodState.ConnectionState.READY),
                addressB to AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED),
            )
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profilesFlow = MutableStateFlow<List<DeviceProfile>>(
            listOf(
                profile(addressA, StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)),
                profile(addressB, StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)),
            )
        )
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { profiles } returns profilesFlow
        }
        val sender = StemConfigSender(aapManager, profilesRepo)

        val job = launch { sender.monitor().collect {} }
        advanceUntilIdle()

        coVerify(exactly = 1) { aapManager.sendCommand(addressA, AapCommand.SetStemConfig(0x01)) }
        coVerify(exactly = 0) { aapManager.sendCommand(addressB, any<AapCommand.SetStemConfig>()) }

        job.cancel()
    }

    @Test
    fun `all-NONE config sends mask 0x00`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(
            mapOf(addressA to AapPodState(connectionState = AapPodState.ConnectionState.READY))
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { profiles } returns MutableStateFlow<List<DeviceProfile>>(
                listOf(profile(addressA, StemActionsConfig()))
            )
        }
        val sender = StemConfigSender(aapManager, profilesRepo)

        val job = launch { sender.monitor().collect {} }
        advanceUntilIdle()

        coVerify(exactly = 1) { aapManager.sendCommand(addressA, AapCommand.SetStemConfig(0x00)) }

        job.cancel()
    }

    @Test
    fun `all four press types contribute independent bits to the mask`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(
            mapOf(addressA to AapPodState(connectionState = AapPodState.ConnectionState.READY))
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { profiles } returns MutableStateFlow<List<DeviceProfile>>(
                listOf(
                    profile(
                        addressA,
                        StemActionsConfig(
                            leftSingle = StemAction.PLAY_PAUSE,
                            rightDouble = StemAction.NEXT_TRACK,
                            leftTriple = StemAction.VOLUME_UP,
                            rightLong = StemAction.VOLUME_DOWN,
                        ),
                    )
                )
            )
        }
        val sender = StemConfigSender(aapManager, profilesRepo)

        val job = launch { sender.monitor().collect {} }
        advanceUntilIdle()

        coVerify(exactly = 1) { aapManager.sendCommand(addressA, AapCommand.SetStemConfig(0x0F)) }

        job.cancel()
    }
}
