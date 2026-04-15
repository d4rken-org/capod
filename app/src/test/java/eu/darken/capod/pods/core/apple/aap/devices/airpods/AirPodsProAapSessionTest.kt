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
 * Tests the full AAP decode pipeline using real captured data from AirPods Pro (Gen 1).
 * Each test uses exact bytes observed on a live device via L2CAP SEQPACKET reads.
 *
 * Device: AirPods Pro (model A2084, product ID 0x0E20)
 * Phone: Pixel 8 (Android 17 Beta)
 * Captured: 2026-03-31
 */
class AirPodsProAapSessionTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO

    // ── Handshake ────────────────────────────────────────────

    @Test
    fun `handshake response - 18 bytes`() {
        val msg = aapMessage("01 00 04 00 00 00 01 00 03 00 04 00 B1 E1 04 00 51 E2")
        msg.commandType shouldBe 0x0000
        msg.raw.size shouldBe 18
        msg.payload.size shouldBe 12
    }

    // ── Device Info ──────────────────────────────────────────

    @Test
    fun `device info - 202 bytes`() {
        val msg = aapMessage(
            "04 00 04 00 1D 00 02 C1 00 0D 00",
            "41 69 72 50 6F 64 73 20 50 72 6F 00",             // "AirPods Pro"
            "41 32 30 38 34 00",                                 // "A2084"
            "41 70 70 6C 65 20 49 6E 63 2E 00",                 // "Apple Inc."
            "47 58 34 46 31 34 44 42 30 43 36 4C 00",           // serial
            "35 31 2E 39 2E 36 00",                              // firmware "51.9.6"
            "35 31 2E 39 2E 36 00",
            "31 2E 30 2E 30 00",
            "63 6F 6D 2E 61 70 70 6C 65 2E 61 63 63 65 73 73 6F 72 79 2E 75 70 64 61 74 65 72 2E 61 70 70 2E 36 30 2D 41 4E 43 00",
            "47 58 44 44 52 46 4E 57 30 43 36 4B 00",           // left serial
            "48 36 52 48 4C 30 48 46 30 43 36 4A 00",           // right serial
            "33 33 34 34 36 34 36 00",
            "41 F0 0C C3 64 66 3B CD 3D CC 67 53 1C 7A 85 7B E0 9E B0 E9 0A D9 8C 5A F0 5D 8D 14 AD B1 CE 19 06 41",
            "31 36 33 37 31 36 36 34 37 35 00 31 36 35 31 31 33 35 38 33 34 00",
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.name shouldBe "AirPods Pro"
        info.modelNumber shouldBe "A2084"
        info.manufacturer shouldBe "Apple Inc."
        info.leftEarbudSerial shouldBe "GXDDRFNW0C6K"
        info.rightEarbudSerial shouldBe "H6RHL0HF0C6J"
        info.buildNumber shouldBe "3344646"
    }

    // ── Battery ──────────────────────────────────────────────

    @Nested
    inner class BatterySessionTests {
        @Test
        fun `in case - pods CHARGING, case NOT_CHARGING`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 64 01 01 04 01 64 01 01 08 01 56 02 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.CASE]!!.let { it.percent shouldBe 0.86f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
        }

        @Test
        fun `out of case - pods NOT_CHARGING, case DISCONNECTED`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 63 02 01 04 01 64 02 01 08 01 00 04 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 0.99f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
            r[AapPodState.BatteryType.CASE]!!.charging shouldBe AapPodState.ChargingState.DISCONNECTED
        }

        @Test
        fun `back in case - pods CHARGING`() {
            val r = profile.decodeBattery(aapMessage("04 00 04 00 04 00 03 02 01 63 01 01 04 01 64 01 01 08 01 56 02 01"))!!
            r[AapPodState.BatteryType.RIGHT]!!.let { it.percent shouldBe 0.99f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.LEFT]!!.let { it.percent shouldBe 1.0f; it.charging shouldBe AapPodState.ChargingState.CHARGING }
            r[AapPodState.BatteryType.CASE]!!.let { it.percent shouldBe 0.86f; it.charging shouldBe AapPodState.ChargingState.NOT_CHARGING }
        }
    }

    // ── Private Keys ─────────────────────────────────────────

    @Test
    fun `private key response - 47 bytes with IRK and ENC`() {
        val result = profile.decodePrivateKeyResponse(
            aapMessage(
                "04 00 04 00 31 00 02 " +
                        "01 00 10 00 9E 46 77 B1 80 CD D7 B4 FA 9E 06 72 21 EB B3 D4 " +
                        "04 00 10 00 2B 95 99 40 E5 7C 6B 7E D8 97 98 0F 1B F7 BF 11"
            )
        )!!
        result.irk!!.size shouldBe 16
        result.irk!![0] shouldBe 0x9E.toByte()
        result.encKey!!.size shouldBe 16
        result.encKey!![0] shouldBe 0x2B.toByte()
    }

    // ── Settings Push ────────────────────────────────────────

    @Nested
    inner class SettingsSessionTests {
        @Test
        fun `ANC mode OFF`() {
            val anc = decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 01 00 00 00")
            anc.current shouldBe AapSetting.AncMode.Value.OFF
            anc.supported shouldContainExactly listOf(
                AapSetting.AncMode.Value.OFF,
                AapSetting.AncMode.Value.ON,
                AapSetting.AncMode.Value.TRANSPARENCY,
            )
        }

        @Test fun `press hold duration DEFAULT`() {
            decodeSetting<AapSetting.PressHoldDuration>("04 00 04 00 09 00 18 00 00 00 00").value shouldBe AapSetting.PressHoldDuration.Value.DEFAULT
        }

        @Test fun `press speed DEFAULT`() {
            decodeSetting<AapSetting.PressSpeed>("04 00 04 00 09 00 17 00 00 00 00").value shouldBe AapSetting.PressSpeed.Value.DEFAULT
        }

        @Test fun `tone volume 80`() {
            decodeSetting<AapSetting.ToneVolume>("04 00 04 00 09 00 1F 50 50 00 00").level shouldBe 0x50
        }

        @Test fun `NC one airpod OFF`() {
            decodeSetting<AapSetting.NcWithOneAirPod>("04 00 04 00 09 00 1B 02 00 00 00").enabled shouldBe false
        }

        @Test fun `end call mute mic - subtype 0x20`() {
            decodeSetting<AapSetting.EndCallMuteMic>("04 00 04 00 09 00 24 20 03 00 00").let {
                it.muteMic shouldBe AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS
                it.endCall shouldBe AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS
            }
        }
    }

    // ── ANC Mode Switching (verified audible) ────────────────

    @Nested
    inner class AncModeSwitchingTests {
        @Test fun `device echoes ON`() {
            decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 02 00 00 00").current shouldBe AapSetting.AncMode.Value.ON
        }

        @Test fun `device echoes TRANSPARENCY`() {
            decodeSetting<AapSetting.AncMode>("04 00 04 00 09 00 0D 03 00 00 00").current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
        }
    }

    // ── Primary Pod (0x08) — real captures ─────────────────────

    @Nested
    inner class PrimaryPodSessionTests {
        @Test fun `primary pod RIGHT - initial state`() {
            // Pro 1 sends byte[2]=0x00 on initial connect (vs Pro 3 which sends 0x01)
            decodeSetting<AapSetting.PrimaryPod>("04 00 04 00 08 00 02 00 00 00").pod shouldBe AapSetting.PrimaryPod.Pod.RIGHT
        }

        @Test fun `primary pod RIGHT - swap completed`() {
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

        @Test fun `primary in case, secondary disconnected`() {
            val ed = decodeSetting<AapSetting.EarDetection>("04 00 04 00 06 00 02 03")
            ed.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_CASE
            ed.secondaryPod shouldBe AapSetting.EarDetection.PodPlacement.DISCONNECTED
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
    }

    // ── Unhandled Messages ───────────────────────────────────

    @Nested
    inner class UnhandledMessageTests {
        @Test fun `cmd 0x002B init exchange`() {
            profile.decodeSetting(aapMessage("04 00 04 00 2B 00 01 22 00 C7 A2 53 00 00 00 00 00 9F 98 27 00 00 00 00 00 39 1B 4C 60 45 94 01 01 00 B8 A5 31 00 00 00 00 00 49 CC B7 0C 65 74 01 02 00")).shouldBeNull()
        }
    }
}
