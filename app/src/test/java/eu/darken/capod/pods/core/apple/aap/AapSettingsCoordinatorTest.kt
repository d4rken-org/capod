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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlin.reflect.KClass

class AapSettingsCoordinatorTest : BaseTest() {

    private val timeSource = mockk<TimeSource> {
        every { now() } returns Instant.ofEpochMilli(1000L)
        every { currentTimeMillis() } returns 1000L
    }

    private fun createCoordinator() = AapSettingsCoordinator(timeSource)

    private fun stateWithSetting(vararg settings: Pair<KClass<out AapSetting>, AapSetting>): AapPodState {
        val map = settings.toMap()
        return AapPodState(connectionState = AapPodState.ConnectionState.READY, settings = map)
    }

    @Nested
    inner class QueueTests {

        @Test
        fun `enqueue returns snapshot with correct count`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(enabled = false),
            )

            val result = coord.enqueue(emptyList(), AapCommand.SetConversationalAwareness(true), state)

            result.snapshot.count shouldBe 1
            result.snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `enqueue ANC returns pendingAncMode in snapshot`() {
            val coord = createCoordinator()

            val result = coord.enqueue(emptyList(), AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), stateWithSetting())

            result.optimisticState.shouldBeNull()
            result.snapshot.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
            result.snapshot.count shouldBe 1
        }

        @Test
        fun `enqueue same command class overwrites previous`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetToneVolume(60), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetToneVolume(80), state)

            second.snapshot.count shouldBe 1
            second.optimisticState.shouldNotBeNull()
            second.optimisticState!!.setting<AapSetting.ToneVolume>()!!.level shouldBe 80
        }

        @Test
        fun `switching away from ADAPTIVE removes queued SetAdaptiveAudioNoise`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetAdaptiveAudioNoise(70), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)

            second.snapshot.count shouldBe 1
            second.snapshot.pendingAncMode shouldBe AapSetting.AncMode.Value.ON
        }

        @Test
        fun `switching to ADAPTIVE keeps queued SetAdaptiveAudioNoise`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetAdaptiveAudioNoise(70), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), state)

            second.snapshot.count shouldBe 2
        }

        @Test
        fun `enqueue returns optimistic state for non-ANC commands`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.VolumeSwipe::class to AapSetting.VolumeSwipe(enabled = false),
            )

            val result = coord.enqueue(emptyList(), AapCommand.SetVolumeSwipe(true), state)

            result.optimisticState.shouldNotBeNull()
            result.optimisticState!!.setting<AapSetting.VolumeSwipe>()!!.enabled shouldBe true
        }

        @Test
        fun `flush empty queue returns empty list and zero snapshot`() {
            val coord = createCoordinator()

            val result = coord.flush(emptyList())

            result.commands.shouldBeEmpty()
            result.snapshot.count shouldBe 0
            result.snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `flush sorts ANC mode first`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetToneVolume(80), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), state)
            val result = coord.flush(second.pendingCommands)

            result.commands shouldHaveSize 2
            result.commands[0].shouldBeInstanceOf<AapCommand.SetAncMode>()
            result.commands[1].shouldBeInstanceOf<AapCommand.SetToneVolume>()
            result.pendingCommands.shouldBeEmpty()
        }

        @Test
        fun `flush sorts AllowOffOption before AncMode before others`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetToneVolume(80), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetAncMode(AapSetting.AncMode.Value.OFF), state)
            val third = coord.enqueue(second.pendingCommands, AapCommand.SetAllowOffOption(true), state)
            val result = coord.flush(third.pendingCommands)

            result.commands shouldHaveSize 3
            result.commands[0].shouldBeInstanceOf<AapCommand.SetAllowOffOption>()
            result.commands[1].shouldBeInstanceOf<AapCommand.SetAncMode>()
            result.commands[2].shouldBeInstanceOf<AapCommand.SetToneVolume>()
        }

        @Test
        fun `removeFromQueue returns updated snapshot`() {
            val coord = createCoordinator()
            val state = stateWithSetting(
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 50),
            )

            val first = coord.enqueue(emptyList(), AapCommand.SetAncMode(AapSetting.AncMode.Value.ON), state)
            val second = coord.enqueue(first.pendingCommands, AapCommand.SetToneVolume(80), state)
            val result = coord.removeFromQueue(second.pendingCommands, AapCommand.SetAncMode::class)

            result.snapshot.count shouldBe 1
            result.snapshot.pendingAncMode.shouldBeNull()
        }

        @Test
        fun `clear returns empty snapshot`() {
            val coord = createCoordinator()

            val result = coord.clear()

            result.snapshot.count shouldBe 0
            result.snapshot.pendingAncMode.shouldBeNull()
            result.pendingCommands.shouldBeEmpty()
        }
    }

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
            val state = stateWithSetting()

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

    @Nested
    inner class VerificationPredicateTests {

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
    }
}
