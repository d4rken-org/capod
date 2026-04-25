package eu.darken.capod.main.ui.widget

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WidgetConfigSerializationTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `default config round-trip`() {
        val original = WidgetConfig()
        val encoded = json.encodeToString(WidgetConfig.serializer(), original)
        val decoded = json.decodeFromString(WidgetConfig.serializer(), encoded)

        decoded shouldBe original
    }

    @Test
    fun `dark preset with full transparency round-trip`() {
        val original = WidgetConfig(
            profileId = "profile-123",
            theme = WidgetTheme(
                backgroundColor = WidgetTheme.Preset.CLASSIC_DARK.presetBg,
                foregroundColor = WidgetTheme.Preset.CLASSIC_DARK.presetFg,
                backgroundAlpha = 0,
                showDeviceLabel = false,
            ),
        )
        val encoded = json.encodeToString(WidgetConfig.serializer(), original)
        val decoded = json.decodeFromString(WidgetConfig.serializer(), encoded)

        decoded shouldBe original
        decoded.theme.backgroundAlpha shouldBe 0
        decoded.theme.showDeviceLabel shouldBe false
    }

    @Test
    fun `material you theme has null colors`() {
        val original = WidgetConfig(
            profileId = "profile-x",
            theme = WidgetTheme(
                backgroundColor = null,
                foregroundColor = null,
                backgroundAlpha = 128,
                showDeviceLabel = true,
            ),
        )
        val encoded = json.encodeToString(WidgetConfig.serializer(), original)
        val decoded = json.decodeFromString(WidgetConfig.serializer(), encoded)

        decoded.theme.backgroundColor shouldBe null
        decoded.theme.foregroundColor shouldBe null
    }

    @Test
    fun `unknown future fields are ignored`() {
        val futureJson = """
            {
                "profileId": "p1",
                "theme": {
                    "backgroundAlpha": 200,
                    "showDeviceLabel": true,
                    "futureField": "ignored"
                },
                "futureTopLevel": 42
            }
        """.trimIndent()

        val decoded = json.decodeFromString(WidgetConfig.serializer(), futureJson)

        decoded.profileId shouldBe "p1"
        decoded.theme.backgroundAlpha shouldBe 200
        decoded.theme.showDeviceLabel shouldBe true
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val sparseJson = """{"profileId":"p2","theme":{}}"""

        val decoded = json.decodeFromString(WidgetConfig.serializer(), sparseJson)

        decoded.profileId shouldBe "p2"
        decoded.theme shouldBe WidgetTheme()
        decoded.theme.backgroundAlpha shouldBe 255
        decoded.theme.showDeviceLabel shouldBe true
    }

    @Test
    fun `empty json decodes to default config`() {
        val decoded = json.decodeFromString(WidgetConfig.serializer(), "{}")

        decoded.profileId shouldBe null
        decoded.theme shouldBe WidgetTheme()
    }

    @Test
    fun `profile-only config decodes`() {
        val encoded = json.encodeToString(
            WidgetConfig.serializer(),
            WidgetConfig(profileId = "only-profile"),
        )

        val decoded = json.decodeFromString(WidgetConfig.serializer(), encoded)

        decoded.profileId shouldBe "only-profile"
        decoded.theme shouldBe WidgetTheme()
    }

    @Test
    fun `theme matches preset after round-trip`() {
        val original = WidgetConfig(
            theme = WidgetTheme(
                backgroundColor = WidgetTheme.Preset.BLUE.presetBg,
                foregroundColor = WidgetTheme.Preset.BLUE.presetFg,
            ),
        )
        val encoded = json.encodeToString(WidgetConfig.serializer(), original)
        val decoded = json.decodeFromString(WidgetConfig.serializer(), encoded)

        WidgetTheme.matchPreset(decoded.theme).shouldNotBeNull() shouldBe WidgetTheme.Preset.BLUE
    }
}
