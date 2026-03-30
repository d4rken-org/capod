package eu.darken.capod.pods.core.apple.protocol.aap

import eu.darken.capod.pods.core.PodModel
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState.BatteryType
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState.ChargingState
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.AncMode
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.AdaptiveAudioNoise
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.ConversationalAwareness
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.ConversationalAwarenessState
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.EndCallMuteMic
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.EndCallMuteMic.EndCallMode
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.EndCallMuteMic.MuteMicMode
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.NcWithOneAirPod
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.PersonalizedVolume
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.PressHoldDuration
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.PressSpeed
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.ToneVolume
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.VolumeSwipe
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting.VolumeSwipeLength
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DefaultAapDeviceProfileTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO2

    // ── Handshake ────────────────────────────────────────────

    @Test
    fun `encode handshake is 16 bytes`() {
        val handshake = profile.encodeHandshake()
        handshake.size shouldBe 16
        handshake[0] shouldBe 0x00.toByte()
        handshake[4] shouldBe 0x01.toByte()
    }

    // ── Notification Enable ──────────────────────────────────

    @Nested
    inner class NotificationEnableTests {
        @Test
        fun `returns two packets`() {
            profile.encodeNotificationEnable().size shouldBe 2
        }

        @Test
        fun `first packet has 0xef filter`() {
            val p = profile.encodeNotificationEnable()[0]
            p[4] shouldBe 0x0f.toByte()
            p[8] shouldBe 0xef.toByte()
        }

        @Test
        fun `second packet has 0xff filter`() {
            val p = profile.encodeNotificationEnable()[1]
            p[4] shouldBe 0x0f.toByte()
            p[8] shouldBe 0xff.toByte()
        }
    }

    // ── InitExt ──────────────────────────────────────────────

    @Nested
    inner class InitExtTests {
        @Test fun `returned for Pro 2`() { DefaultAapDeviceProfile(PodModel.AIRPODS_PRO2).encodeInitExt().shouldNotBeNull() }
        @Test fun `returned for Pro 3`() { DefaultAapDeviceProfile(PodModel.AIRPODS_PRO3).encodeInitExt().shouldNotBeNull() }
        @Test fun `returned for AP4 ANC`() { DefaultAapDeviceProfile(PodModel.AIRPODS_GEN4_ANC).encodeInitExt().shouldNotBeNull() }
        @Test fun `null for basic AirPods`() { DefaultAapDeviceProfile(PodModel.AIRPODS_GEN3).encodeInitExt().shouldBeNull() }
        @Test fun `null for Pro 1`() { DefaultAapDeviceProfile(PodModel.AIRPODS_PRO).encodeInitExt().shouldBeNull() }
        @Test fun `null for Max`() { DefaultAapDeviceProfile(PodModel.AIRPODS_MAX).encodeInitExt().shouldBeNull() }

        @Test
        fun `has correct command byte`() {
            profile.encodeInitExt()!![4] shouldBe 0x4d.toByte()
        }
    }

    // ── Supported ANC Modes per model ────────────────────────

    @Nested
    inner class SupportedAncModesTests {
        private fun ancModesFor(model: PodModel): List<AncMode.Value> {
            val p = DefaultAapDeviceProfile(model)
            return decodeSetting<AncMode>(settingsMessage(0x0D, 0x02)).let {
                // Can't use the outer profile, need per-model profile
                (p.decodeSetting(settingsMessage(0x0D, 0x02))!!.second as AncMode).supported
            }
        }

        @Test
        fun `Pro 2 supports ON, TRANSPARENCY, ADAPTIVE`() {
            ancModesFor(PodModel.AIRPODS_PRO2) shouldContainExactly listOf(
                AncMode.Value.ON, AncMode.Value.TRANSPARENCY, AncMode.Value.ADAPTIVE,
            )
        }

        @Test
        fun `Pro 1 supports ON, TRANSPARENCY only`() {
            ancModesFor(PodModel.AIRPODS_PRO) shouldContainExactly listOf(
                AncMode.Value.ON, AncMode.Value.TRANSPARENCY,
            )
        }

        @Test
        fun `basic AirPods have empty supported modes`() {
            ancModesFor(PodModel.AIRPODS_GEN1) shouldBe emptyList()
        }

        @Test
        fun `Max supports ON, TRANSPARENCY only`() {
            ancModesFor(PodModel.AIRPODS_MAX) shouldContainExactly listOf(
                AncMode.Value.ON, AncMode.Value.TRANSPARENCY,
            )
        }
    }

    // ── ANC Mode encode/decode ───────────────────────────────

    @Nested
    inner class AncModeTests {
        @Test fun `encode OFF`() { profile.encodeCommand(AapCommand.SetAncMode(AncMode.Value.OFF))[7] shouldBe 0x01.toByte() }
        @Test fun `encode ON`() { profile.encodeCommand(AapCommand.SetAncMode(AncMode.Value.ON))[7] shouldBe 0x02.toByte() }
        @Test fun `encode TRANSPARENCY`() { profile.encodeCommand(AapCommand.SetAncMode(AncMode.Value.TRANSPARENCY))[7] shouldBe 0x03.toByte() }
        @Test fun `encode ADAPTIVE`() { profile.encodeCommand(AapCommand.SetAncMode(AncMode.Value.ADAPTIVE))[7] shouldBe 0x04.toByte() }

        @Test fun `decode OFF`() { decodeSetting<AncMode>(settingsMessage(0x0D, 0x01)).current shouldBe AncMode.Value.OFF }
        @Test fun `decode ON`() { decodeSetting<AncMode>(settingsMessage(0x0D, 0x02)).current shouldBe AncMode.Value.ON }
        @Test fun `decode TRANSPARENCY`() { decodeSetting<AncMode>(settingsMessage(0x0D, 0x03)).current shouldBe AncMode.Value.TRANSPARENCY }
        @Test fun `decode ADAPTIVE`() { decodeSetting<AncMode>(settingsMessage(0x0D, 0x04)).current shouldBe AncMode.Value.ADAPTIVE }
        @Test fun `decode unknown wire value returns null`() { profile.decodeSetting(settingsMessage(0x0D, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all modes`() {
            for (mode in AncMode.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetAncMode(mode))
                val decoded = decodeSetting<AncMode>(AapMessage.parse(encoded)!!)
                decoded.current shouldBe mode
            }
        }
    }

    // ── Conversational Awareness ─────────────────────────────

    @Nested
    inner class ConversationalAwarenessTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetConversationalAwareness(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetConversationalAwareness(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<ConversationalAwareness>(settingsMessage(0x28, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<ConversationalAwareness>(settingsMessage(0x28, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown value returns null`() { profile.decodeSetting(settingsMessage(0x28, 0x00)).shouldBeNull() }
    }

    // ── Press Speed ──────────────────────────────────────────

    @Nested
    inner class PressSpeedTests {
        @Test fun `encode default`() { profile.encodeCommand(AapCommand.SetPressSpeed(PressSpeed.Value.DEFAULT))[7] shouldBe 0x00.toByte() }
        @Test fun `encode slower`() { profile.encodeCommand(AapCommand.SetPressSpeed(PressSpeed.Value.SLOWER))[7] shouldBe 0x01.toByte() }
        @Test fun `encode slowest`() { profile.encodeCommand(AapCommand.SetPressSpeed(PressSpeed.Value.SLOWEST))[7] shouldBe 0x02.toByte() }
        @Test fun `decode default`() { decodeSetting<PressSpeed>(settingsMessage(0x17, 0x00)).value shouldBe PressSpeed.Value.DEFAULT }
        @Test fun `decode slower`() { decodeSetting<PressSpeed>(settingsMessage(0x17, 0x01)).value shouldBe PressSpeed.Value.SLOWER }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x17, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all values`() {
            for (v in PressSpeed.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetPressSpeed(v))
                decodeSetting<PressSpeed>(AapMessage.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── Press & Hold Duration ────────────────────────────────

    @Nested
    inner class PressHoldDurationTests {
        @Test fun `encode shorter`() { profile.encodeCommand(AapCommand.SetPressHoldDuration(PressHoldDuration.Value.SHORTER))[7] shouldBe 0x01.toByte() }
        @Test fun `decode shortest`() { decodeSetting<PressHoldDuration>(settingsMessage(0x18, 0x02)).value shouldBe PressHoldDuration.Value.SHORTEST }

        @Test
        fun `round-trip all values`() {
            for (v in PressHoldDuration.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetPressHoldDuration(v))
                decodeSetting<PressHoldDuration>(AapMessage.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── NC with One AirPod ───────────────────────────────────

    @Nested
    inner class NcOneAirPodTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetNcWithOneAirPod(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetNcWithOneAirPod(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<NcWithOneAirPod>(settingsMessage(0x1B, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<NcWithOneAirPod>(settingsMessage(0x1B, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x1B, 0x00)).shouldBeNull() }
    }

    // ── Tone Volume ──────────────────────────────────────────

    @Nested
    inner class ToneVolumeTests {
        @Test fun `encode level 50`() { profile.encodeCommand(AapCommand.SetToneVolume(50))[7] shouldBe 50.toByte() }
        @Test fun `encode clamps to min 15`() { profile.encodeCommand(AapCommand.SetToneVolume(0))[7] shouldBe 0x0F.toByte() }
        @Test fun `encode clamps to max 100`() { profile.encodeCommand(AapCommand.SetToneVolume(200))[7] shouldBe 0x64.toByte() }
        @Test fun `decode level`() { decodeSetting<ToneVolume>(settingsMessage(0x1F, 50)).level shouldBe 50 }
    }

    // ── Volume Swipe Length ──────────────────────────────────

    @Nested
    inner class VolumeSwipeLengthTests {
        @Test fun `encode longest`() { profile.encodeCommand(AapCommand.SetVolumeSwipeLength(VolumeSwipeLength.Value.LONGEST))[7] shouldBe 0x02.toByte() }
        @Test fun `decode longer`() { decodeSetting<VolumeSwipeLength>(settingsMessage(0x23, 0x01)).value shouldBe VolumeSwipeLength.Value.LONGER }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x23, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all values`() {
            for (v in VolumeSwipeLength.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetVolumeSwipeLength(v))
                decodeSetting<VolumeSwipeLength>(AapMessage.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── Volume Swipe ─────────────────────────────────────────

    @Nested
    inner class VolumeSwipeTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetVolumeSwipe(true))[7] shouldBe 0x01.toByte() }
        @Test fun `decode disabled`() { decodeSetting<VolumeSwipe>(settingsMessage(0x25, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x25, 0x00)).shouldBeNull() }
    }

    // ── Personalized Volume ──────────────────────────────────

    @Nested
    inner class PersonalizedVolumeTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetPersonalizedVolume(true))[7] shouldBe 0x01.toByte() }
        @Test fun `decode disabled`() { decodeSetting<PersonalizedVolume>(settingsMessage(0x26, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x26, 0x00)).shouldBeNull() }
    }

    // ── Adaptive Audio Noise ─────────────────────────────────

    @Nested
    inner class AdaptiveAudioNoiseTests {
        @Test fun `encode level 50`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(50))[7] shouldBe 50.toByte() }
        @Test fun `encode clamps to 0`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(-5))[7] shouldBe 0x00.toByte() }
        @Test fun `encode clamps to 100`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(150))[7] shouldBe 0x64.toByte() }
        @Test fun `decode level`() { decodeSetting<AdaptiveAudioNoise>(settingsMessage(0x2E, 64)).level shouldBe 64 }
    }

    // ── EndCall / MuteMic ────────────────────────────────────

    @Nested
    inner class EndCallMuteMicTests {
        @Test
        fun `encode single press mute, double press end call`() {
            val bytes = profile.encodeCommand(AapCommand.SetEndCallMuteMic(MuteMicMode.SINGLE_PRESS, EndCallMode.DOUBLE_PRESS))
            bytes[6] shouldBe 0x24.toByte()
            bytes[7] shouldBe 0x21.toByte()
            bytes[8] shouldBe 0x23.toByte()
            bytes[9] shouldBe 0x02.toByte()
        }

        @Test
        fun `decode standard format 0x21`() {
            val ecm = decodeSetting<EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 21 22 03 00"))
            ecm.muteMic shouldBe MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode compact format 0x20 combined 0x02`() {
            val ecm = decodeSetting<EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 20 02 00 00"))
            ecm.muteMic shouldBe MuteMicMode.SINGLE_PRESS
            ecm.endCall shouldBe EndCallMode.DOUBLE_PRESS
        }

        @Test
        fun `decode compact format 0x20 combined 0x03`() {
            val ecm = decodeSetting<EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 20 03 00 00"))
            ecm.muteMic shouldBe MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode compact format subtype 0x00 from real Pro 3`() {
            val ecm = decodeSetting<EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 00 03 00 00"))
            ecm.muteMic shouldBe MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode unknown combined returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 09 00 24 20 05 00 00")).shouldBeNull()
        }
    }

    // ── Conversation Awareness State (0x4B) ──────────────────

    @Nested
    inner class ConversationAwarenessStateTests {
        @Test fun `speaking start`() { decodeSetting<ConversationalAwarenessState>(aapMessage("04 00 04 00 4B 00 01")).speaking shouldBe true }
        @Test fun `speaking stop`() { decodeSetting<ConversationalAwarenessState>(aapMessage("04 00 04 00 4B 00 04")).speaking shouldBe false }
        @Test fun `empty payload returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 4B 00")).shouldBeNull() }
    }

    // ── Battery ──────────────────────────────────────────────

    @Nested
    inner class BatteryTests {
        @Test
        fun `decode single left pod`() {
            val r = profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 85, 0x02, 0x00))!!
            r[BatteryType.LEFT]!!.percent shouldBe 0.85f
            r[BatteryType.LEFT]!!.charging shouldBe ChargingState.NOT_CHARGING
        }

        @Test
        fun `decode dual pods plus case`() {
            val r = profile.decodeBattery(batteryMessage(
                0x03,
                0x02, 0x00, 90, 0x01, 0x00,
                0x04, 0x00, 80, 0x02, 0x00,
                0x08, 0x00, 50, 0x02, 0x00,
            ))!!
            r.size shouldBe 3
            r[BatteryType.RIGHT]!!.percent shouldBe 0.9f
            r[BatteryType.RIGHT]!!.charging shouldBe ChargingState.CHARGING
            r[BatteryType.LEFT]!!.percent shouldBe 0.8f
            r[BatteryType.CASE]!!.percent shouldBe 0.5f
        }

        @Test
        fun `decode headset (single)`() {
            val r = profile.decodeBattery(batteryMessage(0x01, 0x01, 0x00, 60, 0x04, 0x00))!!
            r[BatteryType.SINGLE]!!.percent shouldBe 0.6f
            r[BatteryType.SINGLE]!!.charging shouldBe ChargingState.DISCONNECTED
        }

        @Test fun `skips percent above 100`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 127, 0x02, 0x00))!! shouldBe emptyMap() }
        @Test fun `skips percent 255`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 0xFF, 0x04, 0x00))!! shouldBe emptyMap() }
        @Test fun `empty count`() { profile.decodeBattery(batteryMessage(0x00))!! shouldBe emptyMap() }
        @Test fun `skips unknown type`() { profile.decodeBattery(batteryMessage(0x01, 0x10, 0x00, 50, 0x02, 0x00))!! shouldBe emptyMap() }
        @Test fun `handles truncated entry`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 50))!! shouldBe emptyMap() }
        @Test fun `non-battery message returns null`() { profile.decodeBattery(settingsMessage(0x0D, 0x02)).shouldBeNull() }
        @Test fun `zero percent is valid`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 0, 0x01, 0x00))!![BatteryType.LEFT]!!.percent shouldBe 0f }
        @Test fun `100 percent is valid`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 100, 0x02, 0x00))!![BatteryType.LEFT]!!.percent shouldBe 1f }

        @Test
        fun `CHARGING_OPTIMIZED state 0x05`() {
            val r = profile.decodeBattery(batteryMessage(0x03, 0x04, 0x01, 80, 0x05, 0x01, 0x02, 0x01, 79, 0x05, 0x01, 0x08, 0x01, 48, 0x01, 0x01))!!
            r[BatteryType.LEFT]!!.charging shouldBe ChargingState.CHARGING_OPTIMIZED
            r[BatteryType.RIGHT]!!.charging shouldBe ChargingState.CHARGING_OPTIMIZED
            r[BatteryType.CASE]!!.charging shouldBe ChargingState.CHARGING
        }

        @Test
        fun `pods out of case, case disconnected`() {
            val r = profile.decodeBattery(batteryMessage(0x03, 0x04, 0x01, 80, 0x02, 0x01, 0x02, 0x01, 77, 0x02, 0x01, 0x08, 0x01, 0, 0x04, 0x01))!!
            r[BatteryType.CASE]!!.percent shouldBe 0f
            r[BatteryType.CASE]!!.charging shouldBe ChargingState.DISCONNECTED
        }

        @Test
        fun `real 22-byte message from Pro 3`() {
            val msg = aapMessage("04 00 04 00 04 00 03 04 01 50 05 01 02 01 4F 05 01 08 01 30 01 01")
            val r = profile.decodeBattery(msg)!!
            r.size shouldBe 3
            r[BatteryType.LEFT]!!.percent shouldBe 0.8f
            r[BatteryType.RIGHT]!!.percent shouldBe 0.79f
            r[BatteryType.CASE]!!.percent shouldBe 0.48f
        }
    }

    // ── Private Keys ─────────────────────────────────────────

    @Nested
    inner class PrivateKeyTests {
        @Test
        fun `encode request`() {
            val bytes = profile.encodePrivateKeyRequest()!!
            bytes[4] shouldBe 0x30.toByte()
            bytes.size shouldBe 8
        }

        @Test
        fun `decode response with IRK and ENC`() {
            val irk = ByteArray(16) { 0x11.toByte() }
            val enc = ByteArray(16) { 0x22.toByte() }
            val payload = byteArrayOf(0x02, 0x01, 0x00, 16, 0x00, *irk, 0x04, 0x00, 16, 0x00, *enc)
            val raw = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x31, 0x00) + payload
            val result = profile.decodePrivateKeyResponse(AapMessage.parse(raw)!!)!!
            result.irk shouldBe irk
            result.encKey shouldBe enc
        }

        @Test
        fun `decode response with only IRK`() {
            val irk = ByteArray(16) { 0xAA.toByte() }
            val raw = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x31, 0x00, 0x01, 0x01, 0x00, 16, 0x00, *irk)
            val result = profile.decodePrivateKeyResponse(AapMessage.parse(raw)!!)!!
            result.irk shouldBe irk
            result.encKey.shouldBeNull()
        }

        @Test fun `unknown key type returns null`() { profile.decodePrivateKeyResponse(aapMessage("04 00 04 00 31 00 01 07 00 10 00 ${" 00".repeat(16).trim()}")).shouldBeNull() }
        @Test fun `wrong key length returns null`() { profile.decodePrivateKeyResponse(aapMessage("04 00 04 00 31 00 01 01 00 08 00 ${" 00".repeat(8).trim()}")).shouldBeNull() }
        @Test fun `non-key message returns null`() { profile.decodePrivateKeyResponse(settingsMessage(0x0D, 0x02)).shouldBeNull() }
    }

    // ── Edge Cases ───────────────────────────────────────────

    @Test fun `unknown setting ID returns null`() { profile.decodeSetting(settingsMessage(0x7F, 0x01)).shouldBeNull() }
    @Test fun `non-settings command returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 1D 00 01 02 03 04")).shouldBeNull() }
    @Test fun `payload too short returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 09 00 0D")).shouldBeNull() }
}
