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
            podMonitor = mockk(relaxed = true),
            bluetoothManager = mockk(relaxed = true),
            reactionSettings = mockk(relaxed = true),
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
         * THE EDGE CASE: This test documents the bug in the current implementation.
         *
         * User scenario:
         * 1. Both pods in ears, music playing
         * 2. Remove right pod → should pause
         * 3. Reinsert right pod → should play
         *
         * Expected behavior:
         * - Step 1→2: shouldPause = true
         * - Step 2→3: shouldPlay = true
         *
         * Current behavior (BUG):
         * - Step 1→2: No action (eitherInEar stays true)
         * - Step 2→3: No action (eitherInEar stays true)
         *
         * This test verifies the EXPECTED behavior and will FAIL until we implement the fix.
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
            decision1to2.shouldPause shouldBe true  // THIS WILL FAIL with current implementation

            // Step 2→3: Right reinserted (should play)
            val step3 = EarDetectionState.fromDualPod(left = true, right = true)

            val decision2to3 = playPause.evaluatePlayPauseAction(
                previous = step2,
                current = step3,
                onePodMode = true,
                isCurrentlyPlaying = false  // Music is now paused from step 2
            )

            // Expected: should play when a pod is reinserted
            decision2to3.shouldPlay shouldBe true  // THIS WILL FAIL with current implementation
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
        fun `single pod - one-pod mode enabled - behaves same as normal mode`() {
            // For single-pod devices, one-pod mode doesn't change behavior
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
                onePodMode = true, // Should behave the same
                isCurrentlyPlaying = false
            )

            // Both should trigger play
            decisionNormal.shouldPlay shouldBe true
            decisionOnePod.shouldPlay shouldBe true
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
