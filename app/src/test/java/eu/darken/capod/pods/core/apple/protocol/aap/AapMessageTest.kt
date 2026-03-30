package eu.darken.capod.pods.core.apple.protocol.aap

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapMessageTest : BaseTest() {

    @Test
    fun `parse settings message`() {
        // ANC mode = ON (0x02)
        val raw = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x02, 0x00, 0x00, 0x00)
        val msg = AapMessage.parse(raw)
        msg.shouldNotBeNull()
        msg.commandType shouldBe 0x0009
        msg.payload.size shouldBe 5
        msg.payload[0] shouldBe 0x0D.toByte()
        msg.payload[1] shouldBe 0x02.toByte()
    }

    @Test
    fun `parse handshake response`() {
        val raw = byteArrayOf(
            0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val msg = AapMessage.parse(raw)
        msg.shouldNotBeNull()
        msg.commandType shouldBe 0x0000
    }

    @Test
    fun `parse returns null for too-short input`() {
        AapMessage.parse(byteArrayOf(0x04, 0x00, 0x04)).shouldBeNull()
        AapMessage.parse(byteArrayOf()).shouldBeNull()
    }

    @Test
    fun `parse minimum valid message (header + command, no payload)`() {
        val raw = byteArrayOf(0x04, 0x00, 0x02, 0x00, 0x09, 0x00)
        val msg = AapMessage.parse(raw)
        msg.shouldNotBeNull()
        msg.commandType shouldBe 0x0009
        msg.payload.size shouldBe 0
    }

    @Test
    fun `parse preserves raw bytes`() {
        val raw = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x02, 0x00, 0x00, 0x00)
        val msg = AapMessage.parse(raw)!!
        msg.raw.contentEquals(raw) shouldBe true
    }
}
