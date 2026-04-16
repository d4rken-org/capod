package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.common.TimeSource
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlin.reflect.KClass

class AapSessionEngineTest : BaseTest() {

    private val timeSource = mockk<TimeSource> {
        every { now() } returns Instant.ofEpochMilli(1000L)
        every { currentTimeMillis() } returns 1000L
    }

    private fun dummyMessage(commandType: Int = 0x0009): AapMessage {
        val header = byteArrayOf(0x04, 0x00, 0x02, 0x00)
        val cmdBytes = byteArrayOf((commandType and 0xFF).toByte(), ((commandType shr 8) and 0xFF).toByte())
        val raw = header + cmdBytes
        return AapMessage(raw = raw, commandType = commandType, payload = ByteArray(0))
    }

    @Suppress("UNCHECKED_CAST")
    private fun settingPair(setting: AapSetting): Pair<KClass<out AapSetting>, AapSetting> =
        setting::class as KClass<out AapSetting> to setting

    /** Build a profile mock with all decode methods stubbed. Does NOT mock encodeCommand (sealed class). */
    private fun mockProfile(block: AapDeviceProfile.() -> Unit = {}): AapDeviceProfile = mockk {
        every { decodeStemPress(any()) } returns null
        every { decodeBattery(any()) } returns null
        every { decodePrivateKeyResponse(any()) } returns null
        every { decodeDeviceInfo(any()) } returns null
        every { decodeSetting(any()) } returns null
        every { encodeHandshake() } returns ByteArray(10)
        every { encodeNotificationEnable() } returns emptyList()
        every { encodeInitExt() } returns ByteArray(10)
        every { encodePrivateKeyRequest() } returns null
        block()
    }

    private fun createEngine(
        profile: AapDeviceProfile = mockProfile(),
    ): AapSessionEngine = AapSessionEngine(profile, timeSource)

    private fun AapSessionEngine.startReady(scope: TestScope) {
        start(scope)
        onHandshakeSent()
        // Non-0x0009 message triggers HANDSHAKING → READY
        processMessage(dummyMessage(commandType = 0x0002))
    }

    // ── Lifecycle ───────────────────────────────────────────

    @Nested
    inner class LifecycleTests {

        @Test
        fun `start sets CONNECTING`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.CONNECTING
        }

