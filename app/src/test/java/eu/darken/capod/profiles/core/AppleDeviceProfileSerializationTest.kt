package eu.darken.capod.profiles.core

import eu.darken.capod.reaction.core.charged.ChargedSlotScope
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppleDeviceProfileSerializationTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `profiles stored before the charged reaction decode with defaults`() {
        val legacyJson = """
            {
                "id": "test-id",
                "label": "My Pods"
            }
        """.trimIndent()

        val profile = json.decodeFromString<AppleDeviceProfile>(legacyJson)

        profile.notifyWhenCharged shouldBe false
        profile.chargedThreshold shouldBe ReactionConfig.DEFAULT_CHARGED_THRESHOLD
        profile.chargedSlotScope shouldBe ChargedSlotScope.PODS_AND_CASE
        profile.reactionConfig.chargedSlotScope shouldBe ChargedSlotScope.PODS_AND_CASE
    }

    @Test
    fun `charged reaction settings round-trip`() {
        val profile = AppleDeviceProfile(
            label = "My Pods",
            notifyWhenCharged = true,
            chargedThreshold = 80,
            chargedSlotScope = ChargedSlotScope.PODS,
        )

        val decoded = json.decodeFromString<AppleDeviceProfile>(json.encodeToString(profile))

        decoded.notifyWhenCharged shouldBe true
        decoded.chargedThreshold shouldBe 80
        decoded.chargedSlotScope shouldBe ChargedSlotScope.PODS
        decoded.reactionConfig shouldBe profile.reactionConfig
    }
}
