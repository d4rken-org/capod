package eu.darken.capod.common

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import eu.darken.capod.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VoiceAssistantLauncherTest {

    private val application: Application get() = RuntimeEnvironment.getApplication()

    private class RecordingContext(base: Application) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        var failure: RuntimeException? = null

        override fun startActivity(intent: Intent) {
            started.add(intent)
            failure?.let { throw it }
        }
    }

    @Test
    fun `launches the voice command activity in a new task`() {
        val context = RecordingContext(application)

        VoiceAssistantLauncher(context).launch()

        val intent = context.started.single()
        intent.action shouldBe Intent.ACTION_VOICE_COMMAND
        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
    }

    @Test
    fun `a missing voice assistant is caught and reported with a toast`() {
        val context = RecordingContext(application).apply { failure = ActivityNotFoundException("none") }

        VoiceAssistantLauncher(context).launch()
        shadowOf(Looper.getMainLooper()).idle()

        context.started.size shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe application.getString(R.string.press_action_voice_assistant_unavailable)
    }

    @Test
    fun `a denied launch is caught without a toast`() {
        val context = RecordingContext(application).apply { failure = SecurityException("denied") }

        VoiceAssistantLauncher(context).launch()
        shadowOf(Looper.getMainLooper()).idle()

        context.started.size shouldBe 1
        ShadowToast.shownToastCount() shouldBe 0
    }

    @Test
    fun `a failed launch does not prevent later launches`() {
        val context = RecordingContext(application).apply { failure = ActivityNotFoundException("none") }
        val launcher = VoiceAssistantLauncher(context)

        launcher.launch()
        context.failure = null
        launcher.launch()

        context.started.size shouldBe 2
        context.started.last().action shouldBe Intent.ACTION_VOICE_COMMAND
    }
}