        @Test
        fun `onHandshakeSent sets HANDSHAKING`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)
            engine.onHandshakeSent()

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.HANDSHAKING
        }

        @Test
        fun `reset clears to DISCONNECTED`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)
            engine.onHandshakeSent()
            engine.reset()

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
        }

        @Test
        fun `reset is idempotent`() {
            val engine = createEngine()

            engine.reset()
            engine.reset()

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
        }

        @Test
        fun `reset cancels pending runtime timers`() = runTest(UnconfinedTestDispatcher()) {
            val supportedModes = listOf(
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.TRANSPARENCY,
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)
            engine.onHandshakeSent()

            nextSetting = settingPair(AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ))
            engine.processMessage(dummyMessage(commandType = 0x0002))

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ON,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.TRANSPARENCY,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            engine.reset()
            advanceTimeBy(1600L)

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
            engine.state.value.setting<AapSetting.AncMode>().shouldBeNull()
        }

        @Test
        fun `first non-0x0009 message transitions HANDSHAKING to READY`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)
            engine.onHandshakeSent()
            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.HANDSHAKING

            engine.processMessage(dummyMessage(commandType = 0x0002))

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.READY
        }

        @Test
        fun `0x0009 message does NOT trigger READY transition`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)
            engine.onHandshakeSent()

            engine.processMessage(dummyMessage(commandType = 0x0009))

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.HANDSHAKING
        }
    }

    // ── Send path ───────────────────────────────────────────

    @Nested
    inner class SendPathTests {

        @Test
        fun `send queues when no pod in ear`() = runTest(UnconfinedTestDispatcher()) {
            val engine = createEngine()
            engine.startReady(this as TestScope)

            // Put ear detection showing pods in case
            val earState = engine.state.value.withSetting(
                AapSetting.EarDetection::class,
                AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
            )
            // We need to set the state with ear detection — do it by processing a setting message
            val profile = mockProfile {
                every { decodeSetting(any()) } returns (AapSetting.EarDetection::class as KClass<out AapSetting> to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                ))
            }
            val engineWithEar = AapSessionEngine(profile, timeSource)
            engineWithEar.startReady(this as TestScope)

            // Force ear detection into state via processMessage
            engineWithEar.processMessage(dummyMessage())

            var sendCount = 0
            engineWithEar.send(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE)) { sendCount++ }

            sendCount shouldBe 0 // Not sent, queued
            engineWithEar.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
            engineWithEar.state.value.pendingSettingsCount shouldBe 1
        }

        @Test
        fun `send immediate when pod in ear`() = runTest(UnconfinedTestDispatcher()) {
            val profile = mockProfile {
                every { decodeSetting(any()) } returns (AapSetting.EarDetection::class as KClass<out AapSetting> to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ))
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.startReady(this as TestScope)
            engine.processMessage(dummyMessage()) // Set ear detection

            var sentCommands = mutableListOf<AapCommand>()
            engine.send(AapCommand.SetConversationalAwareness(true)) { sentCommands.add(it) }

            sentCommands.size shouldBe 1
            engine.state.value.pendingSettingsCount shouldBe 0
        }

        @Test
        fun `flush with ANC plus later setting keeps ANC recent and verifies ANC first`() = runTest(UnconfinedTestDispatcher()) {
            val ancSetting = AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ON,
                supported = listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.ADAPTIVE),
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.startReady(this as TestScope)

            nextSetting = AapSetting.EarDetection::class as KClass<out AapSetting> to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
            )
            engine.processMessage(dummyMessage())

            nextSetting = AapSetting.AncMode::class as KClass<out AapSetting> to ancSetting
            engine.processMessage(dummyMessage())

            nextSetting =
                AapSetting.ConversationalAwareness::class as KClass<out AapSetting> to
                    AapSetting.ConversationalAwareness(enabled = false)
            engine.processMessage(dummyMessage())

            val sentCommands = mutableListOf<AapCommand>()
            val sendRaw: suspend (AapCommand) -> Unit = { sentCommands += it }

            engine.send(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE), sendRaw)
            engine.send(AapCommand.SetConversationalAwareness(true), sendRaw)

            engine.state.value.pendingSettingsCount shouldBe 2
            engine.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE

            nextSetting = AapSetting.EarDetection::class as KClass<out AapSetting> to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            )
            engine.processMessage(dummyMessage())
            runCurrent()

            sentCommands.size shouldBe 2
            sentCommands[0] shouldBe AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE)
            sentCommands[1] shouldBe AapCommand.SetConversationalAwareness(true)
            engine.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE

            nextSetting = AapSetting.AncMode::class as KClass<out AapSetting> to
                ancSetting.copy(current = AapSetting.AncMode.Value.ON)
            engine.processMessage(dummyMessage())

            // This would still be ADAPTIVE if the mixed flush path lost the ANC send marker and debounced.
            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON
            engine.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE

            advanceTimeBy(1100L)

            sentCommands.size shouldBe 3
            sentCommands[2] shouldBe AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE)
            engine.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
        }
    }

    // ── Message processing — state merge ────────────────────

    @Nested
    inner class MessageProcessingTests {

        @Test
        fun `battery decode merges into state and filters DISCONNECTED`() {
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns null
                every { decodeBattery(any()) } returns mapOf(
                    AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.85f, AapPodState.ChargingState.NOT_CHARGING),
                    AapPodState.BatteryType.CASE to AapPodState.Battery(AapPodState.BatteryType.CASE, 0f, AapPodState.ChargingState.DISCONNECTED),
                )
            }
            val engine = AapSessionEngine(profile, timeSource)
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.start(scope)
            engine.onHandshakeSent()

            engine.processMessage(dummyMessage())

            engine.state.value.batteryLeft shouldBe 0.85f
            engine.state.value.batteryCase.shouldBeNull() // DISCONNECTED filtered
        }

        @Test
        fun `decoded battery message transitions HANDSHAKING to READY`() {
            val profile = mockProfile {
                every { decodeBattery(any()) } returns mapOf(
                    AapPodState.BatteryType.LEFT to AapPodState.Battery(
                        AapPodState.BatteryType.LEFT,
                        0.85f,
                        AapPodState.ChargingState.NOT_CHARGING,
                    ),
                )
            }
            val engine = AapSessionEngine(profile, timeSource)
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.start(scope)
            engine.onHandshakeSent()

            engine.processMessage(dummyMessage(commandType = 0x0002))

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.READY
            engine.state.value.batteryLeft shouldBe 0.85f
        }

        @Test
        fun `setting decode merges into state`() {
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns null
                every { decodeBattery(any()) } returns null
                every { decodePrivateKeyResponse(any()) } returns null
                every { decodeDeviceInfo(any()) } returns null
                every { decodeSetting(any()) } returns (AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 75))
            }
            val engine = AapSessionEngine(profile, timeSource)
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.start(scope)
            engine.onHandshakeSent()

            engine.processMessage(dummyMessage(commandType = 0x0002)) // triggers READY + processes setting

            engine.state.value.setting<AapSetting.ToneVolume>()!!.level shouldBe 75
        }

        @Test
        fun `stem press emits event`() = runTest(UnconfinedTestDispatcher()) {
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns StemPressEvent(
                    pressType = StemPressEvent.PressType.SINGLE,
                    bud = StemPressEvent.Bud.LEFT,
                )
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)

            var emitted: StemPressEvent? = null
            val job = launch {
                emitted = engine.stemPressEvents.first()
            }

            engine.processMessage(dummyMessage())
            job.join()

            emitted.shouldNotBeNull()
            emitted!!.pressType shouldBe StemPressEvent.PressType.SINGLE
        }
    }

    // ── Inference ───────────────────────────────────────────

    @Nested
    inner class InferenceTests {

        @Test
        fun `startup OFF while no pod is in ear does not infer AllowOffOption true`() = runTest(UnconfinedTestDispatcher()) {
            val supportedModes = listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.ADAPTIVE,
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)
            engine.onHandshakeSent()

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage(commandType = 0x0002))
            engine.state.value.setting<AapSetting.AllowOffOption>().shouldBeNull()

            nextSetting = settingPair(AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
            ))
            engine.processMessage(dummyMessage())
            advanceTimeBy(1600L)
            engine.state.value.setting<AapSetting.AllowOffOption>().shouldBeNull()

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ADAPTIVE,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())
            advanceTimeBy(1600L)
            engine.state.value.setting<AapSetting.AllowOffOption>().shouldBeNull()
        }

        @Test
        fun `stable in-ear OFF infers AllowOffOption true after delay`() = runTest(UnconfinedTestDispatcher()) {
            val supportedModes = listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.ADAPTIVE,
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)
            engine.onHandshakeSent()

            nextSetting = settingPair(AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ))
            engine.processMessage(dummyMessage(commandType = 0x0002))

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())
            engine.state.value.setting<AapSetting.AllowOffOption>().shouldBeNull()

            advanceTimeBy(1600L)
            engine.state.value.setting<AapSetting.AllowOffOption>()?.enabled shouldBe true
        }

        @Test
        fun `later non-OFF ANC update cancels pending AllowOffOption true inference`() = runTest(UnconfinedTestDispatcher()) {
            val supportedModes = listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.ADAPTIVE,
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)
            engine.onHandshakeSent()

            nextSetting = settingPair(AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ))
            engine.processMessage(dummyMessage(commandType = 0x0002))

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            advanceTimeBy(500L)
            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ADAPTIVE,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            advanceTimeBy(1600L)
            engine.state.value.setting<AapSetting.AllowOffOption>().shouldBeNull()
            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ADAPTIVE
        }

        @Test
        fun `rejected OFF command infers AllowOffOption false`() = runTest(UnconfinedTestDispatcher()) {
            val supportedModes = listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.ADAPTIVE,
            )
            var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
            val profile = mockProfile {
                every { decodeSetting(any()) } answers { nextSetting }
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.startReady(this as TestScope)

            nextSetting = settingPair(AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ))
            engine.processMessage(dummyMessage())

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ADAPTIVE,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            nextSetting = settingPair(AapSetting.AllowOffOption(enabled = true))
            engine.processMessage(dummyMessage())

            val sentCommands = mutableListOf<AapCommand>()
            engine.send(AapCommand.SetAncMode(AapSetting.AncMode.Value.OFF)) { sentCommands += it }

            nextSetting = settingPair(AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ADAPTIVE,
                supported = supportedModes,
            ))
            engine.processMessage(dummyMessage())

            advanceTimeBy(2100L)
            engine.state.value.pendingAncMode.shouldBeNull()
            engine.state.value.setting<AapSetting.AllowOffOption>()?.enabled shouldBe false
            sentCommands shouldBe listOf(
                AapCommand.SetAncMode(AapSetting.AncMode.Value.OFF),
                AapCommand.SetAncMode(AapSetting.AncMode.Value.OFF),
            )
        }
    }


