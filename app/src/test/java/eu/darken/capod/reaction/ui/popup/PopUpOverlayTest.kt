package eu.darken.capod.reaction.ui.popup

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.R
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.reaction.ui.popup.PopUpOverlayState.Companion.DISMISS_VELOCITY_DP_PER_S
import eu.darken.capod.reaction.ui.popup.PopUpOverlayState.Companion.ENTER_DURATION_MS
import eu.darken.capod.reaction.ui.popup.PopUpOverlayState.Companion.EXIT_DURATION_MS
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class PopUpOverlayTest : BaseComposeRobolectricTest() {

    private val state = PopUpOverlayState()
    private val target = mutableStateOf(PopUpTarget(visible = true, token = 1))
    private val device = mutableStateOf<PodDevice>(MockPodDataProvider.dualPodMonitoredMixed())
    private val finished = mutableListOf<Long>()
    private var closes = 0

    private fun setContent() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CapodTheme {
                // Plain Box: it doesn't pass the screen's min height on to the overlay.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    PopUpOverlay(
                        device = device.value,
                        target = target.value,
                        onClose = { closes++ },
                        onExitFinished = { finished += it },
                        extraBottomPadding = 0.dp,
                        modifier = Modifier.testTag(HOST_TAG),
                        state = state,
                    )
                }
            }
        }
    }

    private fun write(block: () -> Unit) = composeRule.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    private fun shouldBeAtRest() {
        state.offsetPx shouldBe 0f
        state.alpha shouldBe 1f
    }

    private fun shouldBeHidden() {
        state.isMeasured shouldBe true
        state.offsetPx shouldBe state.hiddenOffsetPx
        state.alpha shouldBe 0f
    }

    private fun shouldBeBetweenRestAndHidden() {
        state.offsetPx shouldBeGreaterThan 0f
        state.offsetPx shouldBeLessThan state.hiddenOffsetPx
        state.alpha shouldBeGreaterThan 0f
        state.alpha shouldBeLessThan 1f
    }

    private fun enterToRest() {
        advance(ENTER_DURATION_MS + 200L)
        shouldBeAtRest()
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(HOST_TAG).performTouchInput(block)
        composeRule.waitForIdle()
    }

    /** Presses at the card's center and moves [distancePx] vertically, positive is down. */
    private fun TouchInjectionScope.pressAndMove(distancePx: Float, durationMs: Long, steps: Int = 10) {
        down(center)
        repeat(steps) {
            advanceEventTime(durationMs / steps)
            moveBy(Offset(0f, distancePx / steps))
        }
    }

    private val slowDurationMs = 1000L

    private fun fastDurationMs(distancePx: Float): Long {
        val thresholdPxPerMs = DISMISS_VELOCITY_DP_PER_S * composeRule.density.density / 1000f
        return (distancePx / (thresholdPxPerMs * 3f)).toLong().coerceAtLeast(10L)
    }

    @Test
    fun `first frame is transparent at the hidden offset`() {
        setContent()

        shouldBeHidden()
    }

    @Test
    fun `enter reaches rest after the enter duration`() {
        setContent()

        enterToRest()
        finished shouldBe emptyList()
    }

    @Test
    fun `mid-enter is between hidden and rest`() {
        setContent()

        advance(ENTER_DURATION_MS / 2L)

        shouldBeBetweenRestAndHidden()
    }

    @Test
    fun `exit passes through an intermediate state and finishes exactly once`() {
        setContent()
        enterToRest()

        write { target.value = PopUpTarget(visible = false, token = 2) }
        advance(EXIT_DURATION_MS / 2L)

        shouldBeBetweenRestAndHidden()
        finished shouldBe emptyList()

        advance(EXIT_DURATION_MS + 200L)

        shouldBeHidden()
        finished shouldBe listOf(2L)

        advance(1000L)
        finished shouldBe listOf(2L)
    }

    @Test
    fun `becoming visible mid-exit returns to rest without finishing the exit`() {
        setContent()
        enterToRest()

        var token = 10L
        val points = listOf(16L, EXIT_DURATION_MS / 4L, EXIT_DURATION_MS / 2L, EXIT_DURATION_MS - 40L)
        for (point in points) {
            write { target.value = PopUpTarget(visible = false, token = ++token) }
            advance(point)
            // The first frame only recomposes, the exit animation starts on the next one
            if (point >= 50L) state.alpha shouldBeLessThan 1f

            write { target.value = PopUpTarget(visible = true, token = ++token) }
            enterToRest()
        }

        advance(1000L)
        shouldBeAtRest()
        finished shouldBe emptyList()
    }

    @Test
    fun `hiding mid-enter reverses into the exit`() {
        setContent()
        advance(ENTER_DURATION_MS / 2L)
        shouldBeBetweenRestAndHidden()

        write { target.value = PopUpTarget(visible = false, token = 7) }
        advance(32L)
        val offsetAfterReversal = state.offsetPx
        val alphaAfterReversal = state.alpha

        advance(100L)
        state.offsetPx shouldBeGreaterThan offsetAfterReversal
        state.alpha shouldBeLessThan alphaAfterReversal
        finished shouldBe emptyList()

        advance(EXIT_DURATION_MS + 200L)
        shouldBeHidden()
        finished shouldBe listOf(7L)
    }

    @Test
    fun `swapping from dual to single layout keeps the card at rest`() {
        setContent()
        enterToRest()

        write { device.value = MockPodDataProvider.singlePodMonitored() }
        advance(100L)

        shouldBeAtRest()
        state.isMeasured shouldBe true

        advance(1000L)
        shouldBeAtRest()
        finished shouldBe emptyList()
    }

    @Test
    fun `hidden offset follows a layout swap before the enter`() {
        target.value = PopUpTarget(visible = false, token = 3)
        setContent()
        advance(EXIT_DURATION_MS + 200L)
        shouldBeHidden()

        write { device.value = MockPodDataProvider.singlePodMonitored() }
        advance(100L)

        shouldBeHidden()
    }

    @Test
    fun `short slow drag springs back to rest`() {
        setContent()
        enterToRest()

        touch { pressAndMove(state.hiddenOffsetPx * 0.15f, slowDurationMs) }
        state.offsetPx shouldBeGreaterThan 0f
        state.alpha shouldBe 1f

        touch { up() }
        advance(ENTER_DURATION_MS + 200L)

        shouldBeAtRest()
        closes shouldBe 0
        finished shouldBe emptyList()
    }

    @Test
    fun `long drag past the threshold closes once`() {
        setContent()
        enterToRest()

        touch {
            pressAndMove(state.hiddenOffsetPx * 0.6f, slowDurationMs)
            up()
        }
        closes shouldBe 1

        advance(1000L)
        closes shouldBe 1
    }

    @Test
    fun `short fast downward fling closes`() {
        setContent()
        enterToRest()

        val distance = state.hiddenOffsetPx * 0.15f
        touch {
            pressAndMove(distance, fastDurationMs(distance), steps = 5)
            up()
        }

        closes shouldBe 1
    }

    @Test
    fun `upward swipe leaves the card at rest`() {
        setContent()
        enterToRest()

        val distance = state.hiddenOffsetPx * 0.4f
        touch { pressAndMove(-distance, fastDurationMs(distance), steps = 5) }
        shouldBeAtRest()

        touch { up() }
        advance(ENTER_DURATION_MS + 200L)

        shouldBeAtRest()
        closes shouldBe 0
    }

    @Test
    fun `hiding mid-drag runs the exit to completion`() {
        setContent()
        enterToRest()

        touch { pressAndMove(state.hiddenOffsetPx * 0.15f, slowDurationMs) }
        val draggedOffset = state.offsetPx
        draggedOffset shouldBeGreaterThan 0f

        write { target.value = PopUpTarget(visible = false, token = 5) }
        advance(EXIT_DURATION_MS / 2L)
        state.offsetPx shouldBeGreaterThan draggedOffset

        touch {
            moveBy(Offset(0f, -state.hiddenOffsetPx * 0.1f))
            up()
        }
        advance(EXIT_DURATION_MS + 200L)

        shouldBeHidden()
        finished shouldBe listOf(5L)
        closes shouldBe 0

        advance(1000L)
        shouldBeHidden()
        finished shouldBe listOf(5L)
    }

    @Test
    fun `drag during the enter ends opaque at rest`() {
        setContent()
        advance(ENTER_DURATION_MS / 3L)
        state.alpha shouldBeLessThan 1f

        touch { pressAndMove(state.hiddenOffsetPx * 0.1f, 60L, steps = 3) }
        state.alpha shouldBe 1f

        touch { up() }
        advance(ENTER_DURATION_MS + 200L)

        shouldBeAtRest()
        closes shouldBe 0
    }

    @Test
    fun `tapping the close button closes`() {
        setContent()
        enterToRest()

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.general_close_action)).performClick()
        composeRule.waitForIdle()

        closes shouldBe 1
    }

    companion object {
        private const val HOST_TAG = "popup_overlay"
    }
}
