package eu.darken.capod.pods.core.apple.aap.devices

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.BaseAapSessionTest
import eu.darken.capod.pods.core.apple.aap.protocol.DefaultAapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultAapDeviceProfileNewSettingsTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO2

    // ── Microphone Mode (0x01) ──────────────────────────────

    @Nested
    inner class MicrophoneModeTests {
        @Test fun `encode auto`() { profile.encodeCommand(AapCommand.SetMicrophoneMode(AapSetting.MicrophoneMode.Mode.AUTO))[7] shouldBe 0x00.toByte() }
        @Test fun `encode always right`() { profile.encodeCommand(AapCommand.SetMicrophoneMode(AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT))[7] shouldBe 0x01.toByte() }
        @Test fun `encode always left`() { profile.encodeCommand(AapCommand.SetMicrophoneMode(AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT))[7] shouldBe 0x02.toByte() }
        @Test fun `decode auto`() { decodeSetting<AapSetting.MicrophoneMode>(settingsMessage(0x01, 0x00)).mode shouldBe AapSetting.MicrophoneMode.Mode.AUTO }
        @Test fun `decode always right`() { decodeSetting<AapSetting.MicrophoneMode>(settingsMessage(0x01, 0x01)).mode shouldBe AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT }
        @Test fun `decode always left`() { decodeSetting<AapSetting.MicrophoneMode>(settingsMessage(0x01, 0x02)).mode shouldBe AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x01, 0x99)).shouldBeNull() }

        @Test
        fun `round-trip all modes`() {
            for (mode in AapSetting.MicrophoneMode.Mode.entries) {
                val encoded = profile.encodeCommand(AapCommand.SetMicrophoneMode(mode))
                decodeSetting<AapSetting.MicrophoneMode>(AapMessage.parse(encoded)!!).mode shouldBe mode
            }
        }
    }

    // ── Ear Detection Toggle (0x0A) ─────────────────────────

    @Nested
    inner class EarDetectionToggleTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetEarDetectionEnabled(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetEarDetectionEnabled(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.EarDetectionEnabled>(settingsMessage(0x0A, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.EarDetectionEnabled>(settingsMessage(0x0A, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x0A, 0x00)).shouldBeNull() }
    }

    // ── Listening Mode Cycle (0x1A) ─────────────────────────

    @Nested
    inner class ListeningModeCycleTests {
        @Test fun `encode mask 0x0F`() { profile.encodeCommand(AapCommand.SetListeningModeCycle(0x0F))[7] shouldBe 0x0F.toByte() }
        @Test fun `encode mask 0x06`() { profile.encodeCommand(AapCommand.SetListeningModeCycle(0x06))[7] shouldBe 0x06.toByte() }
        @Test fun `decode mask`() { decodeSetting<AapSetting.ListeningModeCycle>(settingsMessage(0x1A, 0x0E)).modeMask shouldBe 0x0E }

        @Test fun `decode mask helpers`() {
            val cycle = decodeSetting<AapSetting.ListeningModeCycle>(settingsMessage(0x1A, 0x0D))
            cycle.includesOff shouldBe true
            cycle.includesAnc shouldBe false
            cycle.includesTransparency shouldBe true
            cycle.includesAdaptive shouldBe true
        }

        @Test fun `encode single mode`() {
            profile.encodeCommand(AapCommand.SetListeningModeCycle(0x01))[7] shouldBe 0x01.toByte()
        }

        @Test fun `encode zero mask`() {
            profile.encodeCommand(AapCommand.SetListeningModeCycle(0x00))[7] shouldBe 0x00.toByte()
        }

        @Test fun `encode masks out high bits`() {
            profile.encodeCommand(AapCommand.SetListeningModeCycle(0xFF))[7] shouldBe 0x0F.toByte()
        }
    }

    // ── Allow Off Option (0x34) ─────────────────────────────

    @Nested
    inner class AllowOffOptionTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetAllowOffOption(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetAllowOffOption(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.AllowOffOption>(settingsMessage(0x34, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.AllowOffOption>(settingsMessage(0x34, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x34, 0x00)).shouldBeNull() }
    }

    // ── Stem Config (0x39) ──────────────────────────────────

    @Nested
    inner class StemConfigTests {
        @Test fun `encode full claim`() { profile.encodeCommand(AapCommand.SetStemConfig(0x0F))[7] shouldBe 0x0F.toByte() }
        @Test fun `encode no claim`() { profile.encodeCommand(AapCommand.SetStemConfig(0x00))[7] shouldBe 0x00.toByte() }
        @Test fun `encode masks high bits`() { profile.encodeCommand(AapCommand.SetStemConfig(0xFF))[7] shouldBe 0x0F.toByte() }

        @Test fun `decode claim mask`() {
            val sc = decodeSetting<AapSetting.StemConfig>(settingsMessage(0x39, 0x05))
            sc.claimsSinglePress shouldBe true
            sc.claimsDoublePress shouldBe false
            sc.claimsTriplePress shouldBe true
            sc.claimsLongPress shouldBe false
        }
    }

    // ── Sleep Detection (0x35) ──────────────────────────────

    @Nested
    inner class SleepDetectionTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetSleepDetection(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetSleepDetection(false))[7] shouldBe 0x02.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.SleepDetection>(settingsMessage(0x35, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.SleepDetection>(settingsMessage(0x35, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x35, 0x00)).shouldBeNull() }
    }

    // ── Dynamic End of Charge / "Optimized Charge Limit" (0x3B) ─
    // Apple-bool wire semantics matching every other boolean setting we've decoded. Real
    // capture on a Pro 3 after handshake showed rawValue 0x01 (enabled).

    @Nested
    inner class DynamicEndOfChargeTests {
        @Test fun `encode enabled`() { profile.encodeCommand(AapCommand.SetDynamicEndOfCharge(true))[7] shouldBe 0x01.toByte() }
        @Test fun `encode disabled`() { profile.encodeCommand(AapCommand.SetDynamicEndOfCharge(false))[7] shouldBe 0x02.toByte() }
        @Test fun `encode carries the right setting id`() { profile.encodeCommand(AapCommand.SetDynamicEndOfCharge(true))[6] shouldBe 0x3B.toByte() }
        @Test fun `decode enabled`() { decodeSetting<AapSetting.DynamicEndOfCharge>(settingsMessage(0x3B, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.DynamicEndOfCharge>(settingsMessage(0x3B, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x3B, 0x00)).shouldBeNull() }
    }

    // ── In-Case Tone (0x31) ─────────────────────────────────
    // Decode path is kept internally even though the setting is no longer exposed in the UI.
    // See the IN_CASE_TONE branch in DefaultAapDeviceProfile.decodeSetting for rationale.

    @Nested
    inner class InCaseToneTests {
        @Test fun `decode enabled`() { decodeSetting<AapSetting.InCaseTone>(settingsMessage(0x31, 0x01)).enabled shouldBe true }
        @Test fun `decode disabled`() { decodeSetting<AapSetting.InCaseTone>(settingsMessage(0x31, 0x02)).enabled shouldBe false }
        @Test fun `decode unknown returns null`() { profile.decodeSetting(settingsMessage(0x31, 0x00)).shouldBeNull() }
    }

    // ── Custom EQ (0x63) ────────────────────────────────────
    // Three-band EQ from the iOS 26/27-era firmware. Bands are 0..100 with 50 neutral, and the
    // state byte is inverted relative to the Apple-bool convention (1 = off, 2 = on).

    @Nested
    inner class CustomEqTests {
        @Test
        fun `encode enabled full frame`() {
            val bytes = profile.encodeCommand(AapCommand.SetCustomEq(enabled = true, low = 60, mid = 50, high = 40))
            bytes shouldBe parseHex("04 00 04 00 63 00 05 00 01 02 3C 32 28")
        }

        @Test
        fun `encode disabled full frame`() {
            val bytes = profile.encodeCommand(AapCommand.SetCustomEq(enabled = false, low = 50, mid = 50, high = 50))
            bytes shouldBe parseHex("04 00 04 00 63 00 05 00 01 01 32 32 32")
        }

        @Test
        fun `encode coerces out of range bands`() {
            val bytes = profile.encodeCommand(AapCommand.SetCustomEq(enabled = true, low = -10, mid = 200, high = 101))
            bytes shouldBe parseHex("04 00 04 00 63 00 05 00 01 02 00 64 64")
        }

        @Test
        fun `decode enabled`() {
            val eq = decodeSetting<AapSetting.CustomEq>("04 00 04 00 63 00 05 00 01 02 00 32 64")
            eq.enabled shouldBe true
            eq.low shouldBe 0
            eq.mid shouldBe 50
            eq.high shouldBe 100
        }

        @Test
        fun `decode disabled`() {
            val eq = decodeSetting<AapSetting.CustomEq>("04 00 04 00 63 00 05 00 01 01 32 32 32")
            eq.enabled shouldBe false
            eq.low shouldBe 50
            eq.mid shouldBe 50
            eq.high shouldBe 50
        }

        @Test
        fun `decode coerces out of range bands`() {
            val eq = decodeSetting<AapSetting.CustomEq>("04 00 04 00 63 00 05 00 01 02 FF 65 32")
            eq.low shouldBe 100
            eq.mid shouldBe 100
            eq.high shouldBe 50
        }

        @Test
        fun `payload too short returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 63 00 05 00 01 02 32 32")).shouldBeNull()
        }

        @Test
        fun `unknown state byte returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 63 00 05 00 01 03 32 32 32")).shouldBeNull()
        }

        @Test
        fun `round-trip`() {
            val command = AapCommand.SetCustomEq(enabled = true, low = 10, mid = 55, high = 90)
            val encoded = profile.encodeCommand(command)
            val decoded = decodeSetting<AapSetting.CustomEq>(AapMessage.parse(encoded)!!)
            decoded shouldBe AapSetting.CustomEq(enabled = true, low = 10, mid = 55, high = 90)
        }
    }

    // ── Device Rename (0x1A) ────────────────────────────────
    // The working opcode is 0x1A (not the 0x1E variant in LibrePods Android); see the rationale
    // comment in DefaultAapDeviceProfile.buildRenameMessage for the on-device test details.

    @Nested
    inner class DeviceRenameTests {
        @Test
        fun `encode simple ASCII name full frame`() {
            // Locks in the full wire format: header + opcode prefix (1A 00 01) + length (LE u16) + name.
            val bytes = profile.encodeCommand(AapCommand.SetDeviceName("MyPods"))
            val expected = byteArrayOf(
                0x04, 0x00, 0x04, 0x00,
                0x1A, 0x00, 0x01,
                0x06, 0x00,
                0x4D, 0x79, 0x50, 0x6F, 0x64, 0x73,
            )
            bytes shouldBe expected
        }

        @Test
        fun `encode multibyte UTF-8 name`() {
            val name = "AirPods \uD83C\uDFA7" // headphone emoji
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val bytes = profile.encodeCommand(AapCommand.SetDeviceName(name))
            bytes.size shouldBe 9 + nameBytes.size
            bytes[7] shouldBe nameBytes.size.toByte()
            String(bytes, 9, nameBytes.size, Charsets.UTF_8) shouldBe name
        }

        @Test
        fun `encode rejects name exceeding 127 bytes`() {
            val longName = "A".repeat(128) // 128 ASCII bytes
            assertThrows<IllegalArgumentException> { profile.encodeCommand(AapCommand.SetDeviceName(longName)) }
        }

        @Test
        fun `encode accepts 127 byte name`() {
            val name = "A".repeat(127)
            val bytes = profile.encodeCommand(AapCommand.SetDeviceName(name))
            bytes.size shouldBe 9 + 127
            bytes[7] shouldBe 127.toByte()
        }
    }

    // ── Stem Press Events (0x19) ────────────────────────────

    @Nested
    inner class StemPressEventTests {
        @Test fun `decode single left`() {
            val event = profile.decodeStemPress(aapMessage("04 00 04 00 19 00 05 01"))!!
            event.pressType shouldBe StemPressEvent.PressType.SINGLE
            event.bud shouldBe StemPressEvent.Bud.LEFT
        }

        @Test fun `decode double right`() {
            val event = profile.decodeStemPress(aapMessage("04 00 04 00 19 00 06 02"))!!
            event.pressType shouldBe StemPressEvent.PressType.DOUBLE
            event.bud shouldBe StemPressEvent.Bud.RIGHT
        }

        @Test fun `decode triple left`() {
            val event = profile.decodeStemPress(aapMessage("04 00 04 00 19 00 07 01"))!!
            event.pressType shouldBe StemPressEvent.PressType.TRIPLE
            event.bud shouldBe StemPressEvent.Bud.LEFT
        }

        @Test fun `decode long right`() {
            val event = profile.decodeStemPress(aapMessage("04 00 04 00 19 00 08 02"))!!
            event.pressType shouldBe StemPressEvent.PressType.LONG
            event.bud shouldBe StemPressEvent.Bud.RIGHT
        }

        @Test fun `unknown press type returns null`() {
            profile.decodeStemPress(aapMessage("04 00 04 00 19 00 99 01")).shouldBeNull()
        }

        @Test fun `unknown bud returns null`() {
            profile.decodeStemPress(aapMessage("04 00 04 00 19 00 05 03")).shouldBeNull()
        }

        @Test fun `payload too short returns null`() {
            profile.decodeStemPress(aapMessage("04 00 04 00 19 00 05")).shouldBeNull()
        }

        @Test fun `wrong command type returns null`() {
            profile.decodeStemPress(settingsMessage(0x0D, 0x02)).shouldBeNull()
        }
    }

    // ── Connected Devices (0x2E) ────────────────────────────

    @Nested
    inner class ConnectedDevicesTests {
        @Test fun `decode single device`() {
            // payload: 2 unknown bytes, count=1, then 6-byte MAC + 2 flags
            val msg = aapMessage("04 00 04 00 2E 00 00 00 01 01 02 03 04 05 06 00 00")
            val cd = decodeSetting<AapSetting.ConnectedDevices>(msg)
            cd.devices.size shouldBe 1
            cd.devices[0].mac shouldBe "01:02:03:04:05:06"
        }

        @Test fun `decode high bytes formats as upper-case hex`() {
            // Use MAC with high bytes (>=0x80) to lock in %02X formatting and catch sign-extension regressions.
            val msg = aapMessage("04 00 04 00 2E 00 00 00 01 AA BB CC DD EE FF 00 00")
            val cd = decodeSetting<AapSetting.ConnectedDevices>(msg)
            cd.devices[0].mac shouldBe "AA:BB:CC:DD:EE:FF"
        }

        @Test fun `decode empty list`() {
            val msg = aapMessage("04 00 04 00 2E 00 00 00 00")
            val cd = decodeSetting<AapSetting.ConnectedDevices>(msg)
            cd.devices shouldBe emptyList()
        }

        @Test fun `payload too short returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 2E 00 00 00")).shouldBeNull()
        }
    }

    // ── Audio Source (0x0E) ──────────────────────────────────

    @Nested
    inner class AudioSourceTests {
        @Test fun `decode media source`() {
            val msg = aapMessage("04 00 04 00 0E 00 01 02 03 04 05 06 02")
            val as_ = decodeSetting<AapSetting.AudioSource>(msg)
            as_.sourceMac shouldBe "01:02:03:04:05:06"
            as_.type shouldBe AapSetting.AudioSource.AudioSourceType.MEDIA
        }

        @Test fun `decode call source`() {
            val msg = aapMessage("04 00 04 00 0E 00 01 02 03 04 05 06 01")
            val as_ = decodeSetting<AapSetting.AudioSource>(msg)
            as_.sourceMac shouldBe "01:02:03:04:05:06"
            as_.type shouldBe AapSetting.AudioSource.AudioSourceType.CALL
        }

        @Test fun `decode unknown type maps to NONE`() {
            val msg = aapMessage("04 00 04 00 0E 00 01 02 03 04 05 06 00")
            val as_ = decodeSetting<AapSetting.AudioSource>(msg)
            as_.sourceMac shouldBe "01:02:03:04:05:06"
            as_.type shouldBe AapSetting.AudioSource.AudioSourceType.NONE
        }

        @Test fun `payload too short returns null`() {
            profile.decodeSetting(aapMessage("04 00 04 00 0E 00 01 02 03 04 05 06")).shouldBeNull()
        }

        @Test fun `decode high bytes formats as upper-case hex`() {
            val msg = aapMessage("04 00 04 00 0E 00 AA BB CC DD EE FF 02")
            val as_ = decodeSetting<AapSetting.AudioSource>(msg)
            as_.sourceMac shouldBe "AA:BB:CC:DD:EE:FF"
        }

        @Test fun `ConnectedDevices and AudioSource decode MAC identically`() {
            // DeviceSettingsScreen matches audioSource.sourceMac == device.mac, so both paths must canonicalize the same way.
            val connectedMsg = aapMessage("04 00 04 00 2E 00 00 00 01 11 22 33 44 55 66 00 00")
            val audioMsg = aapMessage("04 00 04 00 0E 00 11 22 33 44 55 66 02")
            val connected = decodeSetting<AapSetting.ConnectedDevices>(connectedMsg)
            val audio = decodeSetting<AapSetting.AudioSource>(audioMsg)
            connected.devices[0].mac shouldBe audio.sourceMac
        }
    }

    // ── Model Feature Flags ─────────────────────────────────

    @Nested
    inner class ModelFeatureFlags {
        @Test fun `Pro 2 has all new flags`() {
            val f = PodModel.AIRPODS_PRO2.features
            f.hasCustomEq shouldBe true
            f.hasMicrophoneMode shouldBe true
            f.hasEarDetectionToggle shouldBe true
            f.hasListeningModeCycle shouldBe true
            f.hasAllowOffOption shouldBe true
            f.hasStemConfig shouldBe true
            f.hasSleepDetection shouldBe true
        }

        @Test fun `Pro 1 has mic and ear detection but no stem config`() {
            val f = PodModel.AIRPODS_PRO.features
            f.hasMicrophoneMode shouldBe true
            f.hasEarDetectionToggle shouldBe true
            f.hasListeningModeCycle shouldBe true
            f.hasAllowOffOption shouldBe true
            f.hasStemConfig shouldBe false
            f.hasSleepDetection shouldBe false
        }

        @Test fun `Gen 4 has mic, ear detection, sleep, press and tone but no stem config`() {
            val f = PodModel.AIRPODS_GEN4.features
            f.hasMicrophoneMode shouldBe true
            f.hasEarDetectionToggle shouldBe true
            f.hasPressSpeed shouldBe true
            f.hasPressHoldDuration shouldBe true
            f.hasToneVolume shouldBe true
            f.hasEndCallMuteMic shouldBe true
            f.hasListeningModeCycle shouldBe false
            f.hasStemConfig shouldBe false
            f.hasSleepDetection shouldBe true
        }

        @Test fun `Max has ear detection toggle and listening mode cycle`() {
            val f = PodModel.AIRPODS_MAX.features
            f.hasMicrophoneMode shouldBe false
            f.hasEarDetectionToggle shouldBe true
            f.hasListeningModeCycle shouldBe true
            f.hasAllowOffOption shouldBe true
            f.hasStemConfig shouldBe false
        }

        @Test fun `Gen 1 has mic mode and ear detection toggle`() {
            val f = PodModel.AIRPODS_GEN1.features
            f.hasMicrophoneMode shouldBe true
            f.hasEarDetection shouldBe true
            f.hasEarDetectionToggle shouldBe true
            f.hasListeningModeCycle shouldBe false
            f.hasStemConfig shouldBe false
        }

        @Test fun `Gen 2 and Gen 3 expose ear detection toggle`() {
            val gen2 = PodModel.AIRPODS_GEN2.features
            gen2.hasMicrophoneMode shouldBe true
            gen2.hasEarDetection shouldBe true
            gen2.hasEarDetectionToggle shouldBe true

            val gen3 = PodModel.AIRPODS_GEN3.features
            gen3.hasMicrophoneMode shouldBe true
            gen3.hasEarDetection shouldBe true
            gen3.hasEarDetectionToggle shouldBe true
            gen3.hasPressSpeed shouldBe true
            gen3.hasPressHoldDuration shouldBe true
            gen3.hasToneVolume shouldBe true
            gen3.hasEndCallMuteMic shouldBe true
        }

        @Test fun `Max 2 has H2 features`() {
            val f = PodModel.AIRPODS_MAX2.features
            f.hasAdaptiveAnc shouldBe true
            f.hasConversationAwareness shouldBe true
            f.hasPersonalizedVolume shouldBe true
            f.hasAdaptiveAudioNoise shouldBe true
            f.hasListeningModeCycle shouldBe true
            f.hasAllowOffOption shouldBe true
            f.hasEarDetectionToggle shouldBe true
            // Headphone — no stem/swipe/dual-pod/case features
            f.hasDualPods shouldBe false
            f.hasCase shouldBe false
            f.hasStemConfig shouldBe false
            f.hasVolumeSwipe shouldBe false
            f.hasSleepDetection shouldBe false
        }

        @Test fun `Powerbeats Pro 2 has sleep detection and ear detection toggle`() {
            val f = PodModel.POWERBEATS_PRO2.features
            f.hasSleepDetection shouldBe true
            f.hasEarDetectionToggle shouldBe true
            f.hasAncControl shouldBe true
            f.hasEarDetection shouldBe true
            f.hasMicrophoneMode shouldBe true
        }

        @Test fun `custom EQ is limited to Apple's published model list`() {
            PodModel.entries.filter { it.features.hasCustomEq }.toSet() shouldBe setOf(
                PodModel.AIRPODS_GEN4,
                PodModel.AIRPODS_GEN4_ANC,
                PodModel.AIRPODS_GEN5,
                PodModel.AIRPODS_GEN5_WIRELESS,
                PodModel.AIRPODS_PRO2,
                PodModel.AIRPODS_PRO2_USBC,
                PodModel.AIRPODS_PRO3,
                PodModel.AIRPODS_MAX2,
            )
        }

        @Test fun `custom EQ is absent on pre-9A348 hardware generations`() {
            PodModel.AIRPODS_GEN3.features.hasCustomEq shouldBe false
            PodModel.AIRPODS_PRO.features.hasCustomEq shouldBe false
            PodModel.AIRPODS_MAX.features.hasCustomEq shouldBe false
            PodModel.AIRPODS_MAX_USBC.features.hasCustomEq shouldBe false
        }

        @Test fun `Powerbeats Pro and Beats Fit Pro expose ear detection toggle`() {
            val powerbeatsPro = PodModel.POWERBEATS_PRO.features
            powerbeatsPro.hasEarDetection shouldBe true
            powerbeatsPro.hasEarDetectionToggle shouldBe true

            val beatsFitPro = PodModel.BEATS_FIT_PRO.features
            beatsFitPro.hasEarDetection shouldBe true
            beatsFitPro.hasEarDetectionToggle shouldBe true
            beatsFitPro.hasMicrophoneMode shouldBe true
        }
    }

    // ── Feature Flag Invariants ─────────────────────────────

    @Nested
    inner class FeatureFlagInvariants {
        @Test fun `adaptiveAnc implies ancControl`() {
            for (model in PodModel.entries) {
                if (model.features.hasAdaptiveAnc) {
                    model.features.hasAncControl shouldBe true
                }
            }
        }

        @Test fun `listeningModeCycle implies ancControl`() {
            for (model in PodModel.entries) {
                if (model.features.hasListeningModeCycle) {
                    model.features.hasAncControl shouldBe true
                }
            }
        }

        @Test fun `sleepDetection implies earDetection`() {
            for (model in PodModel.entries) {
                if (model.features.hasSleepDetection) {
                    model.features.hasEarDetection shouldBe true
                }
            }
        }

        @Test fun `earDetectionToggle implies earDetection`() {
            for (model in PodModel.entries) {
                if (model.features.hasEarDetectionToggle) {
                    model.features.hasEarDetection shouldBe true
                }
            }
        }

        @Test fun `adaptiveAudioNoise implies adaptiveAnc`() {
            for (model in PodModel.entries) {
                if (model.features.hasAdaptiveAudioNoise) {
                    model.features.hasAdaptiveAnc shouldBe true
                }
            }
        }

        @Test fun `customEq is never set on a Beats model`() {
            for (model in PodModel.entries) {
                if (model.name.contains("BEATS")) {
                    model.features.hasCustomEq shouldBe false
                }
            }
        }

        @Test fun `allowOffOption implies listeningModeCycle`() {
            for (model in PodModel.entries) {
                if (model.features.hasAllowOffOption) {
                    model.features.hasListeningModeCycle shouldBe true
                }
            }
        }
    }
}
