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
 * Tests the full AAP decode pipeline using real captured data from AirPods Pro 2 (USB-C).
 * Each test uses exact bytes observed on a live device via L2CAP SEQPACKET reads.
 *
 * Device: AirPods Pro 2 USB-C (model A3048, product ID 0x2420)
 * Phone: Pixel 8 (Android 17 Beta)
 * Captured: 2026-04-02
 */
class AirPodsPro2UsbcAapSessionTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO2_USBC

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
    fun `device info - 232 bytes`() {
        val msg = aapMessage(
            "04 00 04 00 1D 00 02 DF 00 04 00",
            "41 69 72 50 6F 64 73 20 50 72 6F 00",             // "AirPods Pro"
            "41 33 30 34 38 00",                                 // "A3048"
            "41 70 70 6C 65 20 49 6E 63 2E 00",                 // "Apple Inc."
            "57 35 4A 37 4B 56 30 4E 30 34 00",                 // serial
            "38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 30 38 32 00", // firmware
            "38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 30 38 32 00",
            "31 2E 30 2E 30 00",
            "63 6F 6D 2E 61 70 70 6C 65 2E 61 63 63 65 73 73 6F 72 79 2E 75 70 64 61 74 65 72 2E 61 70 70 2E 37 31 00",
            "48 33 4B 4C 37 48 52 39 32 36 4A 59 00",           // left serial
            "48 33 4B 4C 32 41 59 4C 32 36 4B 30 00",           // right serial
            "38 34 35 34 34 38 30 00",
            "1F 3F B4 B7 E9 81 48 11 94 6B C2 6F 3C 5F 5A 34 0B AB 7E 42 AA BD F1 49 E3 A8 98 E7 81 D6 04 F5 68 1F",
            "31 36 39 37 34 38 30 32 31 31 00 31 36 39 37 34 38 30 32 31 31 00",
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.name shouldBe "AirPods Pro"
        info.modelNumber shouldBe "A3048"
        info.manufacturer shouldBe "Apple Inc."
    }

    // ── Battery ──────────────────────────────────────────────

    @Nested
    inner class BatterySessionTests {
        @Test
        fun `in case - pods CHARGING, case NOT_CHARGING`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 64 01 01 04 01 64 01 01 08 01 52 02 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.CASE]!!.let { it.percent shouldBe 0.82f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
        }

        @Test
        fun `out of case - pods NOT_CHARGING, case DISCONNECTED`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 64 02 01 04 01 64 02 01 08 01 00 04 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
            r[AapPodState.BatteryType.CASE]!!.charging shouldBe AapPodState.ChargingState.DISCONNECTED
        }

        @Test
        fun `back in case - pods CHARGING`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 04 01 64 01 01 02 01 64 01 01 08 01 51 02 01"))!!
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.CASE]!!.let { it.percent shouldBe 0.81f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
        }
    }

    // ── Private Keys ─────────────────────────────────────────

    @Test
    fun `private key response - 47 bytes with IRK and ENC`() {
        val result = profile.decodePrivateKeyResponse(
            aapMessage(
                "04 00 04 00 31 00 02 " +
                        "01 00 10 00 A0 94 D4 BB 03 29 03 27 99 39 14 8D C4 64 60 95 " +
                        "04 00 10 00 80 45 5C F4 F7 74 39 B8 4F AF 74 C8 4E 02 17 B7"
            )
        )!!
        result.irk!!.size shouldBe 16
        result.irk!![0] shouldBe 0xA0.toByte()
        result.encKey!!.size shouldBe 16
        result.encKey!![0] shouldBe 0x80.toByte()
    }

    // ── Settings Push ────────────────────────────────────────

    @Nested
    inner class SettingsSessionTests {
        @Test
        fun `ANC mode ON`() {
            val anc = decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 02 00 00 00")
            anc.current shouldBe AapSetting.AncMode.Value.ON
            anc.supported shouldContainExactly listOf(
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
            decodeSetting<AapSetting.EndCallMuteMic>("04 00 04 00 09 00 24 00 03 00 00").let {
                it.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS
                it.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS
            }
        }

        @Test fun `conversational awareness OFF`() {
            decodeSetting<AapSetting.ConversationalAwareness>("04 00 04 00 09 00 28 02 00 00 00").enabled shouldBe false
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

        @Test fun `device echoes OFF`() {
            decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 01 00 00 00").current shouldBe AapSetting.AncMode.Value.OFF
        }
    }

    // ── Primary Pod (0x08) — real captures ─────────────────────

    @Nested
    inner class PrimaryPodSessionTests {
        @Test fun `primary pod RIGHT`() {
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 02 00 01 00").pod shouldBe AapSetting.PrimaryPod.Pod.RIGHT
        }
    }

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

        @Test fun `one pod in ear, other in case`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 00 02")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.isEitherPodInEar shouldBe true
        }

        @Test fun `one pod in ear, other not in ear`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 00 01")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.isEitherPodInEar shouldBe true
        }

        @Test fun `both pods in ear`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 00 00")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
            ed.isEitherPodInEar shouldBe true
        }

        @Test fun `both pods not in ear (on desk)`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 01 01")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.NOT_IN_EAR
            ed.isEitherPodInEar shouldBe false
        }
    }

    // ── Unhandled Messages ───────────────────────────────────

    @Nested
    inner class UnhandledMessageTests {
        @Test fun `cmd 0x002B init exchange`() {
            profile.decodeSetting(aapMessage("04 00 04 00 2B 00 01 22 00 F3 2F 47 00 00 00 00 00 52 FD 1C 00 00 00 00 00 7C D1 56 70 3F 18 05 00 00 E1 EE 3E 00 00 00 00 00 39 1B 4C 60 45 94 09 00 00")).shouldBeNull()
        }

        @Test
        fun `unknown settings IDs return null`() {
            val unknownIds = listOf(0x29, 0x2C, 0x2F, 0x33, 0x35, 0x3E)
            for (id in unknownIds) {
                profile.decodeSetting(settingsMessage(id, 0x02)).shouldBeNull()
            }
        }
    }
}
