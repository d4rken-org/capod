package eu.darken.capod.pods.core.apple.aap.devices

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.BaseAapSessionTest
import eu.darken.capod.pods.core.apple.aap.protocol.DefaultAapDeviceProfile
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
        @Test
        fun `returned for Pro 2`() { DefaultAapDeviceProfile(PodModel.AIRPODS_PRO2).encodeInitExt().shouldNotBeNull() }
        @Test fun `returned for Pro 3`() { DefaultAapDeviceProfile(PodModel.AIRPODS_PRO3).encodeInitExt().shouldNotBeNull() }
        @Test fun `returned for AP4 ANC`() { DefaultAapDeviceProfile(PodModel.AIRPODS_GEN4_ANC).encodeInitExt().shouldNotBeNull() }
        @Test
        fun `null for basic AirPods`() { DefaultAapDeviceProfile(PodModel.AIRPODS_GEN3).encodeInitExt().shouldBeNull() }
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
        private fun ancModesFor(model: PodModel): List<AapSetting.AncMode.Value> {
            val p = DefaultAapDeviceProfile(model)
            return decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x02)).let {
                // Can't use the outer profile, need per-model profile
                (p.decodeSetting(settingsMessage(0x0D, 0x02))!!.second as AapSetting.AncMode).supported
            }
        }

        @Test
        fun `Pro 2 supports ON, TRANSPARENCY, ADAPTIVE`() {
            ancModesFor(PodModel.AIRPODS_PRO2) shouldContainExactly listOf(
                AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY, AapSetting.AncMode.Value.ADAPTIVE,
            )
        }

        @Test
        fun `Pro 1 supports ON, TRANSPARENCY only`() {
            ancModesFor(PodModel.AIRPODS_PRO) shouldContainExactly listOf(
                AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY,
            )
        }

        @Test
        fun `basic AirPods have empty supported modes`() {
            ancModesFor(PodModel.AIRPODS_GEN1) shouldBe emptyList()
        }

        @Test
        fun `Max supports ON, TRANSPARENCY only`() {
            ancModesFor(PodModel.AIRPODS_MAX) shouldContainExactly listOf(
                AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY,
            )
        }
    }

    // ── ANC Mode encode/decode ───────────────────────────────

    @Nested
    inner class AncModeTests {
        @Test fun `encode OFF`() { profile.encodeCommand(AapCommand.SetAncMode(AapSetting.AncMode.Value.OFF))[7] shouldBe 0x01.toByte() }
        @Test fun `encode ON`() { profile.encodeCommand(AapCommand.SetAncMode(AapSetting.AncMode.Value.ON))[7] shouldBe 0x02.toByte() }
        @Test fun `encode TRANSPARENCY`() { profile.encodeCommand(AapCommand.SetAncMode(AapSetting.AncMode.Value.TRANSPARENCY))[7] shouldBe 0x03.toByte() }
        @Test fun `encode ADAPTIVE`() { profile.encodeCommand(AapCommand.SetAncMode(AapSetting.AncMode.Value.ADAPTIVE))[7] shouldBe 0x04.toByte() }

        @Test
        fun `decode OFF`() { decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x01)).current shouldBe AapSetting.AncMode.Value.OFF }
        @Test
        fun `decode ON`() { decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x02)).current shouldBe AapSetting.AncMode.Value.ON }
        @Test
        fun `decode TRANSPARENCY`() { decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x03)).current shouldBe AapSetting.AncMode.Value.TRANSPARENCY }
        @Test
        fun `decode ADAPTIVE`() { decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x04)).current shouldBe AapSetting.AncMode.Value.ADAPTIVE }
        @Test fun `decode unknown wire value returns null`() { profile.decodeSetting(settingsMessage(0x0D, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all modes`() {
            for (mode in AapSetting.AncMode.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetAncMode(mode))
                val decoded = decodeSetting<AapSetting.AncMode>(AapMessage.Companion.parse(encoded)!!)
                decoded.current shouldBe mode
            }
        }
    }

    // ── Conversational Awareness ─────────────────────────────

    @Nested
    inner class ConversationalAwarenessTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetConversationalAwareness(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetConversationalAwareness(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.ConversationalAwareness>(settingsMessage(0x28, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.ConversationalAwareness>(settingsMessage(0x28, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown value returns null`() { profile.decodeSetting(settingsMessage(0x28, 0x00)).shouldBeNull() }
    }

    // ── Press Speed ──────────────────────────────────────────

    @Nested
    inner class PressSpeedTests {
        @Test fun `encode default`() { profile.encodeCommand(AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.DEFAULT))[7] shouldBe 0x00.toByte() }
        @Test fun `encode slower`() { profile.encodeCommand(AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.SLOWER))[7] shouldBe 0x01.toByte() }
        @Test fun `encode slowest`() { profile.encodeCommand(AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.SLOWEST))[7] shouldBe 0x02.toByte() }
        @Test
        fun `decode default`() { decodeSetting<AapSetting.PressSpeed>(settingsMessage(0x17, 0x00)).value shouldBe AapSetting.PressSpeed.Value.DEFAULT }
        @Test
        fun `decode slower`() { decodeSetting<AapSetting.PressSpeed>(settingsMessage(0x17, 0x01)).value shouldBe AapSetting.PressSpeed.Value.SLOWER }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x17, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all values`() {
            for (v in AapSetting.PressSpeed.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetPressSpeed(v))
                decodeSetting<AapSetting.PressSpeed>(AapMessage.Companion.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── Press & Hold Duration ────────────────────────────────

    @Nested
    inner class PressHoldDurationTests {
        @Test
        fun `encode shorter`() { profile.encodeCommand(AapCommand.SetPressHoldDuration(AapSetting.PressHoldDuration.Value.SHORTER))[7] shouldBe 0x01.toByte() }
        @Test
        fun `decode shortest`() { decodeSetting<AapSetting.PressHoldDuration>(settingsMessage(0x18, 0x02)).value shouldBe AapSetting.PressHoldDuration.Value.SHORTEST }

        @Test
        fun `round-trip all values`() {
            for (v in AapSetting.PressHoldDuration.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetPressHoldDuration(v))
                decodeSetting<AapSetting.PressHoldDuration>(AapMessage.Companion.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── NC with One AirPod ───────────────────────────────────

    @Nested
    inner class NcOneAirPodTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetNcWithOneAirPod(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetNcWithOneAirPod(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.NcWithOneAirPod>(settingsMessage(0x1B, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.NcWithOneAirPod>(settingsMessage(0x1B, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x1B, 0x00)).shouldBeNull() }
    }

    // ── Tone Volume ──────────────────────────────────────────

    @Nested
    inner class ToneVolumeTests {
        @Test fun `encode level 50`() { profile.encodeCommand(AapCommand.SetToneVolume(50))[7] shouldBe 50.toByte() }
        @Test fun `encode clamps to min 15`() { profile.encodeCommand(AapCommand.SetToneVolume(0))[7] shouldBe 0x0F.toByte() }
        @Test fun `encode clamps to max 100`() { profile.encodeCommand(AapCommand.SetToneVolume(200))[7] shouldBe 0x64.toByte() }
        @Test fun `decode level`() { decodeSetting<AapSetting.ToneVolume>(settingsMessage(0x1F, 50)).level shouldBe 50 }
    }

    // ── Volume Swipe Length ──────────────────────────────────

    @Nested
    inner class VolumeSwipeLengthTests {
        @Test
        fun `encode longest`() { profile.encodeCommand(AapCommand.SetVolumeSwipeLength(AapSetting.VolumeSwipeLength.Value.LONGEST))[7] shouldBe 0x02.toByte() }
        @Test
        fun `decode longer`() { decodeSetting<AapSetting.VolumeSwipeLength>(settingsMessage(0x23, 0x01)).value shouldBe AapSetting.VolumeSwipeLength.Value.LONGER }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x23, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all values`() {
            for (v in AapSetting.VolumeSwipeLength.Value.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetVolumeSwipeLength(v))
                decodeSetting<AapSetting.VolumeSwipeLength>(AapMessage.Companion.parse(encoded)!!).value shouldBe v
            }
        }
    }

    // ── Volume Swipe ─────────────────────────────────────────

    @Nested
    inner class VolumeSwipeTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetVolumeSwipe(true))[7] shouldBe 0x01.toByte() }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.VolumeSwipe>(settingsMessage(0x25, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x25, 0x00)).shouldBeNull() }
    }

    // ── Personalized Volume ──────────────────────────────────

    @Nested
    inner class PersonalizedVolumeTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetPersonalizedVolume(true))[7] shouldBe 0x01.toByte() }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.PersonalizedVolume>(settingsMessage(0x26, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x26, 0x00)).shouldBeNull() }
    }

    // ── Adaptive Audio Noise ─────────────────────────────────

    @Nested
    inner class AdaptiveAudioNoiseTests {
        @Test fun `encode level 50`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(50))[7] shouldBe 50.toByte() }
        @Test fun `encode clamps to 0`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(-5))[7] shouldBe 0x00.toByte() }
        @Test fun `encode clamps to 100`() { profile.encodeCommand(AapCommand.SetAdaptiveAudioNoise(150))[7] shouldBe 0x64.toByte() }
        @Test fun `decode level`() { decodeSetting<AapSetting.AdaptiveAudioNoise>(settingsMessage(0x2E, 64)).level shouldBe 64 }
    }

    // ── EndCall / MuteMic ────────────────────────────────────

    @Nested
    inner class EndCallMuteMicTests {
        @Test
        fun `encode single press mute, double press end call`() {
            val bytes = profile.encodeCommand(AapCommand.SetEndCallMuteMic(AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS, AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS))
            bytes[6] shouldBe 0x24.toByte()
            bytes[7] shouldBe 0x21.toByte()
            bytes[8] shouldBe 0x23.toByte()
            bytes[9] shouldBe 0x02.toByte()
        }

        @Test
        fun `decode standard format 0x21`() {
            val ecm = decodeSetting<AapSetting.EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 21 22 03 00"))
            ecm.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode compact format 0x20 combined 0x02`() {
            val ecm = decodeSetting<AapSetting.EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 20 02 00 00"))
            ecm.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS
            ecm.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS
        }

        @Test
        fun `decode compact format 0x20 combined 0x03`() {
            val ecm = decodeSetting<AapSetting.EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 20 03 00 00"))
            ecm.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode compact format subtype 0x00 from real Pro 3`() {
            val ecm = decodeSetting<AapSetting.EndCallMuteMic>(aapMessage("04 00 04 00 09 00 24 00 03 00 00"))
            ecm.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS
            ecm.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
        }

        @Test
        fun `decode unknown combined returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 09 00 24 20 05 00 00")).shouldBeNull()
        }
    }

    // ── Conversation Awareness State (0x4B) ──────────────────

    @Nested
    inner class ConversationAwarenessStateTests {
        @Test fun `speaking start`() { decodeSetting<AapSetting.ConversationalAwarenessState>(aapMessage("04 00 04 00 4B 00 01")).speaking shouldBe true }
        @Test fun `speaking stop`() { decodeSetting<AapSetting.ConversationalAwarenessState>(aapMessage("04 00 04 00 4B 00 04")).speaking shouldBe false }
        @Test fun `empty payload returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 4B 00")).shouldBeNull() }
    }

    // ── Battery ──────────────────────────────────────────────

    @Nested
    inner class BatteryTests {
        @Test
        fun `decode single left pod`() {
            val r = profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 85, 0x02, 0x00))!!
            r[AapPodState.BatteryType.LEFT]!!.percent shouldBe 0.85f
            r[AapPodState.BatteryType.LEFT]!!.charging shouldBe AapPodState.ChargingState.NOT_CHARGING
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
            r[AapPodState.BatteryType.RIGHT]!!.percent shouldBe 0.9f
            r[AapPodState.BatteryType.RIGHT]!!.charging shouldBe AapPodState.ChargingState.CHARGING
            r[AapPodState.BatteryType.LEFT]!!.percent shouldBe 0.8f
            r[AapPodState.BatteryType.CASE]!!.percent shouldBe 0.5f
        }

        @Test
        fun `decode headset (single)`() {
            val r = profile.decodeBattery(batteryMessage(0x01, 0x01, 0x00, 60, 0x04, 0x00))!!
            r[AapPodState.BatteryType.SINGLE]!!.percent shouldBe 0.6f
            r[AapPodState.BatteryType.SINGLE]!!.charging shouldBe AapPodState.ChargingState.DISCONNECTED
        }

        @Test fun `skips percent above 100`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 127, 0x02, 0x00))!! shouldBe emptyMap() }
        @Test fun `skips percent 255`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 0xFF, 0x04, 0x00))!! shouldBe emptyMap() }
        @Test fun `empty count`() { profile.decodeBattery(batteryMessage(0x00))!! shouldBe emptyMap() }
        @Test fun `skips unknown type`() { profile.decodeBattery(batteryMessage(0x01, 0x10, 0x00, 50, 0x02, 0x00))!! shouldBe emptyMap() }
        @Test fun `handles truncated entry`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 50))!! shouldBe emptyMap() }
        @Test fun `non-battery message returns null`() { profile.decodeBattery(settingsMessage(0x0D, 0x02)).shouldBeNull() }
        @Test
        fun `zero percent is valid`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 0, 0x01, 0x00))!![AapPodState.BatteryType.LEFT]!!.percent shouldBe 0f }
        @Test
        fun `100 percent is valid`() { profile.decodeBattery(batteryMessage(0x01, 0x04, 0x00, 100, 0x02, 0x00))!![AapPodState.BatteryType.LEFT]!!.percent shouldBe 1f }

        @Test
        fun `CHARGING_OPTIMIZED state 0x05`() {
            val r = profile.decodeBattery(batteryMessage(0x03, 0x04, 0x01, 80, 0x05, 0x01, 0x02, 0x01, 79, 0x05, 0x01, 0x08, 0x01, 48, 0x01, 0x01))!!
            r[AapPodState.BatteryType.LEFT]!!.charging shouldBe AapPodState.ChargingState.CHARGING_OPTIMIZED
            r[AapPodState.BatteryType.RIGHT]!!.charging shouldBe AapPodState.ChargingState.CHARGING_OPTIMIZED
            r[AapPodState.BatteryType.CASE]!!.charging shouldBe AapPodState.ChargingState.CHARGING
        }

        @Test
        fun `pods out of case, case disconnected`() {
            val r = profile.decodeBattery(batteryMessage(0x03, 0x04, 0x01, 80, 0x02, 0x01, 0x02, 0x01, 77, 0x02, 0x01, 0x08, 0x01, 0, 0x04, 0x01))!!
            r[AapPodState.BatteryType.CASE]!!.percent shouldBe 0f
            r[AapPodState.BatteryType.CASE]!!.charging shouldBe AapPodState.ChargingState.DISCONNECTED
        }

        @Test
        fun `real 22-byte message from Pro 3`() {
            val msg = aapMessage("04 00 04 00 04 00 03 04 01 50 05 01 02 01 4F 05 01 08 01 30 01 01")
            val r = profile.decodeBattery(msg)!!
            r.size shouldBe 3
            r[AapPodState.BatteryType.LEFT]!!.percent shouldBe 0.8f
            r[AapPodState.BatteryType.RIGHT]!!.percent shouldBe 0.79f
            r[AapPodState.BatteryType.CASE]!!.percent shouldBe 0.48f
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
            val result = profile.decodePrivateKeyResponse(AapMessage.Companion.parse(raw)!!)!!
            result.irk shouldBe irk
            result.encKey shouldBe enc
        }

        @Test
        fun `decode response with only IRK`() {
            val irk = ByteArray(16) { 0xAA.toByte() }
            val raw = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x31, 0x00, 0x01, 0x01, 0x00, 16, 0x00, *irk)
            val result = profile.decodePrivateKeyResponse(AapMessage.Companion.parse(raw)!!)!!
            result.irk shouldBe irk
            result.encKey.shouldBeNull()
        }

        @Test fun `unknown key type returns null`() { profile.decodePrivateKeyResponse(aapMessage("04 00 04 00 31 00 01 07 00 10 00 ${" 00".repeat(16).trim()}")).shouldBeNull() }
        @Test fun `wrong key length returns null`() { profile.decodePrivateKeyResponse(aapMessage("04 00 04 00 31 00 01 01 00 08 00 ${" 00".repeat(8).trim()}")).shouldBeNull() }
        @Test fun `non-key message returns null`() { profile.decodePrivateKeyResponse(settingsMessage(0x0D, 0x02)).shouldBeNull() }
    }

    // ── Ear Detection (0x06) ───────────────────────────────────

    @Nested
    inner class EarDetectionTests {
        @Test fun `decode both pods in ear`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 00 00"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.isEitherPodInEar shouldBe true
        }

        @Test fun `decode both pods not in ear`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 01 01"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.isEitherPodInEar shouldBe false
        }

        @Test fun `decode both pods in case`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 02 02"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe false
        }

        @Test fun `decode primary in ear, secondary in case`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 00 02"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe true
        }

        @Test fun `decode primary not in ear, secondary in case`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 01 02"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe false
        }

        @Test fun `decode unknown wire value maps to DISCONNECTED`() {
            val ed = decodeSetting<AapSetting.EarDetection>(aapMessage("04 00 04 00 06 00 FF 03"))
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.DISCONNECTED
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.DISCONNECTED
        }

        @Test fun `payload too short returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 06 00 00")).shouldBeNull()
        }

        @Test fun `does not interfere with settings decode`() {
            decodeSetting<AapSetting.AncMode>(settingsMessage(0x0D, 0x02)).current shouldBe AapSetting.AncMode.Value.ON
        }
    }

    // ── Edge Cases ───────────────────────────────────────────

    @Test fun `unknown setting ID returns null`() { profile.decodeSetting(settingsMessage(0x7F, 0x01)).shouldBeNull() }
    @Test fun `non-settings command returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 1D 00 01 02 03 04")).shouldBeNull() }
    @Test fun `payload too short returns null`() { profile.decodeSetting(aapMessage("04 00 04 00 09 00 0D")).shouldBeNull() }
}