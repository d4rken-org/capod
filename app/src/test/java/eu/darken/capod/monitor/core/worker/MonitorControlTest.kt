package eu.darken.capod.monitor.core.worker

import android.Manifest
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.startServiceCompat
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A start rejection must never escape, but it also must not disappear into a message-only log line
 * — the stack tells us which caller tried to start the service from the background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class MonitorControlTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    private val logLines = mutableListOf<Triple<Logging.Priority, String, String>>()

    private val testLogger = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(Triple(priority, tag, message))
        }
    }

    @Before
    fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH)
        logLines.clear()
        Logging.install(testLogger)
        mockkStatic("eu.darken.capod.common.ContextExtensionsKt")
    }

    @After
    fun teardown() {
        Logging.remove(testLogger)
        unmockkAll()
    }

    private fun warnings(): List<String> = logLines
        .filter { it.first == Logging.Priority.WARN }
        .map { it.third }

    @Test
    fun `background start rejections are logged as such`() {
        val failure = ForegroundServiceStartNotAllowedException("not allowed from background")
        every { any<Context>().startServiceCompat(any()) } throws failure

        MonitorControl(context).startMonitor()

        val warning = warnings().single { it.contains("Start rejected") }
        warning shouldContain ForegroundServiceStartNotAllowedException::class.java.name
    }

    @Test
    fun `other illegal states are logged generically`() {
        val failure = IllegalStateException("something else")
        every { any<Context>().startServiceCompat(any()) } throws failure

        MonitorControl(context).startMonitor()

        val warning = warnings().single { it.contains("Failed to start monitor service") }
        warning shouldContain IllegalStateException::class.java.name
        warning shouldNotContain "Start rejected"
    }

    @Test
    fun `security exceptions are logged with their stack`() {
        val failure = SecurityException("missing permission")
        every { any<Context>().startServiceCompat(any()) } throws failure

        MonitorControl(context).startMonitor()

        val warning = warnings().single { it.contains("Failed to start monitor service") }
        warning shouldContain SecurityException::class.java.name
    }
}
