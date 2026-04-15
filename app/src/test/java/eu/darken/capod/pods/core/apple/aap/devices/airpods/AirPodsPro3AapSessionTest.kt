package eu.darken.capod.pods.core.apple.aap.devices.airpods

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.BaseAapSessionTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the full AAP decode pipeline using real captured data from AirPods Pro 3.
 * Each test uses exact bytes observed on a live device via L2CAP SEQPACKET reads.
 *
 * Device: AirPods Pro 3 (model A3064, product ID 0x2720)
 * Phone: Pixel 8 (Android 17 Beta)
 * Captured: 2026-03-30
 */
class AirPodsPro3AapSessionTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO3

    // ── Handshake ────────────────────────────────────────────

    @Test
    fun `handshake response - 18 bytes`() {
        val msg = aapMessage("01 00 04 00 00 00 01 00 03 00 00 00 00 00 00 00 00 00")
        msg.commandType shouldBe 0x0000
        msg.raw.size shouldBe 18
        msg.payload.size shouldBe 12
    }

    // ── Device Info ──────────────────────────────────────────

    @Test
    fun `device info - 246 bytes`() {
        val msg = aapMessage(
            "04 00 04 00 1D 00 02 ED 00 04 00",
            "41 69 72 50 6F 64 73 20 50 72 6F 20 33 00",       // "AirPods Pro 3"
            "41 33 30 36 34 00",                                 // "A3064"
            "41 70 70 6C 65 20 49 6E 63 2E 00",                 // "Apple Inc."
            "46 59 4C 39 37 33 30 33 54 39 00",                 // serial
            "38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 35 30 33 00", // firmware
            "38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 35 30 33 00",
            "31 2E 30 2E 30 00",
            "63 6F 6D 2E 61 70 70 6C 65 2E 61 63 63 65 73 73 6F 72 79 2E 75 70 64 61 74 65 72 2E 61 70 70 2E 37 31 00",
            "47 4D 50 48 4E 5A 31 36 50 35 5A 30 30 30 30 55 48 5A 00",  // left serial
            "47 4D 56 48 4E 58 31 35 55 45 44 30 30 30 30 55 48 59 00",  // right serial
            "38 34 35 34 36 32 34 00",
            "AE 84 20 32 A7 3E 46 46 A8 EA 0B 13 A9 27 8B 79 0D D3 55 98 AC 3D 1A 4F 2F 89 BE 5C 65 F5 FF 1C D9 AE",
            "31 37 36 37 33 36 34 30 37 34 00 31 37 36 37 33 36 34 30 37 34 00",
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.name shouldBe "AirPods Pro 3"
        info.modelNumber shouldBe "A3064"
        info.manufacturer shouldBe "Apple Inc."
    }

    // ── Battery ──────────────────────────────────────────────

    @Nested
    inner class BatterySessionTests {
        @Test
        fun `in case charging - pods CHARGING_OPTIMIZED, case CHARGING`() {
            val r =
                profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 04 01 50 05 01 02 01 4F 05 01 08 01 30 01 01"))!!
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 0.8f; it.charging shouldBe AapPodState.ChargingState.CHARGING_OPTIMIZED }
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 0.79f; it.charging shouldBe AapPodState.ChargingState.CHARGING_OPTIMIZED }
            r[AapPodState.BatteryType.CASE]!!.let { it.percent shouldBe 0.48f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
        }

        @Test
        fun `out of case - pods NOT_CHARGING, case DISCONNECTED`() {
            val r =
                profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 4D 02 01 04 01 50 02 01 08 01 00 04 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.charging shouldBe AapPodState.ChargingState.NOT_CHARGING
            r[AapPodState.BatteryType.LEFT]!!.charging shouldBe AapPodState.ChargingState.NOT_CHARGING
            r[AapPodState.BatteryType.CASE]!!.charging shouldBe AapPodState.ChargingState.DISCONNECTED
        }
    }

    // ── Private Keys ─────────────────────────────────────────

    @Test
    fun `private key response - 47 bytes with IRK and ENC`() {
        val result = profile.decodePrivateKeyResponse(
            aapMessage(
                "04 00 04 00 31 00 02 " +
                        "01 00 10 00 7C 64 9F C2 6C C1 07 2F 07 A2 BD 34 3A FA 8B A1 " +
                        "04 00 10 00 A3 52 94 92 78 52 5F F0 95 E3 A6 C7 10 32 29 8B"
            )
        )!!
        result.irk!!.size shouldBe 16
        result.irk!![0] shouldBe 0x7C.toByte()
        result.encKey!!.size shouldBe 16
        result.encKey!![0] shouldBe 0xA3.toByte()
    }

    // ── Settings Push ────────────────────────────────────────

    @Nested
    inner class SettingsSessionTests {
        @Test fun `ANC mode ON`() {
            val anc = decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 02 00 00 00")
            anc.current shouldBe AapSetting.AncMode.Value.ON
            anc.supported shouldContainExactly listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.TRANSPARENCY,
                AapSetting.AncMode.Value.ADAPTIVE
            )
        }

        @Test fun `press hold duration DEFAULT`() {
            decodeSetting<AapSetting.PressHoldDuration>("04 00 04 00 09 00 18 00 00 00 00").value shouldBe AapSetting.PressHoldDuration.Value.DEFAULT
        }

        @Test fun `press speed DEFAULT`() {
            decodeSetting<AapSetting.PressSpeed>("04 00 04 00 09 00 17 00 00 00 00").value shouldBe AapSetting.PressSpeed.Value.DEFAULT
        }

        @Test fun `volume swipe ON`() {
            decodeSetting<AapSetting.VolumeSwipe>("04 00 04 00 09 00 25 01 00 00 00").enabled shouldBe true
        }

        @Test fun `volume swipe length DEFAULT`() {
            decodeSetting<AapSetting.VolumeSwipeLength>("04 00 04 00 09 00 23 00 00 00 00").value shouldBe AapSetting.VolumeSwipeLength.Value.DEFAULT
        }

        @Test fun `tone volume 80`() {
            decodeSetting<AapSetting.ToneVolume>("04 00 04 00 09 00 1F 50 50 00 00").level shouldBe 0x50
        }

        @Test fun `end call mute mic - subtype 0x00`() {
            decodeSetting<AapSetting.EndCallMuteMic>("04 00 04 00 09 00 24 00 03 00 00").let { it.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS; it.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS }
        }

        @Test fun `conversational awareness OFF`() {
            decodeSetting<AapSetting.ConversationalAwareness>("04 00 04 00 09 00 28 02 00 00 00").enabled shouldBe false
        }

        @Test fun `conversational awareness ON after reconnect`() {
            decodeSetting<AapSetting.ConversationalAwareness>("04 00 04 00 09 00 28 01 00 00 00").enabled shouldBe true
        }

        @Test fun `personalized volume OFF`() {
            decodeSetting<AapSetting.PersonalizedVolume>("04 00 04 00 09 00 26 02 00 00 00").enabled shouldBe false
        }

        @Test fun `adaptive audio noise 50`() {
            decodeSetting<AapSetting.AdaptiveAudioNoise>("04 00 04 00 09 00 2E 32 00 00 00").level shouldBe 0x32
        }

        @Test fun `NC one airpod OFF`() {
            decodeSetting<AapSetting.NcWithOneAirPod>("04 00 04 00 09 00 1B 02 00 00 00").enabled shouldBe false
        }
    }

    // ── ANC Mode Switching (verified audible) ────────────────

    @Nested
    inner class AncModeSwitchingTests {
        @Test fun `device echoes TRANSPARENCY`() {
            decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 03 00 00 00").current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
        }

        @Test fun `device echoes ADAPTIVE`() {
            decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 04 00 00 00").current shouldBe AapSetting.AncMode.Value.ADAPTIVE
        }
    }

    // ── Conversation Awareness State (0x4B) ──────────────────

    @Test
    fun `conversation awareness state - not speaking`() {
        decodeSetting<AapSetting.ConversationalAwarenessState>("04 00 04 00 4B 00 00").speaking shouldBe false
    }

    // ── Primary Pod (0x08) — real captures ─────────────────────

    @Nested
    inner class PrimaryPodSessionTests {
        @Test fun `primary pod LEFT - swap started`() {
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 01 00 01 01").pod shouldBe AapSetting.PrimaryPod.Pod.LEFT
        }

        @Test fun `primary pod RIGHT - swap started`() {
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 02 00 01 01").pod shouldBe AapSetting.PrimaryPod.Pod.RIGHT
        }

        @Test fun `primary pod LEFT - swap completed`() {
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 01 00 01 00").pod shouldBe AapSetting.PrimaryPod.Pod.LEFT
        }

        @Test fun `primary pod RIGHT - swap completed`() {
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 02 00 01 00").pod shouldBe AapSetting.PrimaryPod.Pod.RIGHT
        }
    }

    // ── Unhandled Messages ───────────────────────────────────

    // ── Ear Detection (0x06) — real captures ──────────────────

    @Nested
    inner class EarDetectionSessionTests {
        @Test fun `both pods in case`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 02 02")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe false
        }

        @Test fun `pod taken from case`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 01 02")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe false
        }

        @Test fun `pod in ear`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 00 02")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe true
        }
    }

    // ── Unhandled Messages ───────────────────────────────────

    @Nested
    inner class UnhandledMessageTests {
        @Test fun `cmd 0x002B init exchange`() {
            profile.decodeSetting(aapMessage("04 00 04 00 2B 00 01 22 00 E9 B4 03")).shouldBeNull()
        }

        @Test
        fun `unknown settings IDs return null`() {
            val unknownIds = listOf(0x29, 0x2C, 0x2F, 0x33, 0x30, 0x37, 0x38, 0x3B)
            for (id in unknownIds) {
                profile.decodeSetting(settingsMessage(id, 0x01)).shouldBeNull()
            }
        }
    }
}
