package eu.darken.capod.common

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The return value is a contract, not a convenience: the FOSS sponsor unlock heuristic only arms
 * when the page actually opened. Every swallow point has to report the failure back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class WebpageToolTest : BaseTest() {

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a launched page reports success`() {
        WebpageTool(application).open(URL) shouldBe true

        shadowOf(application).nextStartedActivity!!.data shouldBe URL.toUri()
    }

    @Test
    fun `a missing browser reports failure`() {
        val context = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) = throw ActivityNotFoundException("No browser")
        }

        WebpageTool(context).open(URL) shouldBe false
    }

    @Test
    fun `a denied launch reports failure`() {
        val context = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) = throw SecurityException("Permission Denial")
        }

        WebpageTool(context).open(URL) shouldBe false
    }

    companion object {
        private const val URL = "https://github.com/sponsors/d4rken"
    }
}
