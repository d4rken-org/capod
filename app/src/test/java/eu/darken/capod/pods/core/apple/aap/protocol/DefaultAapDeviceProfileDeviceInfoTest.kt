package eu.darken.capod.pods.core.apple.aap.protocol

import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Edge-case coverage for `DefaultAapDeviceProfile.decodeDeviceInfo`. The three
 * `AirPods*AapSessionTest` classes hold the authoritative per-model golden
 * captures; this file covers the parser's behaviour when payloads deviate
 * from the full 15-segment schema.
 */
class DefaultAapDeviceProfileDeviceInfoTest : BaseAapSessionTest() {

    override val podModel = PodModel.AIRPODS_PRO3

    private fun infoMessage(vararg hexParts: String): AapMessage =
        aapMessage("04 00 04 00 1D 00 02 ED 00 04 00", *hexParts)

    @Test
    fun `truncated payload with only required system fields decodes without crashing`() {
        val msg = infoMessage(
            "41 69 72 50 6F 64 73 00",      // "AirPods\0"
            "41 32 30 38 34 00",             // "A2084\0"
            "41 70 70 6C 65 00",             // "Apple\0"
            "53 31 32 33 00",                 // "S123\0"
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.name shouldBe "AirPods"
        info.modelNumber shouldBe "A2084"
        info.manufacturer shouldBe "Apple"
        info.serialNumber shouldBe "S123"
        info.firmwareVersion shouldBe ""
        info.firmwareVersionPending.shouldBeNull()
        info.marketingVersion.shouldBeNull()
        info.leftEarbudUuid.shouldBeNull()
        info.rightEarbudUuid.shouldBeNull()
        info.leftEarbudFirstPaired.shouldBeNull()
        info.rightEarbudFirstPaired.shouldBeNull()
    }

    @Test
    fun `truncated after marketingVersion leaves UUIDs and timestamps null`() {
        val msg = infoMessage(
            "41 69 72 50 6F 64 73 00",
            "41 32 30 38 34 00",
            "41 70 70 6C 65 00",
            "53 31 32 33 00",
            "38 31 00",                      // firmware active "81\0"
            "38 31 00",                      // firmware pending (same) → should be null
            "31 2E 30 2E 30 00",             // hardware "1.0.0\0"
            "65 61 00",                       // EA protocol "ea\0"
            "4C 31 00",                       // left bud serial "L1\0"
            "52 31 00",                       // right bud serial "R1\0"
            "38 34 35 34 00",                // marketing version "8454\0"
            // No UUID blob, no timestamps
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.marketingVersion shouldBe "8454"
        info.firmwareVersion shouldBe "81"
        info.firmwareVersionPending.shouldBeNull()
        info.hardwareVersion shouldBe "1.0.0"
        info.eaProtocolName shouldBe "ea"
        info.leftEarbudSerial shouldBe "L1"
        info.rightEarbudSerial shouldBe "R1"
        info.leftEarbudUuid.shouldBeNull()
        info.rightEarbudUuid.shouldBeNull()
        info.leftEarbudFirstPaired.shouldBeNull()
        info.rightEarbudFirstPaired.shouldBeNull()
    }

    @Test
    fun `partial UUID present - single left UUID but no right - does not fail`() {
        val leftUuidHex = "AA BB CC DD EE FF 11 22 33 44 55 66 77 88 99 00 01"
        val msg = infoMessage(
            "41 00", "41 00", "41 00", "53 00",
            "46 00", "46 00", "48 00", "65 00",
            "4C 00", "52 00", "4D 00",
            leftUuidHex,
            // 17 bytes — enough for left UUID, right UUID read fails
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.leftEarbudUuid!!.size shouldBe 17
        info.leftEarbudUuid!![0] shouldBe 0xAA.toByte()
        info.leftEarbudUuid!![16] shouldBe 0x01.toByte()
        info.rightEarbudUuid.shouldBeNull()
    }

    @Test
    fun `firmware pending decoded when different from active firmware`() {
        val msg = infoMessage(
            "41 00", "41 00", "41 00", "53 00",
            "38 31 2E 31 00",       // firmware active "81.1\0"
            "38 31 2E 32 00",       // firmware pending "81.2\0" (different)
            "31 2E 30 00",
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.firmwareVersion shouldBe "81.1"
        info.firmwareVersionPending shouldBe "81.2"
    }

    @Test
    fun `firmware pending suppressed when matching active firmware`() {
        val msg = infoMessage(
            "41 00", "41 00", "41 00", "53 00",
            "38 31 00",
            "38 31 00",
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.firmwareVersion shouldBe "81"
        info.firmwareVersionPending.shouldBeNull()
    }

    @Test
    fun `UUID with embedded zero bytes is preserved verbatim`() {
        val uuidWithZero = "11 00 22 00 33 44 55 66 77 88 99 AA BB CC DD EE FF"
        val msg = infoMessage(
            "41 00", "41 00", "41 00", "53 00",
            "46 00", "46 00", "48 00", "65 00",
            "4C 00", "52 00", "4D 00",
            uuidWithZero,
            uuidWithZero,
        )
        val info = profile.decodeDeviceInfo(msg)!!
        // Embedded 0x00 at indices 1 and 3 must be preserved — we must NOT split UUID on NUL
        info.leftEarbudUuid!!.size shouldBe 17
        info.leftEarbudUuid!![0] shouldBe 0x11.toByte()
        info.leftEarbudUuid!![1] shouldBe 0x00.toByte()
        info.leftEarbudUuid!![2] shouldBe 0x22.toByte()
        info.leftEarbudUuid!![3] shouldBe 0x00.toByte()
        info.rightEarbudUuid!![1] shouldBe 0x00.toByte()
    }

    @Test
    fun `malformed first-paired timestamp is silently dropped`() {
        val uuid = "11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF 00 01"
        val msg = infoMessage(
            "41 00", "41 00", "41 00", "53 00",
            "46 00", "46 00", "48 00", "65 00",
            "4C 00", "52 00", "4D 00",
            uuid, uuid,
            "6E 6F 74 61 64 61 74 65 00",    // "notadate\0"
            "31 36 39 37 34 38 30 32 31 31 00", // valid right timestamp
        )
        val info = profile.decodeDeviceInfo(msg)!!
        info.leftEarbudFirstPaired.shouldBeNull()
        info.rightEarbudFirstPaired shouldBe java.time.Instant.ofEpochSecond(1697480211L)
    }

    @Test
    fun `decodeDeviceInfo returns null for non-Information messages`() {
        val msg = aapMessage("04 00 04 00 09 00 0D 02 00 00 00") // Control message, not 0x1D
        profile.decodeDeviceInfo(msg).shouldBeNull()
    }

    @Test
    fun `decodeDeviceInfo returns null for too-short payload`() {
        val msg = aapMessage("04 00 04 00 1D 00 02") // just 1 byte of payload after command type
        profile.decodeDeviceInfo(msg).shouldBeNull()
    }
}
