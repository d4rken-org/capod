package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import kotlin.time.Duration.Companion.seconds

/**
 * The DataStore runs on its own IO scope, so these wait in real time instead of on a test dispatcher.
 * One [WidgetSettings] per test: each instance owns a DataStore on the same file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class WidgetSettingsTest : BaseTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private val configA = WidgetConfig(
        profileId = "profile-a",
        theme = WidgetTheme(backgroundColor = 0xFF1565C0.toInt(), foregroundColor = 0xFFFFFFFF.toInt()),
    )
    private val configB = WidgetConfig(
        profileId = "profile-b",
        theme = WidgetTheme(backgroundAlpha = 128, showDeviceLabel = false),
    )

    private fun createSettings() = WidgetSettings(context = context, json = json)

    private fun runWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(10.seconds) { block() }
    }

    @Test
    fun `absent key emits the default config`() = runWithTimeout {
        val settings = createSettings()

        settings.widgetConfigFlow(1).first() shouldBe WidgetConfig()
    }

    @Test
    fun `saved config reaches a running collector`() = runWithTimeout {
        val settings = createSettings()
        val emissions = Channel<WidgetConfig>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) { settings.widgetConfigFlow(1).collect { emissions.send(it) } }

        emissions.receive() shouldBe WidgetConfig()

        settings.saveWidgetConfig(1, configA)

        emissions.receive().apply {
            profileId shouldBe configA.profileId
            theme shouldBe configA.theme
        }
        collector.cancel()
    }

    @Test
    fun `malformed JSON emits the default config`() = runWithTimeout {
        // Seeded through the SharedPreferences migration, the only way raw values reach this store.
        context.getSharedPreferences("widget_preferences", Context.MODE_PRIVATE).edit()
            .putString("widget_config_1", "{not json")
            .putString("widget_config_2", json.encodeToString(WidgetConfig.serializer(), configB))
            .commit()
        val settings = createSettings()

        settings.widgetConfigFlow(2).first() shouldBe configB
        settings.widgetConfigFlow(1).first() shouldBe WidgetConfig()
    }

    @Test
    fun `removeWidget returns to the default config`() = runWithTimeout {
        val settings = createSettings()
        settings.saveWidgetConfig(1, configA)
        settings.widgetConfigFlow(1).first() shouldBe configA

        settings.removeWidget(1)

        settings.widgetConfigFlow(1).first() shouldBe WidgetConfig()
    }

    @Test
    fun `unrelated key changes do not re-emit`() = runWithTimeout {
        val settings = createSettings()
        settings.saveWidgetConfig(1, configA)

        val firstEmission = CompletableDeferred<Unit>()
        val emissions = async(Dispatchers.IO) {
            settings.widgetConfigFlow(1)
                .onEach { firstEmission.complete(Unit) }
                .take(2)
                .toList()
        }
        settings.getWidgetConfig(1) shouldBe configA
        firstEmission.await()

        settings.saveWidgetConfig(2, configB)
        settings.saveWidgetConfig(1, configB)

        emissions.await() shouldBe listOf(configA, configB)
    }
}
