package eu.darken.capod.profiles.core

import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Verifies the legacy reaction DataStore key names and JSON serialization format.
 * A key-name mismatch would silently zero all reaction settings for upgrading users.
 */
class LegacyReactionSettingsReaderTest : BaseTest() {

    /**
     * The keys that were used by the now-deleted `ReactionSettings` class.
     * If any of these change in `LegacyReactionSettingsReader`, upgrading users lose their settings.
     * This test acts as a frozen snapshot — DO NOT update these values to "fix" the test.
     */
    @Test
    fun `legacy key names match the deleted ReactionSettings class`() {
        // Boolean keys — frozen snapshot of the old ReactionSettings key names.
        // If LegacyReactionSettingsReader keys change, upgrading users silently lose settings.
        LegacyReactionSettingsReader.AUTO_PAUSE_KEY.name shouldBe "reaction.autopause.enabled"
        LegacyReactionSettingsReader.AUTO_PLAY_KEY.name shouldBe "reaction.autoplay.enabled"
        LegacyReactionSettingsReader.AUTO_CONNECT_KEY.name shouldBe "reaction.autoconnect.enabled"
        LegacyReactionSettingsReader.POPUP_CASE_OPEN_KEY.name shouldBe "reaction.popup.caseopen"
        LegacyReactionSettingsReader.POPUP_CONNECTED_KEY.name shouldBe "reaction.popup.connected"
        LegacyReactionSettingsReader.ONE_POD_KEY.name shouldBe "reaction.onepod.enabled"

        // String key for serialized enum
        LegacyReactionSettingsReader.CONDITION_KEY.name shouldBe "reaction.autoconnect.condition"
    }

    /**
     * Verifies the project Json can round-trip AutoConnectCondition enum values using their
     * @SerialName annotations. The legacy DataStore stored these as JSON strings.
     */
    @Test
    fun `AutoConnectCondition round-trips through project Json`() {
        val projectJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            classDiscriminator = "type"
        }

        for (condition in AutoConnectCondition.entries) {
            val encoded = projectJson.encodeToString(condition)
            val decoded = projectJson.decodeFromString<AutoConnectCondition>(encoded)
            decoded shouldBe condition
        }
    }

    /**
     * Verifies the serialized form matches what the deleted ReactionSettings class stored.
     * The old class used `createValue(key, default, json, onErrorFallbackToDefault = true)`
     * which serializes the enum to its @SerialName value as a JSON string.
     */
    @Test
    fun `AutoConnectCondition serialized format matches legacy storage`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            classDiscriminator = "type"
        }

        json.encodeToString(AutoConnectCondition.WHEN_SEEN) shouldBe "\"autoconnect.condition.seen\""
        json.encodeToString(AutoConnectCondition.CASE_OPEN) shouldBe "\"autoconnect.condition.case\""
        json.encodeToString(AutoConnectCondition.IN_EAR) shouldBe "\"autoconnect.condition.inear\""
    }
}
