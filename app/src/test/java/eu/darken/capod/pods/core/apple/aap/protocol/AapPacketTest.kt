package eu.darken.capod.pods.core.apple.aap.protocol

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapPacketTest : BaseTest() {

    private fun hex(str: String): ByteArray = str.split(" ")
        .filter { it.isNotBlank() }
        .map { it.toInt(16).toByte() }
        .toByteArray()

    @Test
    fun `parse Connect (type 0x0000)`() {
        // CAPod's handshake packet
        val bytes = hex("00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.Connect>()
        packet.service shouldBe 0x0004
        packet.major shouldBe 0x0001
        packet.minor shouldBe 0x0002
        packet.features shouldBe 0UL
    }

    @Test
    fun `parse Connect Response (type 0x0001) status=0 zero features`() {
        val bytes = hex("01 00 04 00 00 00 01 00 03 00 00 00 00 00 00 00 00 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.ConnectResponse>()
        packet.service shouldBe 0x0004
        packet.status shouldBe 0x0000
        packet.major shouldBe 0x0001
        packet.minor shouldBe 0x0003
        packet.features shouldBe 0UL
    }

    @Test
    fun `parse Connect Response with non-zero features (Pro 1 capture)`() {
        val bytes = hex("01 00 04 00 00 00 01 00 03 00 04 00 B1 E1 04 00 51 E2")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.ConnectResponse>()
        packet.status shouldBe 0
        packet.features shouldBe 0xE251_0004_E1B1_0004UL
    }

    @Test
    fun `parse Connect Response with non-zero status does not crash`() {
        // Simulate a failure response — status=1, everything else zeroed
        val bytes = hex("01 00 04 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.ConnectResponse>()
        packet.status shouldBe 0x0001
    }

    @Test
    fun `parse Disconnect (type 0x0002)`() {
        val bytes = hex("02 00 04 00 00 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.Disconnect>()
        packet.service shouldBe 0x0004
        packet.status shouldBe 0x0000
    }

    @Test
    fun `parse Disconnect Response (type 0x0003)`() {
        val bytes = hex("03 00 04 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.DisconnectResponse>()
        packet.service shouldBe 0x0004
    }

    @Test
    fun `parse Message (type 0x0004)`() {
        // ANC mode ON control message
        val bytes = hex("04 00 04 00 09 00 0D 02 00 00 00")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.Message>()
        packet.commandType shouldBe 0x0009
        packet.payload.size shouldBe 5
        packet.payload[0] shouldBe 0x0D.toByte()
    }

    @Test
    fun `parse unknown packet type returns Unknown variant`() {
        val bytes = hex("09 00 04 00 AA BB CC")
        val packet = AapPacket.parse(bytes).shouldBeInstanceOf<AapPacket.Unknown>()
        packet.packetType shouldBe 0x0009
    }

    @Test
    fun `parse returns null for too-short input`() {
        AapPacket.parse(byteArrayOf()).shouldBeNull()
        AapPacket.parse(hex("04 00")).shouldBeNull()
        // Message packet minimum is 6 bytes (4-byte header + 2-byte command type)
        AapPacket.parse(hex("04 00 04 00 09")).shouldBeNull()
    }

    @Test
    fun `parse truncated Connect Response returns null`() {
        // Need 18 bytes for a Connect Response; this is only 16
        AapPacket.parse(hex("01 00 04 00 00 00 01 00 03 00 00 00 00 00 00 00")).shouldBeNull()
    }

    @Test
    fun `AapMessage-parse only returns Message-type packets`() {
        // Connect Response bytes — AapMessage.parse (= AapPacket.Message.parse) rejects
        val connectResponseBytes = hex("01 00 04 00 00 00 01 00 03 00 00 00 00 00 00 00 00 00")
        AapMessage.parse(connectResponseBytes).shouldBeNull()

        // Message bytes — accepted
        val messageBytes = hex("04 00 04 00 09 00 0D 02 00 00 00")
        val msg = AapMessage.parse(messageBytes)
        msg.shouldNotBeNull()
        msg.commandType shouldBe 0x0009
    }
}
