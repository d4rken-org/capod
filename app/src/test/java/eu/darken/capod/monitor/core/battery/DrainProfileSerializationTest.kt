package eu.darken.capod.monitor.core.battery

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DrainProfileSerializationTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `profiles stored before charge rates and the model tag decode with defaults`() {
        val legacyJson = """
            {
                "rates": {
                    "UNKNOWN/LEFT": {
                        "fractionPerHour": 0.15,
                        "sampleCount": 12,
                        "updatedAt": 1700000000000
                    }
                }
            }
        """.trimIndent()

        val profile = json.decodeFromString<DrainProfile>(legacyJson)

        profile.model shouldBe null
        profile.chargeRates shouldBe emptyMap()
        profile.chargeBands shouldBe emptyMap()
        profile.listeningRates shouldBe emptyMap()
        profile.caseTransfer shouldBe null
        profile.rates.getValue("UNKNOWN/LEFT").updateCount shouldBe 1
    }

    @Test
    fun `full profile round-trips`() {
        val profile = DrainProfile(
            model = "AIRPODS_PRO2",
            rates = mapOf(
                "ON/LEFT" to DrainProfile.LearnedRate(
                    fractionPerHour = 0.21f,
                    sampleCount = 9,
                    updateCount = 4,
                    updatedAt = Instant.ofEpochMilli(1700000000000L),
                )
            ),
            chargeRates = mapOf(
                "LEFT" to DrainProfile.LearnedRate(
                    fractionPerHour = 1.3f,
                    sampleCount = 5,
                    updateCount = 2,
                    updatedAt = Instant.ofEpochMilli(1700000000000L),
                )
            ),
            chargeBands = mapOf(
                "LEFT" to mapOf(
                    "TAPER" to DrainProfile.LearnedRate(
                        fractionPerHour = 0.9f,
                        sampleCount = 3,
                        updateCount = 2,
                        updatedAt = Instant.ofEpochMilli(1700000000000L),
                    )
                )
            ),
            listeningRates = mapOf(
                "ON/LEFT" to DrainProfile.LearnedRate(
                    fractionPerHour = 0.24f,
                    sampleCount = 7,
                    updateCount = 3,
                    updatedAt = Instant.ofEpochMilli(1700000000000L),
                )
            ),
            caseTransfer = DrainProfile.TransferRatio(
                ratio = 6.5f,
                updateCount = 4,
                updatedAt = Instant.ofEpochMilli(1700000000000L),
            ),
        )

        json.decodeFromString<DrainProfile>(json.encodeToString(DrainProfile.serializer(), profile)) shouldBe profile
    }
}
