package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.common.TimeSource
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class AapSettingsCoordinatorTest : BaseTest() {

    private val timeSource = mockk<TimeSource> {
        every { now() } returns Instant.ofEpochMilli(1000L)
        every { currentTimeMillis() } returns 1000L
    }

    private fun createCoordinator() = AapSettingsCoordinator(timeSource)

    private fun stateWithSetting(vararg settings: Pair<kotlin.reflect.KClass<out AapSetting>, AapSetting>): AapPodState {
        val map = settings.toMap()
        return AapPodState(connectionState = AapPodState.ConnectionState.READY, settings = map)
    }

    // ── Enqueue ─────────────────────────────────────────────

    @Nested
    inner class EnqueueTests {

        @Test
        fun `enqueue returns snapshot with correct count`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(enabled = false),
            )

            val (_, snapshot) = coord.enqueue(AapCommand.SetConversationalAwareness(true), state)

            snapshot.count shouldBe 1
            snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `enqueue ANC returns pendingAncMode in snapshot`() {
            val coord = createCoordinator()
            val state = stateWithSetting()

            val (optimistic, snapshot) = coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), state)

            optimistic.shouldBeNull() // ANC uses pendingAncMode, not optimistic update
            snapshot.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
            snapshot.count shouldBe 1
        }

        @Test
        fun `enqueue same command class overwrites previous`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            coord.enqueue(AapCommand.SetToneVolume(60), state)
            val (optimistic, snapshot) = coord.enqueue(AapCommand.SetToneVolume(80), state)

            snapshot.count shouldBe 1 // Not 2
            optimistic.shouldNotBeNull()
            optimistic.setting<AapSetting.ToneVolume>()!!.level shouldBe 80
        }

        @Test
        fun `switching away from ADAPTIVE removes queued SetAdaptiveAudioNoise`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 50),
            )

            coord.enqueue(AapCommand.SetAdaptiveAudioNoise(70), state)
            val (_, snapshot) = coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)

            snapshot.count shouldBe 1 // Only ANC, noise removed
            snapshot.pendingAncMode shouldBe AapSetting.AncMode.Value.ON
        }

        @Test
        fun `switching to ADAPTIVE keeps queued SetAdaptiveAudioNoise`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 50),
            )

            coord.enqueue(AapCommand.SetAdaptiveAudioNoise(70), state)
            val (_, snapshot) = coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), state)

            snapshot.count shouldBe 2
        }

        @Test
        fun `enqueue returns optimistic state for non-ANC commands`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.VolumeSwipe::class to AapSetting.VolumeSwipe(enabled = false),
            )

            val (optimistic, _) = coord.enqueue(AapCommand.SetVolumeSwipe(true), state)

            optimistic.shouldNotBeNull()
            optimistic.setting<AapSetting.VolumeSwipe>()!!.enabled shouldBe true
        }
    }

    // ── Flush ───────────────────────────────────────────────

    @Nested
    inner class FlushTests {

        @Test
        fun `flush empty queue returns empty list and zero snapshot`() {
            val coord = createCoordinator()

            val (commands, snapshot) = coord.flush()

            commands.shouldBeEmpty()
            snapshot.count shouldBe 0
            snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `flush sorts ANC mode first`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            coord.enqueue(AapCommand.SetToneVolume(80), state)
            coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), state)

            val (commands, _) = coord.flush()

            commands shouldHaveSize 2
            commands[0].shouldBeInstanceOf<AapCommand.SetAncMode>()
            commands[1].shouldBeInstanceOf<AapCommand.SetToneVolume>()
        }

        @Test
        fun `flush clears queue`() {
            val coord = createCoordinator()
            val state = stateWithSetting()

            coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)
            coord.flush()

            val (commands, snapshot) = coord.flush()
            commands.shouldBeEmpty()
            snapshot.count shouldBe 0
        }
    }

    // ── Optimistic Update ───────────────────────────────────

    @Nested
    inner class OptimisticUpdateTests {

        @Test
        fun `ANC mode returns null`() {
            val coord = createCoordinator()
            val state = stateWithSetting()

            coord.optimisticUpdate(state, AapCommand.SetAncMode(AapSetting.AncMode.Value.ON)).shouldBeNull()
        }

        @Test
        fun `returns null when setting not yet in state`() {
            val coord = createCoordinator()
            val state = stateWithSetting() // No ToneVolume in state

            coord.optimisticUpdate(state, AapCommand.SetToneVolume(80)).shouldBeNull()
        }

        @Test
        fun `boolean toggle returns correct state`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.PersonalizedVolume::class to AapSetting.PersonalizedVolume(enabled = false),
            )

            val result = coord.optimisticUpdate(state, AapCommand.SetPersonalizedVolume(true))

            result.shouldNotBeNull()
            result.setting<AapSetting.PersonalizedVolume>()!!.enabled shouldBe true
        }

        @Test
        fun `slider value returns correct state`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 30),
            )

            val result = coord.optimisticUpdate(state, AapCommand.SetAdaptiveAudioNoise(70))

            result.shouldNotBeNull()
            result.setting<AapSetting.AdaptiveAudioNoise>()!!.level shouldBe 70
        }

        @Test
        fun `SetDeviceName updates deviceInfo`() {
            val coord = createCoordinator()
            val state = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                deviceInfo = AapDeviceInfo(
                    name = "Old Name",
                    modelNumber = "A2084",
                    manufacturer = "Apple",
                    serialNumber = "ABC123",
                    firmwareVersion = "1.0.0",
                ),
            )

            val result = coord.optimisticUpdate(state, AapCommand.SetDeviceName("New Name"))

            result.shouldNotBeNull()
            result.deviceInfo!!.name shouldBe "New Name"
        }

        @Test
        fun `does not mutate input state`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            coord.optimisticUpdate(state, AapCommand.SetToneVolume(80))

            state.setting<AapSetting.ToneVolume>()!!.level shouldBe 50
        }
    }

    // ── Verification ────────────────────────────────────────

    @Nested
    inner class VerificationTests {

        @Test
        fun `verificationFor returns null for SetDeviceName`() {
            val coord = createCoordinator()
            coord.verificationFor(AapCommand.SetDeviceName("test")).shouldBeNull()
        }

        @Test
        fun `verificationFor returns correct check for ANC mode`() {
            val coord = createCoordinator()
            val check = coord.verificationFor(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE))!!

            val matching = stateWithSetting(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.ADAPTIVE,
                    supported = listOf(AapSetting.AncMode.Value.ADAPTIVE),
                ),
            )
            val mismatched = stateWithSetting(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.ON,
                    supported = listOf(AapSetting.AncMode.Value.ON),
                ),
            )

            check(matching) shouldBe true
            check(mismatched) shouldBe false
        }

        @Test
        fun `confirmed when state matches after delay`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var outcome: AapSettingsCoordinator.VerificationOutcome? = null
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 80),
            )

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = { state },
                sendRaw = { },
                onOutcome = { outcome = it },
            )

            advanceTimeBy(1100L)
            outcome.shouldBeInstanceOf<AapSettingsCoordinator.VerificationOutcome.Confirmed>()
        }

        @Test
        fun `resends on mismatch then confirms if retry succeeds`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var outcome: AapSettingsCoordinator.VerificationOutcome? = null
            var sendCount = 0
            var currentLevel = 50 // Initially wrong

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = {
                    stateWithSetting(AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = currentLevel))
                },
                sendRaw = {
                    sendCount++
                    currentLevel = 80 // Simulate device accepting on retry
                },
                onOutcome = { outcome = it },
            )

            advanceTimeBy(1100L) // First check — mismatch, triggers resend
            sendCount shouldBe 1
            advanceTimeBy(1100L) // Second check — matches now
            outcome.shouldBeInstanceOf<AapSettingsCoordinator.VerificationOutcome.Confirmed>()
        }

        @Test
        fun `rejected after failed retry`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var outcome: AapSettingsCoordinator.VerificationOutcome? = null

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = {
                    stateWithSetting(AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50)) // Always wrong
                },
                sendRaw = { },
                onOutcome = { outcome = it },
            )

            advanceTimeBy(2200L) // Both checks fail
            outcome.shouldBeInstanceOf<AapSettingsCoordinator.VerificationOutcome.Rejected>()
        }

        @Test
        fun `aborts when no pod in ear`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var outcome: AapSettingsCoordinator.VerificationOutcome? = null
            var sendCount = 0

            val state = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                settings = mapOf(
                    AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
                    AapSetting.EarDetection::class to AapSetting.EarDetection(
                        primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                        secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                    ),
                ),
            )

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = { state },
                sendRaw = { sendCount++ },
                onOutcome = { outcome = it },
            )

            advanceTimeBy(2200L)
            sendCount shouldBe 0 // No resend attempt
            outcome.shouldBeNull() // No outcome — aborted silently
        }

        @Test
        fun `new startVerification cancels previous`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var firstOutcome: AapSettingsCoordinator.VerificationOutcome? = null
            var secondOutcome: AapSettingsCoordinator.VerificationOutcome? = null

            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 80),
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(value = AapSetting.PressSpeed.Value.DEFAULT),
            )

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = { state },
                sendRaw = { },
                onOutcome = { firstOutcome = it },
            )

            // Start second verification before first completes
            coord.startVerification(
                command = AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.DEFAULT),
                scope = this,
                stateProvider = { state },
                sendRaw = { },
                onOutcome = { secondOutcome = it },
            )

            advanceTimeBy(1100L)
            firstOutcome.shouldBeNull() // Cancelled, never completed
            secondOutcome.shouldBeInstanceOf<AapSettingsCoordinator.VerificationOutcome.Confirmed>()
        }
    }

    // ── Clear ───────────────────────────────────────────────

    @Nested
    inner class ClearTests {

        @Test
        fun `clear empties queue and returns zero snapshot`() {
            val coord = createCoordinator()
            val state = stateWithSetting()

            coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)
            val snapshot = coord.clear()

            snapshot.count shouldBe 0
            snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `clear cancels verification`() = runTest(UnconfinedTestDispatcher()) {
            val coord = createCoordinator()
            var outcome: AapSettingsCoordinator.VerificationOutcome? = null

            coord.startVerification(
                command = AapCommand.SetToneVolume(80),
                scope = this,
                stateProvider = {
                    stateWithSetting(AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 80))
                },
                sendRaw = { },
                onOutcome = { outcome = it },
            )

            coord.clear()
            advanceTimeBy(2000L)
            outcome.shouldBeNull() // Cancelled, no outcome
        }
    }

    // ── Pending State ───────────────────────────────────────

    @Nested
    inner class PendingStateTests {

        @Test
        fun `removeFromQueue returns updated snapshot`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            coord.enqueue(AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)
            coord.enqueue(AapCommand.SetToneVolume(80), state)

            val snapshot = coord.removeFromQueue(AapCommand.SetAncMode::class)

            snapshot.count shouldBe 1
            snapshot.pendingAncMode.shouldBeNull()
        }
    }
}
