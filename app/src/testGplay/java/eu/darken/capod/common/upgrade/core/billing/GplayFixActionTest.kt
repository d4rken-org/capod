package eu.darken.capod.common.upgrade.core.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import eu.darken.capod.R
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The error dialog's "Google Play" button runs on an activity context: a device where the launch is
 * refused must get the failure reported through the dialog (which can show the full message), not a
 * clipped toast and not a crash. The successful launch is covered by ComposeErrorDialogTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayFixActionTest : BaseTest() {

    /** Play is installed but unreachable: disabled app, restricted profile or a guarding ROM. */
    class DeniedLaunchActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw SecurityException("Permission Denial")
    }

    /** Play isn't on the device at all, so nothing resolves the app info intent. */
    class MissingPlayActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw ActivityNotFoundException("No Activity found")
    }

    private fun <T : Activity> activityOf(clazz: Class<T>): T = Robolectric.buildActivity(clazz).setup().get()

    private fun fixActionOf(activity: Activity) = GplayServiceUnavailableException(RuntimeException("Play hiccup"))
        .getLocalizedError(activity).fixAction.shouldNotBeNull()

    @Test
    fun `a denied launch reports through the dialog instead of a toast`() {
        val activity = activityOf(DeniedLaunchActivity::class.java)

        shouldThrow<SecurityException> { fixActionOf(activity).invoke(activity) }

        // A toast caps at 2 lines and clipped this message (French lost a whole condition
        // mid-word) — the dialog renders it inline instead.
        ShadowToast.getLatestToast() shouldBe null
    }

    @Test
    fun `an unresolvable launch reports through the dialog instead of a toast`() {
        val activity = activityOf(MissingPlayActivity::class.java)

        shouldThrow<ActivityNotFoundException> { fixActionOf(activity).invoke(activity) }

        ShadowToast.getLatestToast() shouldBe null
    }

    @Test
    fun `the failure message travels with the error for the dialog to show`() {
        val activity = activityOf(Activity::class.java)

        val message = GplayServiceUnavailableException(RuntimeException("Play hiccup"))
            .getLocalizedError(activity).fixActionErrorMessage.shouldNotBeNull()

        message shouldBe activity.getString(R.string.upgrades_gplay_not_installed_message)
    }
}
