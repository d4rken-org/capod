package eu.darken.capod.monitor.core.aap

import android.view.KeyEvent
import eu.darken.capod.common.MediaControl
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
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
            profile(addressA, StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)),
            profile(addressB, StemActionsConfig(leftSingle = StemAction.NEXT_TRACK)),
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
                listOf<DeviceProfile>(profile(addressA, StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)))
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
    fun `NO_ACTION and NONE do not execute anything`() = runTest(UnconfinedTestDispatcher()) {
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
                            leftSingle = StemAction.NONE,
                            rightSingle = StemAction.NO_ACTION,
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
}
