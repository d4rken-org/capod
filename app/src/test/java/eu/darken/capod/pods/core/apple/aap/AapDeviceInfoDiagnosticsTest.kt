package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.DefaultAapDeviceProfile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Tests for the issue #173 diagnostic helper [AapDeviceInfoDiagnostics.describeSegments].
 *
 * The helper must:
 * - Work on real captured 0x1D payloads (matches production decoder on ASCII slots).
 * - Decode non-ASCII UTF-8 (emoji, non-Latin script) — the production parser drops these.
 * - Always emit segments for any payload shape, even when [DefaultAapDeviceProfile.decodeDeviceInfo]
 *   would return null (malformed / unknown-shaped packets).
 */
class AapDeviceInfoDiagnosticsTest : BaseTest() {

    private fun hex(s: String): ByteArray = s
        .replace("\n", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .map { it.toInt(16).toByte() }
        .toByteArray()

    @Test
    fun `describeDeviceInfoSegments decodes known slots from AirPods Pro 2 USB-C capture`() {
        // Exact payload (post message-header) from AirPodsPro2UsbcAapSessionTest.
        // Headers `02 DF 00 04 00` are skipped by the helper until the first printable ASCII byte.
        val payload = hex(
            """
            02 DF 00 04 00
            41 69 72 50 6F 64 73 20 50 72 6F 00
            41 33 30 34 38 00
            41 70 70 6C 65 20 49 6E 63 2E 00
            57 35 4A 37 4B 56 30 4E 30 34 00
            38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 30 38 32 00
            38 31 2E 32 36 37 35 30 30 30 30 37 35 30 30 30 30 30 30 2E 36 30 38 32 00
            31 2E 30 2E 30 00
            63 6F 6D 2E 61 70 70 6C 65 2E 61 63 63 65 73 73 6F 72 79 2E 75 70 64 61 74 65 72 2E 61 70 70 2E 37 31 00
            48 33 4B 4C 37 48 52 39 32 36 4A 59 00
            48 33 4B 4C 32 41 59 4C 32 36 4B 30 00
            38 34 35 34 34 38 30 00
            1F 3F B4 B7 E9 81 48 11 94 6B C2 6F 3C 5F 5A 34 0B AB 7E 42 AA BD F1 49 E3 A8 98 E7 81 D6 04 F5 68 1F
            31 36 39 37 34 38 30 32 31 31 00 31 36 39 37 34 38 30 32 31 31 00
            """
        )

        val segments = AapDeviceInfoDiagnostics.describeSegments(payload)

        // The first five segments mirror the production decoder's name/modelNumber/manufacturer/
        // serialNumber/firmwareVersion slots.
        segments[0].utf8 shouldBe "AirPods Pro"
        segments[1].utf8 shouldBe "A3048"
        segments[2].utf8 shouldBe "Apple Inc."
        segments[3].utf8 shouldBe "W5J7KV0N04"
        segments[4].utf8 shouldBe "81.2675000075000000.6082"

        // Every well-formed text segment retains a hex rendering too.
        segments[0].hex shouldBe "416972506F64732050726F"

        // The encrypted blob immediately follows "8454480" without a NUL separator, so it merges
        // with the subsequent manufacturing-date string into a single non-UTF-8 chunk. The helper
        // surfaces it as a segment with utf8 == null and a hex rendering that still contains the
        // original blob bytes so maintainers can spot it in a debug recording.
        val nonUtf8 = segments.firstOrNull { it.utf8 == null }
        nonUtf8.shouldNotBeNull()
        nonUtf8.hex.contains("1F3FB4B7E98148") shouldBe true
    }

    @Test
    fun `describeDeviceInfoSegments decodes UTF-8 emoji that the production ASCII parser would drop`() {
        // Production parseNullTerminatedStrings filters to 0x20..0x7E and decodes as US_ASCII,
        // so any emoji / non-Latin engraving is silently skipped. The diagnostic helper must not.
        val engraving = "My AirPods 🌈"
        val payload = hex("02 DF 00 04 00") +
            engraving.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) +
            "A3048".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00)

        val segments = AapDeviceInfoDiagnostics.describeSegments(payload)

        segments.size shouldBe 2
        segments[0].utf8 shouldBe engraving
        segments[1].utf8 shouldBe "A3048"
    }

    @Test
    fun `describeDeviceInfoSegments emits segments even when decodeDeviceInfo returns null`() {
        // Only two strings — production decoder requires at least four and returns null. The
        // diagnostic dump must still surface whatever segments it finds so maintainers can inspect
        // unexpected-shape packets from an engraved device.
        val payload = hex("02 DF 00 04 00") +
            "Name".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) +
            "Model".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00)
        val fullMessageBytes = hex("04 00 04 00 1D 00") + payload
        val message = AapMessage.parse(fullMessageBytes)!!

        DefaultAapDeviceProfile(PodModel.AIRPODS_PRO2_USBC).decodeDeviceInfo(message).shouldBeNull()

        val segments = AapDeviceInfoDiagnostics.describeSegments(payload)
        segments.size shouldBe 2
        segments[0].utf8 shouldBe "Name"
        segments[1].utf8 shouldBe "Model"
    }
}
