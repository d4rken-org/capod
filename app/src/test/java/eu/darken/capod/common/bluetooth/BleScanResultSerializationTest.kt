package eu.darken.capod.common.bluetooth

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

/**
 * Tests backward compatibility with legacy Moshi-serialized BleScanResult cache files.
 */
class BleScanResultSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `legacy Moshi cache JSON decodes correctly`() {
        // This matches the format PodDeviceCache previously wrote via Moshi
        val legacyJson = """{"receivedAt":1709553600000,"address":"AA:BB:CC:DD:EE:FF","rssi":-55,"generatedAtNanos":123456789,"manufacturerSpecificData":{"76":"AQID"}}"""

        val result = json.decodeFromString<BleScanResult>(legacyJson)
        result.receivedAt shouldBe Instant.ofEpochMilli(1709553600000)
        result.address shouldBe "AA:BB:CC:DD:EE:FF"
        result.rssi shouldBe -55
        result.generatedAtNanos shouldBe 123456789L
        result.manufacturerSpecificData.size shouldBe 1
        result.manufacturerSpecificData[76]!!.toList() shouldBe listOf<Byte>(0x01, 0x02, 0x03)
    }

    @Test
    fun `round-trip preserves all fields`() {
        val original = BleScanResult(
            receivedAt = Instant.ofEpochMilli(1709553600000),
            address = "11:22:33:44:55:66",
            rssi = -42,
            generatedAtNanos = 987654321L,
            manufacturerSpecificData = mapOf(
                76 to byteArrayOf(0x01, 0x02, 0x03),
                77 to byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            )
        )

        val encoded = json.encodeToString(BleScanResult.serializer(), original)
        val decoded = json.decodeFromString<BleScanResult>(encoded)

        decoded.receivedAt shouldBe original.receivedAt
        decoded.address shouldBe original.address
        decoded.rssi shouldBe original.rssi
        decoded.generatedAtNanos shouldBe original.generatedAtNanos
        decoded.manufacturerSpecificData.size shouldBe original.manufacturerSpecificData.size
        original.manufacturerSpecificData.forEach { (key, value) ->
            decoded.manufacturerSpecificData[key]!!.toList() shouldBe value.toList()
        }
    }

    @Test
    fun `empty manufacturer data round-trips`() {
        val original = BleScanResult(
            receivedAt = Instant.EPOCH,
            address = "00:00:00:00:00:00",
            rssi = 0,
            generatedAtNanos = 0L,
            manufacturerSpecificData = emptyMap()
        )

        val encoded = json.encodeToString(BleScanResult.serializer(), original)
        val decoded = json.decodeFromString<BleScanResult>(encoded)

        decoded.manufacturerSpecificData shouldBe emptyMap()
    }
}
