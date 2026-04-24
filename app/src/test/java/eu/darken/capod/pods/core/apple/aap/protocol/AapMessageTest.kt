package eu.darken.capod.pods.core.apple.aap.protocol

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
    fun `handshake response parses via AapMessage-parse as null (not a Message packet)`() {
        // Packet type 0x0001 = Connect Response. AapMessage.parse now only returns
        // Message-type packets; the full packet lives in AapPacket.parse.
        // Historically this test asserted it decoded as a Message with commandType=0 —
        // that was treating the Connect Response's `status` field as a command ID.
        val raw = byteArrayOf(
            0x01, 0x00, 0x04, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        AapMessage.parse(raw).shouldBeNull()
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
