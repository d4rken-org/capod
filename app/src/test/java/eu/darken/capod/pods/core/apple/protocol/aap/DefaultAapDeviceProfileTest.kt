package eu.darken.capod.pods.core.apple.protocol.aap

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DefaultAapDeviceProfileTest : BaseTest() {

    private val profile = DefaultAapDeviceProfile()

    @Test
    fun `encode handshake is 16 bytes`() {
        val handshake = profile.encodeHandshake()
        handshake.size shouldBe 16
        handshake[0] shouldBe 0x00.toByte()
        handshake[4] shouldBe 0x01.toByte()
    }

    @Test
    fun `encode SetAncMode ON`() {
        val bytes = profile.encodeCommand(AapCommand.SetAncMode(AncModeValue.ON))
        bytes.size shouldBe 11
        bytes[4] shouldBe 0x09.toByte()  // command type low byte
        bytes[5] shouldBe 0x00.toByte()  // command type high byte
        bytes[6] shouldBe 0x0D.toByte()  // setting ID = ANC mode
        bytes[7] shouldBe 0x02.toByte()  // value = ON
    }

    @Test
    fun `encode SetAncMode TRANSPARENCY`() {
        val bytes = profile.encodeCommand(AapCommand.SetAncMode(AncModeValue.TRANSPARENCY))
        bytes[6] shouldBe 0x0D.toByte()
        bytes[7] shouldBe 0x03.toByte()
    }

    @Test
    fun `encode SetAncMode ADAPTIVE`() {
        val bytes = profile.encodeCommand(AapCommand.SetAncMode(AncModeValue.ADAPTIVE))
        bytes[7] shouldBe 0x04.toByte()
    }

    @Test
    fun `encode SetConversationalAwareness enabled`() {
        val bytes = profile.encodeCommand(AapCommand.SetConversationalAwareness(true))
        bytes[6] shouldBe 0x18.toByte()  // setting ID
        bytes[7] shouldBe 0x01.toByte()  // enabled
    }

    @Test
    fun `encode SetConversationalAwareness disabled`() {
        val bytes = profile.encodeCommand(AapCommand.SetConversationalAwareness(false))
        bytes[6] shouldBe 0x18.toByte()
        bytes[7] shouldBe 0x00.toByte()
    }

    @Test
    fun `decode ANC mode setting`() {
        val msg = AapMessage.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x02, 0x00, 0x00, 0x00)
        )!!
        val result = profile.decodeSetting(msg)
        result.shouldNotBeNull()
        val (key, setting) = result
        key shouldBe AapSetting.AncMode::class
        (setting as AapSetting.AncMode).current shouldBe AncModeValue.ON
    }

    @Test
    fun `decode transparency mode`() {
        val msg = AapMessage.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x03, 0x00, 0x00, 0x00)
        )!!
        val (_, setting) = profile.decodeSetting(msg)!!
        (setting as AapSetting.AncMode).current shouldBe AncModeValue.TRANSPARENCY
    }

    @Test
    fun `decode conversational awareness`() {
        val msg = AapMessage.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x18, 0x01, 0x00, 0x00, 0x00)
        )!!
        val (key, setting) = profile.decodeSetting(msg)!!
        key shouldBe AapSetting.ConversationalAwareness::class
        (setting as AapSetting.ConversationalAwareness).enabled shouldBe true
    }

    @Test
    fun `decode unknown setting returns null`() {
        val msg = AapMessage.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x7F.toByte(), 0x01, 0x00, 0x00, 0x00)
        )!!
        profile.decodeSetting(msg).shouldBeNull()
    }

    @Test
    fun `decode non-settings message returns null`() {
        val msg = AapMessage.parse(
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x1D, 0x00, 0x01, 0x02, 0x03, 0x04)
        )!!
        profile.decodeSetting(msg).shouldBeNull()
    }

    @Test
    fun `round-trip encode then decode ANC mode`() {
        val command = AapCommand.SetAncMode(AncModeValue.TRANSPARENCY)
        val encoded = profile.encodeCommand(command)
        val msg = AapMessage.parse(encoded)!!
        val (_, setting) = profile.decodeSetting(msg)!!
        (setting as AapSetting.AncMode).current shouldBe AncModeValue.TRANSPARENCY
    }
}
