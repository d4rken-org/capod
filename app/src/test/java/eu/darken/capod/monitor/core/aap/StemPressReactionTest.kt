package eu.darken.capod.monitor.core.aap

import android.view.KeyEvent
import eu.darken.capod.common.MediaControl
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class StemPressReactionTest : BaseTest() {

    private val addressA = "AA:AA:AA:AA:AA:AA"
    private val addressB = "BB:BB:BB:BB:BB:BB"

    private fun profile(address: String, stemActions: StemActionsConfig) = AppleDeviceProfile(
        label = "Test",
        model = PodModel.AIRPODS_PRO2,
        address = address,
        stemActions = stemActions,
    )

    @Test
    fun `resolves action per profile address`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
        }
        val profiles = listOf<DeviceProfile>(
            profile(addressA, StemActionsConfig(leftSingle = StemAction.PlayPause)),
            profile(addressB, StemActionsConfig(leftSingle = StemAction.NextTrack)),
        )
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(profiles)
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)

        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()
        events.emit(addressB to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaControl.sendPlayPause() }
        coVerify(exactly = 1) { mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

        job.cancel()
    }

    @Test
    fun `ignores events from unknown address`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(
                listOf<DeviceProfile>(profile(addressA, StemActionsConfig(leftSingle = StemAction.PlayPause)))
            )
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)

        val job = launch { reaction.monitor().collect {} }

        events.emit("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ" to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaControl.sendPlayPause() }

        job.cancel()
    }

    @Test
    fun `NoAction and None do not execute anything`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(
                listOf<DeviceProfile>(
                    profile(
                        addressA,
                        StemActionsConfig(
                            leftSingle = StemAction.None,
                            rightSingle = StemAction.NoAction,
                        ),
                    )
                )
            )
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)

        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.RIGHT))
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaControl.sendPlayPause() }
        coVerify(exactly = 0) { mediaControl.sendKey(any()) }

        job.cancel()
    }

    @Test
    fun `new media-key actions dispatch correct keycodes`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(
                listOf<DeviceProfile>(
                    profile(
                        addressA,
                        StemActionsConfig(
                            leftSingle = StemAction.Stop,
                            leftDouble = StemAction.FastForward,
                            leftTriple = StemAction.Rewind,
                            leftLong = StemAction.MuteToggle,
                        ),
                    )
                )
            )
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)
        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.DOUBLE, StemPressEvent.Bud.LEFT))
        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.TRIPLE, StemPressEvent.Bud.LEFT))
        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.LONG, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_STOP) }
        coVerify(exactly = 1) { mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) }
        coVerify(exactly = 1) { mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_REWIND) }
        coVerify(exactly = 1) { mediaControl.toggleMuteMusic() }

        job.cancel()
    }

    @Test
    fun `CycleAnc sends SetAncMode with the next cycle mode`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapState = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.ON,
                    supported = listOf(
                        AapSetting.AncMode.Value.OFF,
                        AapSetting.AncMode.Value.ON,
                        AapSetting.AncMode.Value.TRANSPARENCY,
                        AapSetting.AncMode.Value.ADAPTIVE,
                    ),
                ),
                AapSetting.ListeningModeCycle::class to AapSetting.ListeningModeCycle(modeMask = 0x0E),
            ),
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
            every { allStates } returns MutableStateFlow(mapOf(addressA to aapState))
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(
                listOf<DeviceProfile>(
                    profile(addressA, StemActionsConfig(leftSingle = StemAction.CycleAnc))
                )
            )
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)
        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            aapManager.sendCommand(addressA, AapCommand.SetAncMode(AapSetting.AncMode.Value.TRANSPARENCY))
        }

        job.cancel()
    }

    @Test
    fun `ToggleAncTransparency flips to ON when leaving TRANSPARENCY`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapState = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.TRANSPARENCY,
                    supported = listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY),
                ),
            ),
        )
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
            every { allStates } returns MutableStateFlow(mapOf(addressA to aapState))
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(
                listOf<DeviceProfile>(
                    profile(addressA, StemActionsConfig(leftSingle = StemAction.ToggleAncTransparency))
                )
            )
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)
        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            aapManager.sendCommand(addressA, AapCommand.SetAncMode(AapSetting.AncMode.Value.ON))
        }

        job.cancel()
    }

    @Test
    fun `CycleAnc is a no-op when model lacks listening-mode-cycle`() = runTest(UnconfinedTestDispatcher()) {
        val events = MutableSharedFlow<Pair<String, StemPressEvent>>(extraBufferCapacity = 8)
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { stemPressEvents } returns events
            every { allStates } returns MutableStateFlow(emptyMap())
        }
        val noCycleProfile = AppleDeviceProfile(
            label = "Test",
            model = PodModel.AIRPODS_GEN3,
            address = addressA,
            stemActions = StemActionsConfig(leftSingle = StemAction.CycleAnc),
        )
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxed = true) {
            every { this@mockk.profiles } returns flowOf(listOf<DeviceProfile>(noCycleProfile))
        }
        val mediaControl = mockk<MediaControl>(relaxed = true)
        val reaction = StemPressReaction(aapManager, profilesRepo, mediaControl)
        val job = launch { reaction.monitor().collect {} }

        events.emit(addressA to StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT))
        advanceUntilIdle()

        coVerify(exactly = 0) { aapManager.sendCommand(any(), any<AapCommand.SetAncMode>()) }

        job.cancel()
    }
}
