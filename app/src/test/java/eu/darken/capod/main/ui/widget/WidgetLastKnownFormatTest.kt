package eu.darken.capod.main.ui.widget

import android.app.Application
import android.provider.Settings
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WidgetLastKnownFormatTest {

    private val at = Instant.parse("2026-10-13T22:15:00Z")

    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun setup() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun teardown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    private fun format(timeFormat: String): String {
        val context = RuntimeEnvironment.getApplication()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, timeFormat)
        return formatWidgetLastKnown(context, at)
    }

    @Test
    fun `shows date and 24h time`() {
        val text = format("24")

        text shouldContain "Oct"
        text shouldContain "13"
        text shouldContain "22:15"
    }

    @Test
    fun `shows date and 12h time`() {
        val text = format("12")

        text shouldContain "Oct"
        text shouldContain "13"
        text shouldContain "10:15"
        text shouldNotContain "22:15"
    }
}
