package eu.darken.capod.pods.core.apple.aap.engine

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class HidTrackerTest : BaseTest() {

    private fun hexToBytes(hex: String): ByteArray =
        hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    // ── Classification ─────────────────────────────────────

    @Nested
    inner class ClassifyTests {

        @Test
        fun `service directory frame from Pro 3 capture`() {
            val payload = hexToBytes(
                "FE 00 00 06 41 50 00 00 00 80 00 00 41 4F 50 00 00 80 00 00 " +
                        "52 54 50 00 00 80 00 00 42 54 4D 00 00 80 00 00 " +
                        "44 53 50 31 00 80 00 00 44 53 50 32 00 80 00 00"
            )
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.ServiceDirectory>()
            result.services.shouldContainExactly("AP", "AOP", "RTP", "BTM", "DSP1", "DSP2")
        }

        @Test
        fun `descriptor bulk frame phase 0x81`() {
            val fill = ByteArray(65) { 0xFF.toByte() }
            val payload = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Descriptor>()
            result.phase shouldBe 0x81
            result.fill shouldBe 0xFF
        }

        @Test
        fun `descriptor bulk frame phase 0x02`() {
            val fill = ByteArray(65) { 0xEF.toByte() }
            val payload = hexToBytes("00 04 00 00 44 00 01 C3 02") + fill
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Descriptor>()
            result.phase shouldBe 0x02
            result.fill shouldBe 0xEF
        }

        @Test
        fun `terminator frame`() {
            val payload = hexToBytes("00 04 00 00 01 00 FF")
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Terminator>()
            result.payloadSize shouldBe 7
        }

        @Test
        fun `short frame ending in FF but not 7 bytes is Other`() {
            val payload = hexToBytes("00 04 00 FF")
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Other>()
        }

        @Test
        fun `empty payload is Other`() {
            val result = HidTracker.classify(ByteArray(0))
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Other>()
        }

        @Test
        fun `random payload is Other`() {
            val payload = hexToBytes("AB CD EF 01 02 03 04 05 06 07 08")
            val result = HidTracker.classify(payload)
            result.shouldBeInstanceOf<HidTracker.HidFrameType.Other>()
        }
    }

    // ── Batching ───────────────────────────────────────────

    @Nested
    inner class BatchingTests {

        @Test
        fun `same phase and fill batches together`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fill = ByteArray(65) { 0xFF.toByte() }
            val payload = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill

            tracker.consume(payload)
            tracker.consume(payload)
            tracker.consume(payload)
            logs.shouldBeEmpty()

            tracker.flush()
            logs.size shouldBe 1
            logs[0] shouldBe "HID: 3 descriptor frames phase=0x81 fill=0xFF"
        }

        @Test
        fun `phase change flushes previous batch`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fill81 = ByteArray(65) { 0xFF.toByte() }
            val payload81 = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill81

            val fill02 = ByteArray(65) { 0xEF.toByte() }
            val payload02 = hexToBytes("00 04 00 00 44 00 01 C3 02") + fill02

            tracker.consume(payload81)
            tracker.consume(payload81)
            tracker.consume(payload02)

            logs.size shouldBe 1
            logs[0] shouldBe "HID: 2 descriptor frames phase=0x81 fill=0xFF"

            tracker.flush()
            logs.size shouldBe 2
            logs[1] shouldBe "HID: 1 descriptor frames phase=0x02 fill=0xEF"
        }

        @Test
        fun `same phase but different fill creates separate batch`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fillFF = ByteArray(65) { 0xFF.toByte() }
            val payloadFF = hexToBytes("00 04 00 00 44 00 01 A1 81") + fillFF

            val fillEF = ByteArray(65) { 0xEF.toByte() }
            val payloadEF = hexToBytes("00 04 00 00 44 00 01 C3 81") + fillEF

            tracker.consume(payloadFF)
            tracker.consume(payloadEF)

            logs.size shouldBe 1
            logs[0] shouldBe "HID: 1 descriptor frames phase=0x81 fill=0xFF"

            tracker.flush()
            logs.size shouldBe 2
            logs[1] shouldBe "HID: 1 descriptor frames phase=0x81 fill=0xEF"
        }

        @Test
        fun `service directory flushes pending batch`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fill = ByteArray(65) { 0xFF.toByte() }
            val descriptor = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill
            val directory = hexToBytes(
                "FE 00 00 02 41 50 00 00 00 80 00 00 41 4F 50 00 00 80 00 00"
            )

            tracker.consume(descriptor)
            tracker.consume(descriptor)
            tracker.consume(directory)

            logs.size shouldBe 2
            logs[0] shouldBe "HID: 2 descriptor frames phase=0x81 fill=0xFF"
            logs[1] shouldBe "HID: services=[AP, AOP] (20B)"
        }

        @Test
        fun `terminator flushes pending batch`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fill = ByteArray(65) { 0xFF.toByte() }
            val descriptor = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill
            val terminator = hexToBytes("00 04 00 00 01 00 FF")

            tracker.consume(descriptor)
            tracker.consume(terminator)

            logs.size shouldBe 2
            logs[0] shouldBe "HID: 1 descriptor frames phase=0x81 fill=0xFF"
            logs[1] shouldBe "HID: terminator (7B)"
        }

        @Test
        fun `flush with zero count is no-op`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            tracker.flush()

            logs.shouldBeEmpty()
        }

        @Test
        fun `reset clears pending batch without logging`() {
            val logs = mutableListOf<String>()
            val tracker = HidTracker { logs.add(it) }

            val fill = ByteArray(65) { 0xFF.toByte() }
            val descriptor = hexToBytes("00 04 00 00 44 00 01 A1 81") + fill

            tracker.consume(descriptor)
            tracker.consume(descriptor)
            tracker.reset()
            tracker.flush()

            logs.shouldBeEmpty()
        }
    }
}