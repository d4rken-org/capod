package eu.darken.capod.reaction.core.playpause

import eu.darken.capod.reaction.core.playpause.PlayPause.EarDetectionState
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

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
        fun `one in to both in - should play if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
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
        fun `none in to both in - should play if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
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
        fun `none in to one in - should play if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = false)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
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
        fun `one in to both in - should play if paused`() {
            val previous = EarDetectionState.fromDualPod(left = true, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
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
        fun `none in to both in - should play if not playing`() {
            val previous = EarDetectionState.fromDualPod(left = false, right = false)
            val current = EarDetectionState.fromDualPod(left = true, right = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
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

            // Step 2→3: Right reinserted (should play)
            val step3 = EarDetectionState.fromDualPod(left = true, right = true)

            val decision2to3 = playPause.evaluatePlayPauseAction(
                previous = step2,
                current = step3,
                onePodMode = true,
                isCurrentlyPlaying = false  // Music is now paused from step 2
            )

            // Expected: should play when a pod is reinserted
            decision2to3.shouldPlay shouldBe true
            decision2to3.shouldPause shouldBe false
        }

        @Test
        fun `rapid transitions - none to one to none - should play then pause`() {
            // Step 1: None → One in (should play)
            val step1 = EarDetectionState.fromDualPod(left = false, right = false)
            val step2 = EarDetectionState.fromDualPod(left = true, right = false)

            val decision1to2 = playPause.evaluatePlayPauseAction(
                previous = step1,
                current = step2,
                onePodMode = true,
                isCurrentlyPlaying = false
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
    }

    @Nested
    inner class BleConfirmationTests {

        @Test
        fun `ble-only normal mode autoplay waits for confirmation`() {
            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = false),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
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
        fun `ble-only normal mode autoplay confirms on stable follow-up state`() {
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
            )

            result.decision.shouldPlay shouldBe true
            result.decision.reason shouldBe "Normal mode: both pods in ear (confirmed by a second state update)"
            result.pending shouldBe null
            result.stagedConfirmation shouldBe false
            result.confirmedPendingPlay shouldBe true
        }

        @Test
        fun `aap-backed autoplay bypasses confirmation`() {
            val rawDecision = playPause.evaluatePlayPauseAction(
                previous = EarDetectionState.fromDualPod(left = true, right = false),
                current = EarDetectionState.fromDualPod(left = true, right = true),
                onePodMode = false,
                isCurrentlyPlaying = false,
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
        fun `aggregate normal mode - none to both - should play`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = false, isCurrentlyPlaying = false,
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
        fun `aggregate one-pod mode - none to one - should play`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = false)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = false,
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
        fun `aggregate one-pod mode - one to both - should play`() {
            val previous = EarDetectionState.fromAapAggregate(isBeingWorn = false, isEitherPodInEar = true)
            val current = EarDetectionState.fromAapAggregate(isBeingWorn = true, isEitherPodInEar = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous, current = current,
                onePodMode = true, isCurrentlyPlaying = false,
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
        fun `single pod - not worn to worn - should play if not playing`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decision = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false, // Irrelevant for single pods
                isCurrentlyPlaying = false
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
        fun `single pod - one-pod mode enabled - play behaves same as normal mode`() {
            val previous = EarDetectionState.fromSinglePod(worn = false)
            val current = EarDetectionState.fromSinglePod(worn = true)

            val decisionNormal = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = false,
                isCurrentlyPlaying = false
            )

            val decisionOnePod = playPause.evaluatePlayPauseAction(
                previous = previous,
                current = current,
                onePodMode = true,
                isCurrentlyPlaying = false
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
}
