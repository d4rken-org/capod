package eu.darken.capod.reaction.core.popup

import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import java.time.Duration
import java.time.Instant

class PopUpReactionLogicTest : BaseTest() {

    private lateinit var popUpReaction: PopUpReaction

    @BeforeEach
    fun setup() {
        popUpReaction = PopUpReaction(
            deviceMonitor = mockk(relaxed = true),
            bluetoothManager = mockk(relaxed = true),
            timeSource = TestTimeSource(),
        )
    }

    @Nested
    inner class CasePopUpTests {

        private val now = Instant.ofEpochSecond(1000)
        private val cooldown = Duration.ofSeconds(10)

        private fun evaluate(
            currentLidState: DualApplePods.LidState?,
            lastShownTime: Instant? = null,
        ) = popUpReaction.evaluateCasePopUp(
            currentLidState = currentLidState,
            lastShownTime = lastShownTime,
            now = now,
            cooldownDuration = cooldown,
        )

        @Test
        fun `lid OPEN with no cooldown (first show) - should show`() {
            val decision = evaluate(
                currentLidState = DualApplePods.LidState.OPEN,
                lastShownTime = null,
            )
            decision.shouldShow shouldBe true
            decision.shouldHide shouldBe false
        }

        @Test
        fun `lid OPEN within cooldown - should NOT show`() {
            val decision = evaluate(
                currentLidState = DualApplePods.LidState.OPEN,
                lastShownTime = now.minusSeconds(5),
            )
            decision.shouldShow shouldBe false
        }

        @Test
        fun `lid OPEN with cooldown expired - should show`() {
            val decision = evaluate(
                currentLidState = DualApplePods.LidState.OPEN,
                lastShownTime = now.minusSeconds(15),
            )
            decision.shouldShow shouldBe true
        }

        @Test
        fun `lid OPEN with cooldown exactly at boundary - should show`() {
            val decision = evaluate(
                currentLidState = DualApplePods.LidState.OPEN,
                lastShownTime = now.minusSeconds(10),
            )
            decision.shouldShow shouldBe true
        }

        @Test
        fun `lid CLOSED - should NOT show, should hide, should reset cooldown`() {
            val decision = evaluate(currentLidState = DualApplePods.LidState.CLOSED)
            decision.shouldShow shouldBe false
            decision.shouldHide shouldBe true
            decision.shouldResetCooldown shouldBe true
        }

        @Test
        fun `lid UNKNOWN - should NOT show, should hide, should NOT reset cooldown`() {
            val decision = evaluate(currentLidState = DualApplePods.LidState.UNKNOWN)
            decision.shouldShow shouldBe false
            decision.shouldHide shouldBe true
            decision.shouldResetCooldown shouldBe false
        }

        @Test
        fun `lid NOT_IN_CASE - should NOT show, should hide, should NOT reset cooldown`() {
            val decision = evaluate(currentLidState = DualApplePods.LidState.NOT_IN_CASE)
            decision.shouldShow shouldBe false
            decision.shouldHide shouldBe true
            decision.shouldResetCooldown shouldBe false
        }

        @Test
        fun `null lid state (non-DualApplePods) - should NOT show, should NOT hide`() {
            val decision = evaluate(currentLidState = null)
            decision.shouldShow shouldBe false
            decision.shouldHide shouldBe false
            decision.shouldResetCooldown shouldBe false
        }
    }

    @Nested
    inner class ConnectionPopUpTests {

        private fun evaluate(
            hasConnectedDevice: Boolean = true,
            hasPodDevice: Boolean = true,
            hasAlreadyShown: Boolean = false,
            broadcastAge: Duration = Duration.ofSeconds(5),
            connectionAge: Duration = Duration.ofSeconds(5),
        ) = popUpReaction.evaluateConnectionPopUp(
            hasConnectedDevice = hasConnectedDevice,
            hasPodDevice = hasPodDevice,
            hasAlreadyShown = hasAlreadyShown,
            broadcastAge = broadcastAge,
            connectionAge = connectionAge,
        )

        @Test
        fun `connected + pod found + within age + not shown yet - should show`() {
            evaluate().shouldShow shouldBe true
        }

        @Test
        fun `not connected - should NOT show`() {
            evaluate(hasConnectedDevice = false).shouldShow shouldBe false
        }

        @Test
        fun `connected but no pod device - should NOT show`() {
            evaluate(hasPodDevice = false).shouldShow shouldBe false
        }

        @Test
        fun `connected long ago - should NOT show`() {
            evaluate(
                broadcastAge = Duration.ofSeconds(5),
                connectionAge = Duration.ofSeconds(60),
            ).shouldShow shouldBe false
        }

        @Test
        fun `already shown for this connection - should NOT show`() {
            evaluate(hasAlreadyShown = true).shouldShow shouldBe false
        }

        @Test
        fun `broadcast device age much older than connection (false positive) - should NOT show`() {
            evaluate(
                broadcastAge = Duration.ofSeconds(60),
                connectionAge = Duration.ofSeconds(5),
            ).shouldShow shouldBe false
        }

        @Test
        fun `broadcast device age within threshold of connection - should show`() {
            evaluate(
                broadcastAge = Duration.ofSeconds(20),
                connectionAge = Duration.ofSeconds(5),
            ).shouldShow shouldBe true
        }

        @Test
        fun `broadcast device age exactly at threshold - should NOT show`() {
            evaluate(
                broadcastAge = Duration.ofSeconds(36),
                connectionAge = Duration.ofSeconds(5),
            ).shouldShow shouldBe false
        }

        @Test
        fun `negative durations (reversed Duration-between args) must still detect false positives`() {
            // Duration.between(now, pastTimestamp) produces negative durations.
            // The false-positive filter must work regardless of sign.
            evaluate(
                broadcastAge = Duration.ofSeconds(-60),
                connectionAge = Duration.ofSeconds(-5),
            ).shouldShow shouldBe false
        }
    }
}
