package eu.darken.capod.monitor.core.cache

import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Guards the cache schema against accidental breakage when AAP-layer fields are
 * renamed. The wire key `buildNumber` was renamed to `marketingVersion` in the
 * domain type; the cache keeps the original key via `@SerialName("buildNumber")`
 * so devices that upgrade from older CAPod versions don't drop their cached data.
 */
class CachedDeviceStateMigrationTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pre-rename JSON with buildNumber key loads into marketingVersion field`() {
        val legacy = """
            {
                "profileId": "legacy-profile",
                "model": "airpods.pro3",
                "address": "AA:BB:CC:DD:EE:FF",
                "deviceName": "AirPods Pro 3",
                "serialNumber": "W5J7KV0N04",
                "firmwareVersion": "81.26750000075000000.6503",
                "leftEarbudSerial": "GMPHNZ16P5Z0000UHZ",
                "rightEarbudSerial": "GMVHNX15UED0000UHY",
                "buildNumber": "8454624",
                "lastSeenAt": 1767364074000
            }
        """.trimIndent()

        val state = json.decodeFromString(CachedDeviceState.serializer(), legacy)
        state.marketingVersion shouldBe "8454624"
        state.profileId shouldBe "legacy-profile"
        state.model shouldBe PodModel.AIRPODS_PRO3
        state.firmwareVersion shouldBe "81.26750000075000000.6503"
    }

    @Test
    fun `round-trip serializes marketingVersion back to buildNumber key`() {
        val state = CachedDeviceState(
            profileId = "roundtrip",
            model = PodModel.AIRPODS_PRO3,
            marketingVersion = "8454624",
            lastSeenAt = java.time.Instant.ofEpochMilli(1767364074000L),
        )
        val encoded = json.encodeToString(CachedDeviceState.serializer(), state)
        // Cache back-compat: the JSON key is still "buildNumber", never the in-memory name
        (encoded.contains("\"buildNumber\":\"8454624\"")) shouldBe true
        (encoded.contains("\"marketingVersion\"")) shouldBe false
    }

    @Test
    fun `deviceInfo surfaces marketingVersion from legacy cached state`() {
        val legacy = """
            {
                "profileId": "legacy-profile",
                "model": "airpods.pro3",
                "deviceName": "AirPods",
                "serialNumber": "ABC",
                "firmwareVersion": "81.x",
                "buildNumber": "8454480",
                "lastSeenAt": 1697480211000
            }
        """.trimIndent()

        val state = json.decodeFromString(CachedDeviceState.serializer(), legacy)
        state.deviceInfo!!.marketingVersion shouldBe "8454480"
    }

    @Test
    fun `deviceInfo is non-null when only new earbud-serial fields are present`() {
        val state = CachedDeviceState(
            profileId = "earbud-only",
            model = PodModel.AIRPODS_PRO3,
            leftEarbudSerial = "L-9",
            rightEarbudSerial = "R-9",
            marketingVersion = "8888",
            lastSeenAt = java.time.Instant.ofEpochMilli(1767364074000L),
        )

        val info = state.deviceInfo!!
        info.leftEarbudSerial shouldBe "L-9"
        info.rightEarbudSerial shouldBe "R-9"
        info.marketingVersion shouldBe "8888"
        info.name shouldBe ""
    }
}