@Test
fun `matching ANC echo clears pending mode without optimistic current overwrite`() = runTest(UnconfinedTestDispatcher()) {
    val supportedModes = listOf(
        AapSetting.AncMode.Value.OFF,
        AapSetting.AncMode.Value.ON,
        AapSetting.AncMode.Value.ADAPTIVE,
    )
    var nextSetting: Pair<KClass<out AapSetting>, AapSetting>? = null
    val profile = mockProfile {
        every { decodeSetting(any()) } answers { nextSetting }
    }
    val engine = AapSessionEngine(profile, timeSource)
    engine.startReady(this as TestScope)

    nextSetting = settingPair(AapSetting.EarDetection(
        primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
        secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
    ))
    engine.processMessage(dummyMessage())

    nextSetting = settingPair(AapSetting.AncMode(
        current = AapSetting.AncMode.Value.ON,
        supported = supportedModes,
    ))
    engine.processMessage(dummyMessage())

    val sentCommands = mutableListOf<AapCommand>()
    engine.send(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE)) { sentCommands += it }

    engine.state.value.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
    engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON

    nextSetting = settingPair(AapSetting.AncMode(
        current = AapSetting.AncMode.Value.ADAPTIVE,
        supported = supportedModes,
    ))
    engine.processMessage(dummyMessage())

    engine.state.value.pendingAncMode.shouldBeNull()
    engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ADAPTIVE
    sentCommands shouldBe listOf(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE))
}

    // ── ANC Debounce ────────────────────────────────────────

    @Nested
    inner class AncDebounceTests {

        @Test
        fun `first ANC mode applied immediately without debounce`() {
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns null
                every { decodeBattery(any()) } returns null
                every { decodePrivateKeyResponse(any()) } returns null
                every { decodeDeviceInfo(any()) } returns null
                every { decodeSetting(any()) } returns (AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.ON,
                    supported = listOf(AapSetting.AncMode.Value.ON),
                ))
            }
            val engine = AapSessionEngine(profile, timeSource)
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.start(scope)
            engine.onHandshakeSent()

            engine.processMessage(dummyMessage())

            // Applied immediately (first ANC mode, no debounce)
            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON
        }

        @Test
        fun `unsolicited ANC mode change is debounced`() = runTest(UnconfinedTestDispatcher()) {
            // Set up with an existing ANC mode and no recent send
            every { timeSource.currentTimeMillis() } returns 10000L // Well past any send

            val ancSetting = AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ON,
                supported = listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY),
            )
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns null
                every { decodeBattery(any()) } returns null
                every { decodePrivateKeyResponse(any()) } returns null
                every { decodeDeviceInfo(any()) } returns null
                every { decodeSetting(any()) } returns (AapSetting.AncMode::class as KClass<out AapSetting> to ancSetting.copy(current = AapSetting.AncMode.Value.TRANSPARENCY))
            }
            val engine = AapSessionEngine(profile, timeSource)
            engine.start(this as TestScope)
            engine.onHandshakeSent()

            // First message: set initial ANC mode (no debounce for first)
            every { profile.decodeSetting(any()) } returns (AapSetting.AncMode::class as KClass<out AapSetting> to ancSetting)
            engine.processMessage(dummyMessage(commandType = 0x0002)) // triggers READY + first ANC

            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON

            // Second message: unsolicited change — should be debounced
            every { profile.decodeSetting(any()) } returns (AapSetting.AncMode::class as KClass<out AapSetting> to ancSetting.copy(current = AapSetting.AncMode.Value.TRANSPARENCY))
            engine.processMessage(dummyMessage())

            // Not yet applied (debounced)
            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON

            // After 1500ms debounce
            advanceTimeBy(1600L)
            engine.state.value.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
        }
    }

    // ── HID descriptor handling ─────────────────────────────

    @Nested
    inner class HidDescriptorTests {

        private fun hidMessage(payload: ByteArray): AapMessage {
            val header = byteArrayOf(0x04, 0x00, (payload.size and 0xFF).toByte(), ((payload.size shr 8) and 0xFF).toByte())
            val cmdBytes = byteArrayOf(0x17, 0x00)
            val raw = header + cmdBytes + payload
            return AapMessage(raw = raw, commandType = 0x0017, payload = payload)
        }

        private fun hexToBytes(hex: String): ByteArray =
            hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

        @Test
        fun `0x0017 during HANDSHAKING triggers READY transition`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())

            engine.start(scope)
            engine.onHandshakeSent()
            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.HANDSHAKING

            engine.processMessage(hidMessage(hexToBytes("00 04 00 00 01 00 FF")))

            engine.state.value.connectionState shouldBe AapPodState.ConnectionState.READY
        }

        @Test
        fun `0x0017 refreshes lastMessageAt`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.startReady(scope)

            engine.state.value.lastMessageAt.shouldNotBeNull()
            val before = engine.state.value.lastMessageAt

            every { timeSource.now() } returns Instant.ofEpochMilli(5000L)

            val fill = ByteArray(65) { 0xFF.toByte() }
            val descriptor = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill
            engine.processMessage(hidMessage(descriptor))

            engine.state.value.lastMessageAt shouldBe Instant.ofEpochMilli(5000L)
        }

        @Test
        fun `malformed 0x0017 payload does not throw`() {
            val engine = createEngine()
            val scope = TestScope(UnconfinedTestDispatcher())
            engine.startReady(scope)

            engine.processMessage(hidMessage(ByteArray(0)))
            engine.processMessage(hidMessage(hexToBytes("AB CD")))
            engine.processMessage(hidMessage(hexToBytes("00 04 00 00 44 00")))
        }

        @Test
        fun `0x0017 does not run through decode pipeline`() = runTest(UnconfinedTestDispatcher()) {
            val profile = mockProfile {
                every { decodeStemPress(any()) } returns StemPressEvent(StemPressEvent.PressType.SINGLE, StemPressEvent.Bud.LEFT)
            }
            val engine = createEngine(profile)
            engine.startReady(this as TestScope)

            val collected = mutableListOf<StemPressEvent>()
            val collector = launch { engine.stemPressEvents.collect { collected.add(it) } }

            val fill = ByteArray(65) { 0xFF.toByte() }
            val descriptor = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill
            engine.processMessage(hidMessage(descriptor))
            runCurrent()

            // If 0x0017 went through the decode pipeline, decodeStemPress would fire
            // and emit a StemPressEvent. The fast-path should bypass all decode calls.
            collected.shouldBeEmpty()
            collector.cancel()
        }
    }
}
