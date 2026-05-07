package eu.darken.capod.reaction.core.playpause

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.playpause.PlayPause.EarDetectionState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class PlayPauseLogicTest : BaseTest() {

    private lateinit var playPause: PlayPause

    @BeforeEach
    fun setup() {
        // Create PlayPause instance with mocked dependencies (relaxed so we don't need to stub everything)
        playPause = PlayPause(
            deviceMonitor = mockk(relaxed = true),
            bluetoothManager = mockk(relaxed = true),
            mediaControl = mockk(relaxed = true)
        )
    }

    @Nested
    inner class NormalModeTests {

        @Test
        fun `both in to one out - should pause if playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `both in to one out - no action if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - should play if not playing (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - no action if already playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - should play if recently paused by cap`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true,
                wasRecentlyPausedByUs = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
            decision.usedRecentCapPauseOverride shouldBe true
        }

        @Test
        fun `none in to one in - no action (need both)`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `none in to both in - should play if not playing (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        @Test
        fun `both in to both out - should pause if playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = false, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `one in to none in - no action (wasn't fully worn)`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = false, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `both in stays both in - no action`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `both out to one in - no action (need both)`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = false, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - default suppresses autoplay when not we-paused`() {
            // Bug repro: user manually paused, then took one pod out and put it back.
            // We didn't pause and the user hasn't opted into cold-wear; suppress autoplay.
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = false,
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - we-paused resumes regardless of startMusicOnWear`() {
            // wasRecentlyPausedByUs=true must take precedence over the cold-wear setting:
            // resume our pause whether or not the user opted into cold-wear.
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decisionWearOff = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = true,
                startMusicOnWear = false,
            )
            decisionWearOff.shouldPlay shouldBe true

            val decisionWearOn = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = true,
                startMusicOnWear = true,
            )
            decisionWearOn.shouldPlay shouldBe true
        }

        @Test
        fun `none in to both in - cold start fires play only when startMusicOnWear is opted in`() {
            // Default OFF: putting pods on with no music history does nothing.
            // Opted in: same transition fires the cold-wear play key.
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decisionDefault = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = false,
            )
            decisionDefault.shouldPlay shouldBe false

            val decisionOptedIn = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = true,
            )
            decisionOptedIn.shouldPlay shouldBe true
        }
    }

    @Nested
    inner class OnePodModeTests {

        @Test
        fun `one in to none in - should pause if playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = false, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `one in to none in - no action if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = false, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `none in to one in - should play if not playing (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        @Test
        fun `none in to one in - no action if already playing`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - should play if paused (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            // NEW BEHAVIOR: In one-pod mode, inserting a pod triggers play
            // Net change: +1 pod (right inserted)
            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - no action if already playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            // If already playing, inserting another pod shouldn't do anything
            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one in to both in - should play if recently paused by cap`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true,
                wasRecentlyPausedByUs = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
            decision.usedRecentCapPauseOverride shouldBe true
        }

        @Test
        fun `both in to one in - should pause if playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            // NEW BEHAVIOR: In one-pod mode, removing a pod triggers pause
            // Net change: -1 pod (right removed)
            // This fixes the edge case!
            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `both in to one in - no action if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
            )

            // If not playing, removing a pod shouldn't do anything
            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `switch pods (left to right) - no action`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = false, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            // Switching pods shouldn't affect playback
            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `both in to none in - should pause if playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = true)
            val current = EarDetectionState.fromDualPod(left = false, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `none in to both in - should play if not playing (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        /**
         * Verifies the fix for the one-pod mode edge case where removing a pod
         * while another remains in ear previously did not trigger pause
         * (eitherInEar stayed true, masking individual pod removal).
         */
        @Test
        fun `EDGE CASE - both in, remove one, reinsert - should pause then play`() {
            // Step 1→2: Both in → Right removed (should pause)
            val step1 = EarDetectionState.fromDualPod(left = true, right = true)
            val step2 = EarDetectionState.fromDualPod(left = true, right = false)

            val decision1to2 = playPause.evaluatePlayPauseAction(
                previous = step1,
                current = step2,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            // Expected: should pause when a pod is removed, even if another is still in
            decision1to2.shouldPlay shouldBe false
            decision1to2.shouldPause shouldBe true

            // Step 2→3: Right reinserted (should play). The original pause came from CAP in
            // step 1→2, so wasRecentlyPausedByUs would be true in real flow — pass that here
            // to keep the test focused on the one-pod-mode transition logic without depending
            // on the cold-wear setting.
            val step3 = EarDetectionState.fromDualPod(left = true, right = true)

            val decision2to3 = playPause.evaluatePlayPauseAction(
                previous = step2,
                current = step3,
                onePodMode = true,
                isCurrentlyPlaying = false, // Music is now paused from step 2
                wasRecentlyPausedByUs = true,
            )

            // Expected: should play when a pod is reinserted
            decision2to3.shouldPlay shouldBe true
            decision2to3.shouldPause shouldBe false
        }

        @Test
        fun `rapid transitions - none to one to none - should play then pause (cold-wear opt-in)`() {
            // Step 1: None → One in (should play)
            val step1 = EarDetectionState.fromDualPod(left = false, right = false)
            val step2 = EarDetectionState.fromDualPod(left = true, right = false)

            val decision1to2 = playPause.evaluatePlayPauseAction(
                previous = step1,
                current = step2,
                onePodMode = true,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision1to2.shouldPlay shouldBe true
            decision1to2.shouldPause shouldBe false

            // Step 2: One → None (should pause)
            val step3 = EarDetectionState.fromDualPod(left = false, right = false)

            val decision2to3 = playPause.evaluatePlayPauseAction(
                previous = step2,
                current = step3,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            decision2to3.shouldPlay shouldBe false
            decision2to3.shouldPause shouldBe true
        }

        @Test
        fun `one-pod mode pod insertion - default suppresses autoplay`() {
            // Bug repro in one-pod mode: user manually paused, removed one pod, put it back.
            // Default startMusicOnWear=false means cold-wear path is gated; suppress.
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = false,
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `one-pod mode pod insertion - cap-paused still resumes regardless of startMusicOnWear`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = true,
                startMusicOnWear = false,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }
    }

    @Nested
    inner class BleConfirmationTests {

        @Test
        fun `ble-only normal mode autoplay waits for confirmation (cold-wear opt-in)`() {
            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = false),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            val result = playPause.applyBleOnlyPlayConfirmation(
                pending = null,
                profileId = "profile",
                onePodMode = false,
                autoPlayEnabled = true,
                rawDecision = rawDecision,
                currentState = EarDetectionState.fromDualPod(left = true, right = true),
                shouldStageBleOnlyPlay = true,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = true,
            )

            result.decision.shouldPlay shouldBe false
            result.decision.reason shouldBe "Normal mode: both pods in ear (waiting for BLE confirmation)"
            result.pending shouldBe PlayPause.PendingPlayConfirmation(
                profileId = "profile",
                onePodMode = false,
                targetState = EarDetectionState.fromDualPod(left = true, right = true),
                reason = "Normal mode: both pods in ear",
            )
            result.stagedConfirmation shouldBe true
            result.confirmedPendingPlay shouldBe false
        }

        @Test
        fun `ble-only normal mode autoplay confirms on stable follow-up state (cold-wear opt-in)`() {
            val pending = PlayPause.PendingPlayConfirmation(
                profileId = "profile",
                onePodMode = false,
                targetState = EarDetectionState.fromDualPod(left = true, right = true),
                reason = "Normal mode: both pods in ear",
            )

            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = true),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            val result = playPause.applyBleOnlyPlayConfirmation(
                pending = pending,
                profileId = "profile",
                onePodMode = false,
                autoPlayEnabled = true,
                rawDecision = rawDecision,
                currentState = EarDetectionState.fromDualPod(left = true, right = true),
                shouldStageBleOnlyPlay = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = true,
            )

            result.decision.shouldPlay shouldBe true
            result.decision.reason shouldBe "Normal mode: both pods in ear (confirmed by a second state update)"
            result.pending shouldBe null
            result.stagedConfirmation shouldBe false
            result.confirmedPendingPlay shouldBe true
        }

        @Test
        fun `aap-backed autoplay bypasses confirmation (cold-wear opt-in)`() {
            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = false),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            val result = playPause.applyBleOnlyPlayConfirmation(
                pending = null,
                profileId = "profile",
                onePodMode = false,
                autoPlayEnabled = true,
                rawDecision = rawDecision,
                currentState = EarDetectionState.fromDualPod(left = true, right = true),
                shouldStageBleOnlyPlay = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = true,
            )

            result.decision.shouldPlay shouldBe true
            result.pending shouldBe null
            result.stagedConfirmation shouldBe false
            result.confirmedPendingPlay shouldBe false
        }

        @Test
        fun `pending ble-only autoplay is dropped when state reverts`() {
            val pending = PlayPause.PendingPlayConfirmation(
                profileId = "profile",
                onePodMode = false,
                targetState = EarDetectionState.fromDualPod(left = true, right = true),
                reason = "Normal mode: both pods in ear",
            )

            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = true),
                current = EarDetectionState.fromDualPod(left = true, right = false),
                onePodMode = false,
                isCurrentlyPlaying = false,
            )

            val result = playPause.applyBleOnlyPlayConfirmation(
                pending = pending,
                profileId = "profile",
                onePodMode = false,
                autoPlayEnabled = true,
                rawDecision = rawDecision,
                currentState = EarDetectionState.fromDualPod(left = true, right = false),
                shouldStageBleOnlyPlay = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
            )

            result.decision.shouldPlay shouldBe false
            result.pending shouldBe null
            result.stagedConfirmation shouldBe false
            result.confirmedPendingPlay shouldBe false
        }

        @Test
        fun `ble-only confirmation suppressed by default when not we-paused`() {
            // A staged BLE-only autoplay must not confirm under default settings (cold-wear
            // gated) when CAP didn't pause — even if the second worn sample matches.
            val pending = PlayPause.PendingPlayConfirmation(
                profileId = "profile",
                onePodMode = false,
                targetState = EarDetectionState.fromDualPod(left = true, right = true),
                reason = "Normal mode: both pods in ear",
            )

            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = true),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = false,
            )

            val result = playPause.applyBleOnlyPlayConfirmation(
                pending = pending,
                profileId = "profile",
                onePodMode = false,
                autoPlayEnabled = true,
                rawDecision = rawDecision,
                currentState = EarDetectionState.fromDualPod(left = true, right = true),
                shouldStageBleOnlyPlay = false,
                isCurrentlyPlaying = false,
                wasRecentlyPausedByUs = false,
                startMusicOnWear = false,
            )

            result.decision.shouldPlay shouldBe false
            result.confirmedPendingPlay shouldBe false
        }
    }

    @Nested
    inner class AapAggregateTests {

        @Test
        fun `fromAapAggregate - both in ear`() {
            val state = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)
            state.bothInEar shouldBe true
            state.eitherInEar shouldBe true
            state.podCount shouldBe 2
        }

        @Test
        fun `fromAapAggregate - one in ear`() {
            val state = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)
            state.bothInEar shouldBe false
            state.eitherInEar shouldBe true
            state.podCount shouldBe 1
        }

        @Test
        fun `fromAapAggregate - none in ear`() {
            val state = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)
            state.bothInEar shouldBe false
            state.eitherInEar shouldBe false
            state.podCount shouldBe 0
        }

        @Test
        fun `aggregate normal mode - both to none - should pause`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = false, isCurrentlyPlaying = true,
            )
            decision.shouldPause shouldBe true
        }

        @Test
        fun `aggregate normal mode - none to both - should play (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = false, isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )
            decision.shouldPlay shouldBe true
        }

        @Test
        fun `aggregate normal mode - both to one - should pause`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = false, isCurrentlyPlaying = true,
            )
            decision.shouldPause shouldBe true
        }

        @Test
        fun `aggregate one-pod mode - none to one - should play (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )
            decision.shouldPlay shouldBe true
        }

        @Test
        fun `aggregate one-pod mode - one to none - should pause`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = true,
            )
            decision.shouldPause shouldBe true
        }

        @Test
        fun `aggregate one-pod mode - one to both - should play (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )
            decision.shouldPlay shouldBe true
        }

        @Test
        fun `aggregate one-pod mode - both to one - should pause`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = true,
            )
            decision.shouldPause shouldBe true
        }
    }

    @Nested
    inner class EarDetectionStateTests {

        @Test
        fun `eitherInEar - both in`() {
            val state = EarDetectionState.fromDualPod(left = true, right = true)
            state.eitherInEar shouldBe true
        }

        @Test
        fun `eitherInEar - left only`() {
            val state = EarDetectionState.fromDualPod(left = true, right = false)
            state.eitherInEar shouldBe true
        }

        @Test
        fun `eitherInEar - right only`() {
            val state = EarDetectionState.fromDualPod(left = false, right = true)
            state.eitherInEar shouldBe true
        }

        @Test
        fun `eitherInEar - none in`() {
            val state = EarDetectionState.fromDualPod(left = false, right = false)
            state.eitherInEar shouldBe false
        }

        @Test
        fun `bothInEar - both in`() {
            val state = EarDetectionState.fromDualPod(left = true, right = true)
            state.bothInEar shouldBe true
        }

        @Test
        fun `bothInEar - left only`() {
            val state = EarDetectionState.fromDualPod(left = true, right = false)
            state.bothInEar shouldBe false
        }

        @Test
        fun `bothInEar - none in`() {
            val state = EarDetectionState.fromDualPod(left = false, right = false)
            state.bothInEar shouldBe false
        }

        @Test
        fun `podCount - both in`() {
            val state = EarDetectionState.fromDualPod(left = true, right = true)
            state.podCount shouldBe 2
        }

        @Test
        fun `podCount - left only`() {
            val state = EarDetectionState.fromDualPod(left = true, right = false)
            state.podCount shouldBe 1
        }

        @Test
        fun `podCount - none in`() {
            val state = EarDetectionState.fromDualPod(left = false, right = false)
            state.podCount shouldBe 0
        }

        @Test
        fun `single pod - eitherInEar and bothInEar delegate to isWorn`() {
            val worn = EarDetectionState.fromSinglePod(worn = true)
            worn.eitherInEar shouldBe true
            worn.bothInEar shouldBe true

            val notWorn = EarDetectionState.fromSinglePod(worn = false)
            notWorn.eitherInEar shouldBe false
            notWorn.bothInEar shouldBe false
        }
    }

    @Nested
    inner class SinglePodDeviceTests {

        @Test
        fun `single pod - not worn to worn - should play if not playing (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false, // Irrelevant for single pods
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decision.shouldPlay shouldBe true
            decision.shouldPause shouldBe false
        }

        @Test
        fun `single pod - not worn to worn - no action if already playing`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `single pod - worn to not worn - should pause if playing`() {
            val previous = EarDetectionState.fromSinglePod(worn = true)
            val current = EarDetectionState.fromSinglePod(worn = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe true
        }

        @Test
        fun `single pod - worn to not worn - no action if not playing`() {
            val previous = EarDetectionState.fromSinglePod(worn = true)
            val current = EarDetectionState.fromSinglePod(worn = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `single pod - worn stays worn - no action`() {
            val previous = EarDetectionState.fromSinglePod(worn = true)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `single pod - not worn stays not worn - no action`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            decision.shouldPlay shouldBe false
            decision.shouldPause shouldBe false
        }

        @Test
        fun `single pod - one-pod mode enabled - play behaves same as normal mode (cold-wear opt-in)`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decisionNormal = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            val decisionOnePod = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false,
                startMusicOnWear = true,
            )

            decisionNormal.shouldPlay shouldBe true
            decisionNormal.shouldPause shouldBe false
            decisionOnePod.shouldPlay shouldBe true
            decisionOnePod.shouldPause shouldBe false
        }

        @Test
        fun `single pod - one-pod mode enabled - pause behaves same as normal mode`() {
            val previous = EarDetectionState.fromSinglePod(worn = true)
            val current = EarDetectionState.fromSinglePod(worn = false)

            val decisionNormal = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = true
            )

            val decisionOnePod = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = true
            )

            decisionNormal.shouldPlay shouldBe false
            decisionNormal.shouldPause shouldBe true
            decisionOnePod.shouldPlay shouldBe false
            decisionOnePod.shouldPause shouldBe true
        }

        @Test
        fun `single pod - podCount is 0 or 1`() {
            val notWorn = EarDetectionState.fromSinglePod(worn = false)
            val worn = EarDetectionState.fromSinglePod(worn = true)

            notWorn.podCount shouldBe 0
            worn.podCount shouldBe 1
        }

        @Test
        fun `single pod - isSinglePod is true`() {
            val state = EarDetectionState.fromSinglePod(worn = true)
            state.isSinglePod shouldBe true
            state.isDualPod shouldBe false
        }

        @Test
        fun `single pod - leftInEar and rightInEar are null`() {
            val state = EarDetectionState.fromSinglePod(worn = true)
            state.leftInEar shouldBe null
            state.rightInEar shouldBe null
        }
    }

    @Nested
    inner class PauseDebounceTests {

        private val pauseDecision = PlayPause.PlayPauseDecision(
            shouldPlay = false,
            shouldPause = true,
            reason = "test pause",
        )

        private val playDecision = PlayPause.PlayPauseDecision(
            shouldPlay = true,
            shouldPause = false,
            reason = "test play",
        )

        private val noopDecision = PlayPause.PlayPauseDecision(
            shouldPlay = false,
            shouldPause = false,
            reason = "test no-op",
        )

        @Test
        fun `AAP source - debounce is skipped`() {
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.AAP,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            result.decision shouldBe pauseDecision
            result.pending shouldBe null
        }

        @Test
        fun `BLE_IRK_MATCH source - debounce is skipped`() {
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_IRK_MATCH,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            result.decision shouldBe pauseDecision
            result.pending shouldBe null
        }

        @Test
        fun `NO_LIVE_BLE source - clears existing pending without advancing it`() {
            // Stale-cache scenario: pending was started by a prior fresh BLE sample, then BLE
            // dropped (ble == null). The cached state must NOT count as a confirmation.
            val pending = PlayPause.PendingPauseDebounce(
                profileId = "profile",
                initialPodCount = 0,
                confirmationsRemaining = 1,
            )
            val result = playPause.applyPauseDebounce(
                pending = pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.NO_LIVE_BLE,
                rawDecision = noopDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            result.pending shouldBe null
            result.decision.shouldPause shouldBe false
            result.event shouldBe PlayPause.PauseDebounceEvent.RESET
        }

        @Test
        fun `NO_LIVE_BLE source - suppresses raw shouldPause to prevent stale-cache pause`() {
            // Without live BLE evidence, a cached worn → not-worn transition could otherwise
            // fire shouldPause. Verify the helper suppresses that to zero.
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.NO_LIVE_BLE,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            result.decision.shouldPause shouldBe false
            result.decision.reason shouldBe "test pause (suppressed: no live BLE evidence)"
            result.pending shouldBe null
            result.event shouldBe PlayPause.PauseDebounceEvent.NONE
        }

        @Test
        fun `BLE_PROFILE_FALLBACK first not-worn sample is suppressed and pending created`() {
            val current = EarDetectionState.fromDualPod(false, false)
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = pauseDecision,
                currentState = current,
                autoPauseEnabled = true,
            )

            result.decision.shouldPause shouldBe false
            result.pending shouldNotBe null
            result.pending!!.profileId shouldBe "profile"
            result.pending!!.initialPodCount shouldBe 0
            result.pending!!.confirmationsRemaining shouldBe PlayPause.PAUSE_DEBOUNCE_SAMPLES
        }

        @Test
        fun `BLE_PROFILE_FALLBACK three consecutive not-worn samples fires pause on third`() {
            val current = EarDetectionState.fromDualPod(false, false)

            // Sample 1 — first detection, suppressed, pending created
            val result1 = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = pauseDecision,
                currentState = current,
                autoPauseEnabled = true,
            )
            result1.decision.shouldPause shouldBe false
            result1.pending shouldNotBe null

            // Sample 2 — confirmation 1/2, still suppressed (raw is now no-op since not-worn -> not-worn)
            val result2 = playPause.applyPauseDebounce(
                pending = result1.pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = noopDecision,
                currentState = current,
                autoPauseEnabled = true,
            )
            result2.decision.shouldPause shouldBe false
            result2.pending shouldNotBe null
            result2.pending!!.confirmationsRemaining shouldBe (PlayPause.PAUSE_DEBOUNCE_SAMPLES - 1)

            // Sample 3 — confirmation 2/2, pause fires
            val result3 = playPause.applyPauseDebounce(
                pending = result2.pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = noopDecision,
                currentState = current,
                autoPauseEnabled = true,
            )
            result3.decision.shouldPause shouldBe true
            result3.pending shouldBe null
        }

        @Test
        fun `BLE_PROFILE_FALLBACK first count-up is tolerated as a rebound`() {
            // First detection: both pods removed (initialPodCount=0). A single corrupt
            // count-up sample (pod=1) should be tolerated — the helper holds pending
            // and decrements resetTolerance.
            val pending = PlayPause.PendingPauseDebounce(
                profileId = "profile",
                initialPodCount = 0,
                confirmationsRemaining = 1,
                resetTolerance = 1,
            )
            val current = EarDetectionState.fromDualPod(true, false)

            val result = playPause.applyPauseDebounce(
                pending = pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = noopDecision,
                currentState = current,
                autoPauseEnabled = true,
            )

            result.decision.shouldPause shouldBe false
            result.pending shouldNotBe null
            result.pending!!.resetTolerance shouldBe 0
            result.event shouldBe PlayPause.PauseDebounceEvent.ADVANCED
        }

        @Test
        fun `BLE_PROFILE_FALLBACK second count-up resets pending (tolerance exhausted)`() {
            // After the first rebound was tolerated, resetTolerance=0. A second count-up
            // sample resets pending — this is a genuine pod-return signal.
            val pending = PlayPause.PendingPauseDebounce(
                profileId = "profile",
                initialPodCount = 0,
                confirmationsRemaining = 1,
                resetTolerance = 0,
            )
            val current = EarDetectionState.fromDualPod(true, false)

            val result = playPause.applyPauseDebounce(
                pending = pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = noopDecision,
                currentState = current,
                autoPauseEnabled = true,
            )

            result.decision.shouldPause shouldBe false
            result.pending shouldBe null
            result.event shouldBe PlayPause.PauseDebounceEvent.RESET
        }

        @Test
        fun `BLE_PROFILE_FALLBACK raw shouldPlay clears pending`() {
            val pending = PlayPause.PendingPauseDebounce(
                profileId = "profile",
                initialPodCount = 0,
                confirmationsRemaining = 1,
            )
            val current = EarDetectionState.fromDualPod(true, true)

            val result = playPause.applyPauseDebounce(
                pending = pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = playDecision,
                currentState = current,
                autoPauseEnabled = true,
            )

            result.decision shouldBe playDecision
            result.pending shouldBe null
        }

        @Test
        fun `BLE_ANONYMOUS one-pod mode - both to one to none confirms across decreasing counts`() {
            // Sample 1: both -> one (initial pause request, podCount=1)
            val sample1Current = EarDetectionState.fromDualPod(true, false)
            val result1 = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_ANONYMOUS,
                rawDecision = pauseDecision,
                currentState = sample1Current,
                autoPauseEnabled = true,
            )
            result1.decision.shouldPause shouldBe false
            result1.pending shouldNotBe null
            result1.pending!!.initialPodCount shouldBe 1

            // Sample 2: one -> none (count=0, still <= initial 1, confirms)
            val sample2Current = EarDetectionState.fromDualPod(false, false)
            val result2 = playPause.applyPauseDebounce(
                pending = result1.pending,
                profileId = "profile",
                // In one-pod mode with prev=1 curr=0, evaluateOnePodMode returns shouldPause=true
                // again — but the helper should still treat this as a confirmation, not restart.
                source = PlayPause.EarDetectionSource.BLE_ANONYMOUS,
                rawDecision = pauseDecision,
                currentState = sample2Current,
                autoPauseEnabled = true,
            )
            result2.decision.shouldPause shouldBe false
            result2.pending shouldNotBe null
            result2.pending!!.confirmationsRemaining shouldBe (PlayPause.PAUSE_DEBOUNCE_SAMPLES - 1)

            // Sample 3: still none, final confirmation
            val result3 = playPause.applyPauseDebounce(
                pending = result2.pending,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_ANONYMOUS,
                rawDecision = noopDecision,
                currentState = sample2Current,
                autoPauseEnabled = true,
            )
            result3.decision.shouldPause shouldBe true
            result3.pending shouldBe null
        }

        @Test
        fun `profile change - pending from different profile is ignored`() {
            val pending = PlayPause.PendingPauseDebounce(
                profileId = "old-profile",
                initialPodCount = 0,
                confirmationsRemaining = 1,
            )

            val result = playPause.applyPauseDebounce(
                pending = pending,
                profileId = "new-profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            // Old pending is dropped (different profileId), new pending is started for new profile
            result.decision.shouldPause shouldBe false
            result.pending shouldNotBe null
            result.pending!!.profileId shouldBe "new-profile"
            result.pending!!.confirmationsRemaining shouldBe PlayPause.PAUSE_DEBOUNCE_SAMPLES
        }

        @Test
        fun `autoPause disabled - debounce skipped, raw decision passes through`() {
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = false,
            )

            result.decision shouldBe pauseDecision
            result.pending shouldBe null
        }

        @Test
        fun `null profileId - debounce skipped`() {
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = null,
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = pauseDecision,
                currentState = EarDetectionState.fromDualPod(false, false),
                autoPauseEnabled = true,
            )

            result.decision shouldBe pauseDecision
            result.pending shouldBe null
        }

        @Test
        fun `no pause request - raw decision passes through unchanged`() {
            val result = playPause.applyPauseDebounce(
                pending = null,
                profileId = "profile",
                source = PlayPause.EarDetectionSource.BLE_PROFILE_FALLBACK,
                rawDecision = noopDecision,
                currentState = EarDetectionState.fromDualPod(true, true),
                autoPauseEnabled = true,
            )

            result.decision shouldBe noopDecision
            result.pending shouldBe null
        }
    }

    @Nested
    inner class ToEarDetectionStateTests {

        @Test
        fun `AAP EarDetection present - returns aggregate state`() {
            // With AAP EarDetection saying NOT_IN_EAR for both, the helper should return
            // not-worn aggregate regardless of any conflicting BLE per-side bits.
            val ble = mockk<DualApplePods>(relaxed = true) {
                every { isLeftPodInEar } returns true   // BLE says worn (potentially corrupt)
                every { isRightPodInEar } returns true
                every { isBeingWorn } returns true
                every { isEitherPodInEar } returns true
            }
            val aapEarDetection = AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            )
            val aap = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                settings = mapOf(AapSetting.EarDetection::class to aapEarDetection),
            )
            val device = PodDevice(
                profileId = "test",
                ble = ble,
                aap = aap,
            )

            val state = with(playPause) { device.toEarDetectionState() }

            // AAP says not worn → aggregate should be not-worn.
            state.bothInEar shouldBe false
            state.podCount shouldBe 0
        }

        @Test
        fun `AAP says worn while BLE per-side falsely says not-worn - returns worn (#557 scenario)`() {
            // The motivating bug for the per-side gap fix: AAP authoritative state says
            // pods worn, but BLE fallback bits say not-worn. AAP must win — this is the
            // scenario users hit in #557 when high-RF interference flipped BLE bits.
            val ble = mockk<DualApplePods>(relaxed = true) {
                every { isLeftPodInEar } returns false   // BLE corrupt: says not-worn
                every { isRightPodInEar } returns false
                every { isBeingWorn } returns false
                every { isEitherPodInEar } returns false
                every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
            }
            val aapEarDetection = AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
            )
            val aapPrimary = AapSetting.PrimaryPod(pod = AapSetting.PrimaryPod.Pod.LEFT)
            val aap = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                settings = mapOf(
                    AapSetting.EarDetection::class to aapEarDetection,
                    AapSetting.PrimaryPod::class to aapPrimary,
                ),
            )
            val device = PodDevice(
                profileId = "test",
                ble = ble,
                aap = aap,
            )

            val state = with(playPause) { device.toEarDetectionState() }

            // AAP says worn → aggregate must reflect worn even though BLE bits say not-worn.
            state.bothInEar shouldBe true
            state.podCount shouldBe 2
        }

        @Test
        fun `AAP absent - falls back to BLE per-side`() {
            val ble = mockk<DualApplePods>(relaxed = true) {
                every { isLeftPodInEar } returns true
                every { isRightPodInEar } returns false
                every { isBeingWorn } returns false
                every { isEitherPodInEar } returns true
                every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
            }
            val device = PodDevice(
                profileId = "test",
                ble = ble,
                aap = null,
            )

            val state = with(playPause) { device.toEarDetectionState() }

            // BLE per-side: left=true, right=false → podCount=1
            state.leftInEar shouldBe true
            state.rightInEar shouldBe false
            state.podCount shouldBe 1
        }

        @Test
        fun `monitor key freshness field differs across BLE scans for unauthenticated source`() {
            // Repeated identical not-worn samples must produce distinct monitor keys when
            // bleKeyState is NONE / profile-fallback, so monitor distinct filtering doesn't
            // collapse them and the debounce counter can advance.
            val now = java.time.Instant.parse("2026-01-01T00:00:00Z")
            val ble1 = mockk<DualApplePods>(relaxed = true) {
                every { meta } returns eu.darken.capod.pods.core.apple.ble.devices.ApplePods.AppleMeta(
                    isIRKMatch = false,
                    profile = null,
                )
                every { seenLastAt } returns now
                every { isLeftPodInEar } returns false
                every { isRightPodInEar } returns false
                every { isBeingWorn } returns false
                every { isEitherPodInEar } returns false
            }
            val ble2 = mockk<DualApplePods>(relaxed = true) {
                every { meta } returns eu.darken.capod.pods.core.apple.ble.devices.ApplePods.AppleMeta(
                    isIRKMatch = false,
                    profile = null,
                )
                every { seenLastAt } returns now.plusMillis(1000)  // 1 second later, identical state
                every { isLeftPodInEar } returns false
                every { isRightPodInEar } returns false
                every { isBeingWorn } returns false
                every { isEitherPodInEar } returns false
            }
            val device1 = PodDevice(profileId = "test", ble = ble1, aap = null)
            val device2 = PodDevice(profileId = "test", ble = ble2, aap = null)

            val key1 = with(playPause) { device1.toPlayPauseMonitorKey() }
            val key2 = with(playPause) { device2.toPlayPauseMonitorKey() }

            // Same wear state but different seenLastAt → distinct keys (debounce can advance)
            (key1 == key2) shouldBe false
            key1.source shouldBe PlayPause.EarDetectionSource.BLE_ANONYMOUS
            key2.source shouldBe PlayPause.EarDetectionSource.BLE_ANONYMOUS
        }

        @Test
        fun `monitor key freshness field is null for IRK-authenticated source`() {
            // For IRK_MATCH source, debounceFreshness should be null so that battery-only
            // updates with identical wear state still collapse via monitor distinct filtering.
            val now = java.time.Instant.parse("2026-01-01T00:00:00Z")
            val profile = mockk<eu.darken.capod.profiles.core.AppleDeviceProfile>(relaxed = true)
            val ble = mockk<DualApplePods>(relaxed = true) {
                every { meta } returns eu.darken.capod.pods.core.apple.ble.devices.ApplePods.AppleMeta(
                    isIRKMatch = true,
                    profile = profile,
                )
                every { seenLastAt } returns now
                every { isLeftPodInEar } returns false
                every { isRightPodInEar } returns false
                every { isBeingWorn } returns false
                every { isEitherPodInEar } returns false
            }
            val device = PodDevice(profileId = "test", ble = ble, aap = null)

            val key = with(playPause) { device.toPlayPauseMonitorKey() }

            key.source shouldBe PlayPause.EarDetectionSource.BLE_IRK_MATCH
            key.debounceFreshness shouldBe null
        }

        @Test
        fun `AAP EarDetection present and primary pod resolved - aggregate matches per-side`() {
            // When AAP has both EarDetection AND primary pod resolved, aggregate and per-side
            // should agree. Verify the helper still returns aggregate (not per-side) since
            // both are equivalent.
            val aapEarDetection = AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
            )
            val aapPrimary = AapSetting.PrimaryPod(pod = AapSetting.PrimaryPod.Pod.LEFT)
            val aap = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                settings = mapOf(
                    AapSetting.EarDetection::class to aapEarDetection,
                    AapSetting.PrimaryPod::class to aapPrimary,
                ),
            )
            val ble = mockk<DualApplePods>(relaxed = true) {
                every { isLeftPodInEar } returns true
                every { isRightPodInEar } returns true
                every { isBeingWorn } returns true
                every { isEitherPodInEar } returns true
                every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
            }
            val device = PodDevice(
                profileId = "test",
                ble = ble,
                aap = aap,
            )

            val state = with(playPause) { device.toEarDetectionState() }

            state.bothInEar shouldBe true
            state.podCount shouldBe 2
        }
    }

    @Nested
    inner class MonitorFlowTests {

        private fun buildBle(seenAt: Instant, leftWorn: Boolean, rightWorn: Boolean) =
            mockk<DualApplePods>(relaxed = true) {
                every { meta } returns ApplePods.AppleMeta(
                    isIRKMatch = false,
                    profile = mockk(relaxed = true),
                )
                every { seenLastAt } returns seenAt
                every { isLeftPodInEar } returns leftWorn
                every { isRightPodInEar } returns rightWorn
                every { isBeingWorn } returns (leftWorn && rightWorn)
                every { isEitherPodInEar } returns (leftWorn || rightWorn)
                every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
                every { model } returns PodModel.AIRPODS_PRO3
            }

        // Default `startMusicOnWear = true` preserves the cold-wear semantics most existing
        // flow tests describe (autoplay fires on pod-in even without a CAP-pause history).
        // The bug-repro test that asserts the new default behavior overrides with `false`.
        private fun buildDevice(
            seenAt: Instant,
            leftWorn: Boolean,
            rightWorn: Boolean,
            startMusicOnWear: Boolean = true,
        ) = PodDevice(
            profileId = "test-profile",
            ble = buildBle(seenAt, leftWorn, rightWorn),
            aap = null,
            profileModel = PodModel.AIRPODS_PRO3,
            reactions = ReactionConfig(
                autoPlay = true,
                autoPause = true,
                startMusicOnWear = startMusicOnWear,
            ),
        )

        @Test
        fun `flow - 3 consecutive not-worn unauthenticated samples fire pause exactly once`() = runTest {
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns true
                every { wasRecentlyPausedByCap } returns false
                coEvery { sendPause() } returns true
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: worn baseline
            deviceFlow.value = listOf(buildDevice(now, leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            // T1, T2, T3: identical not-worn samples with incrementing seenLastAt.
            // Without the freshness fix, monitor distinct filtering would collapse T2 and T3
            // and the debounce counter could never advance. With it, the helper sees
            // 3 samples and commits the pause on the third.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(1000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()
            deviceFlow.value = listOf(buildDevice(now.plusMillis(2000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()
            deviceFlow.value = listOf(buildDevice(now.plusMillis(3000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            coVerify(exactly = 1) { mediaControl.sendPause() }

            job.cancel()
        }

        @Test
        fun `flow - stable worn rebound resets stale pause debounce before a new removal sequence`() = runTest {
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns true
                every { wasRecentlyPausedByCap } returns false
                coEvery { sendPause() } returns true
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: worn baseline
            deviceFlow.value = listOf(buildDevice(now, leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            // T1: corrupt not-worn sample starts pause debounce.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(1000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            // T2/T3: the pods are stably worn again. T2 consumes resetTolerance, and T3
            // must still reach applyPauseDebounce so the stale pending state is cleared.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(2000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()
            deviceFlow.value = listOf(buildDevice(now.plusMillis(3000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            // T4/T5: a later real removal has only two not-worn samples so far. If T3 was
            // collapsed, stale pending from T1 would incorrectly commit a pause here.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(4000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()
            deviceFlow.value = listOf(buildDevice(now.plusMillis(5000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPause() }

            // T6: the new removal sequence reaches three consecutive not-worn samples.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(6000), leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            coVerify(exactly = 1) { mediaControl.sendPause() }

            job.cancel()
        }

        private fun buildIrkMatchedBle(seenAt: Instant, leftWorn: Boolean, rightWorn: Boolean) =
            mockk<DualApplePods>(relaxed = true) {
                every { meta } returns ApplePods.AppleMeta(
                    isIRKMatch = true,
                    profile = mockk(relaxed = true),
                )
                every { seenLastAt } returns seenAt
                every { isLeftPodInEar } returns leftWorn
                every { isRightPodInEar } returns rightWorn
                every { isBeingWorn } returns (leftWorn && rightWorn)
                every { isEitherPodInEar } returns (leftWorn || rightWorn)
                every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
                every { model } returns PodModel.AIRPODS_PRO3
            }

        private fun buildIrkMatchedDevice(
            seenAt: Instant,
            leftWorn: Boolean,
            rightWorn: Boolean,
            startMusicOnWear: Boolean = true,
        ) = PodDevice(
            profileId = "test-profile",
            ble = buildIrkMatchedBle(seenAt, leftWorn, rightWorn),
            aap = null,
            profileModel = PodModel.AIRPODS_PRO3,
            reactions = ReactionConfig(
                autoPlay = true,
                autoPause = true,
                startMusicOnWear = startMusicOnWear,
            ),
        )

        private fun buildNoLiveBleDevice(startMusicOnWear: Boolean = true) =
            PodDevice(
                profileId = "test-profile",
                ble = null,
                aap = null,
                profileModel = PodModel.AIRPODS_PRO3,
                reactions = ReactionConfig(
                    autoPlay = true,
                    autoPause = true,
                    startMusicOnWear = startMusicOnWear,
                ),
            )

        @Test
        fun `flow - process start with pods worn does not fire autoplay`() = runTest {
            // App process starts while user is already wearing pods. The first emission has
            // no live BLE/AAP yet (cache-only). When live BLE arrives showing both worn,
            // the synthetic null -> false coercion in toEarDetectionState would otherwise
            // produce a fake not-worn -> worn transition and fire autoplay. With the
            // NO_LIVE_BLE-previous guard, the reaction must be suppressed.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns false
                every { wasRecentlyPausedByCap } returns false
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: process-start emission — no live BLE, only cached profile state.
            deviceFlow.value = listOf(buildNoLiveBleDevice())
            advanceUntilIdle()

            // T1: live IRK-matched BLE arrives showing both pods worn. Previous emission
            // had no live evidence, so the transition must NOT fire autoplay.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now, leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPlay() }

            // T2: another live worn sample. Previous is now live worn — same wear state,
            // no transition, no action. autoplay still must not fire.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now.plusMillis(1500), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPlay() }

            job.cancel()
        }

        @Test
        fun `flow - genuine pod insertion after process start fires autoplay normally`() = runTest {
            // After the no-live-evidence baseline is replaced by a stable live not-worn
            // state, a real not-worn -> worn transition must still fire autoplay.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns false
                every { wasRecentlyPausedByCap } returns false
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: cache-only baseline.
            deviceFlow.value = listOf(buildNoLiveBleDevice())
            advanceUntilIdle()

            // T1: live IRK-matched BLE arrives, pods NOT worn.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now, leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPlay() }

            // T2: user inserts pods. Genuine not-worn -> worn transition with both
            // previous and current carrying live evidence. autoplay fires.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now.plusMillis(1500), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 1) { mediaControl.sendPlay() }

            job.cancel()
        }

        @Test
        fun `flow - mid-session BLE gap does not fire false reactions on resume`() = runTest {
            // Existing live evidence -> BLE drops out (NO_LIVE_BLE) -> BLE comes back.
            // The recovery sample's previous is NO_LIVE_BLE, so no reaction must fire
            // even if the wear state happens to differ from the synthetic baseline.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns true
                every { wasRecentlyPausedByCap } returns false
                coEvery { sendPause() } returns true
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: stable live worn baseline.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now, leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            // T1: BLE gap — device evicted from live cache, only profile state remains.
            deviceFlow.value = listOf(buildNoLiveBleDevice())
            advanceUntilIdle()

            // T2: BLE resumes with worn=true. Previous is NO_LIVE_BLE, so the recovery
            // sample must not be treated as a fresh transition.
            deviceFlow.value = listOf(buildIrkMatchedDevice(now.plusMillis(2000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPause() }
            coVerify(exactly = 0) { mediaControl.sendPlay() }

            job.cancel()
        }

        @Test
        fun `flow - IRK-matched source fires autoplay on first worn sample without confirmation`() = runTest {
            // For BLE_IRK_MATCH source, BLE-only autoplay confirmation should be skipped
            // (mirroring the pause-debounce skip). Otherwise an IRK-matched device with no
            // live AAP would stage a confirmation that never fires — the 2nd worn sample
            // has no freshness on the monitor key for IRK_MATCH and gets collapsed.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns false
                every { wasRecentlyPausedByCap } returns false
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: not-worn baseline
            deviceFlow.value = listOf(buildIrkMatchedDevice(now, leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            // T1: pods inserted. With IRK_MATCH source, autoplay must fire immediately
            // (no waiting for a 2nd sample).
            deviceFlow.value = listOf(buildIrkMatchedDevice(now.plusMillis(1000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 1) { mediaControl.sendPlay() }

            job.cancel()
        }

        @Test
        fun `flow - BLE-only autoplay confirmation fires after second worn sample`() = runTest {
            // Verifies that the freshness field passes a 2nd identical worn sample through
            // monitor distinct filtering, so applyBleOnlyPlayConfirmation can confirm a
            // staged play. This is the inverse of the pause-debounce flow test: the same
            // mechanism that lets repeated not-worn samples advance the pause counter must
            // also let repeated worn samples confirm a staged BLE-only autoplay.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns false
                every { wasRecentlyPausedByCap } returns false
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: not-worn baseline
            deviceFlow.value = listOf(buildDevice(now, leftWorn = false, rightWorn = false))
            advanceUntilIdle()

            // T1: pods inserted (transition not-worn -> worn). BLE-only path stages an
            // autoplay confirmation; sendPlay is suppressed pending a second worn sample.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(1000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPlay() }

            // T2: identical worn sample with a fresher seenLastAt. The freshness field
            // must let it through monitor distinct filtering so the staged play confirms.
            deviceFlow.value = listOf(buildDevice(now.plusMillis(2000), leftWorn = true, rightWorn = true))
            advanceUntilIdle()

            coVerify(exactly = 1) { mediaControl.sendPlay() }

            job.cancel()
        }

        @Test
        fun `flow - default suppresses autoplay when CAP did not pause`() = runTest {
            // Bug repro: while wearing pods, user manually pauses music via the phone, then
            // takes one pod out and puts it back. Auto-play must NOT fire because the user
            // (not CAP) is the one who stopped playback. Uses default ReactionConfig with
            // startMusicOnWear=false (the new default) and the IRK-matched source so the
            // BLE-only autoplay confirmation step is bypassed and the decision lands on
            // a single transition.
            val deviceFlow = MutableStateFlow<List<PodDevice>>(emptyList())
            val deviceMonitor: DeviceMonitor = mockk(relaxed = true) {
                every { devices } returns deviceFlow
            }
            val bluetoothManager: BluetoothManager2 = mockk(relaxed = true) {
                every { connectedDevices } returns flowOf(listOf(mockk(relaxed = true)))
            }
            val mediaControl: MediaControl = mockk(relaxed = true) {
                every { isPlaying } returns false
                every { wasRecentlyPausedByCap } returns false
            }
            val flowPlayPause = PlayPause(deviceMonitor, bluetoothManager, mediaControl)

            val now = Instant.parse("2026-01-01T00:00:00Z")
            val job = launch { flowPlayPause.monitor().collect {} }

            // T0: worn baseline.
            deviceFlow.value = listOf(
                buildIrkMatchedDevice(now, leftWorn = true, rightWorn = true, startMusicOnWear = false),
            )
            advanceUntilIdle()

            // T1: one pod removed. Music is already paused, so no autoPause fires.
            deviceFlow.value = listOf(
                buildIrkMatchedDevice(now.plusMillis(1000), leftWorn = true, rightWorn = false, startMusicOnWear = false),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPause() }

            // T2: pod reinserted. Auto-play must NOT fire — the user paused, not CAP, and the
            // user hasn't opted into cold-wear.
            deviceFlow.value = listOf(
                buildIrkMatchedDevice(now.plusMillis(2000), leftWorn = true, rightWorn = true, startMusicOnWear = false),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { mediaControl.sendPlay() }

            job.cancel()
        }
    }
}
