package eu.darken.capod.common.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.debug.logging.Logging
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Screen-off is one of the two explanations for a BLE reception blackout, and a debug capture
 * carries no evidence for it unless the state is written into the log while the recording runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PowerStateLoggerTest : BaseTest() {

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val powerManager: PowerManager get() = context.getSystemService(PowerManager::class.java)

    @Before
    fun setup() {
        Bugs.isDebug.value = false
        Logging.install(logCapture)
    }

    @After
    fun teardown() {
        Logging.remove(logCapture)
        Bugs.isDebug.value = false
    }

    private fun powerLines() = logLines.filter { it.startsWith("Power state (") }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a starting recording logs the current power state`() = runTest {
        shadowOf(powerManager).setIsInteractive(true)
        shadowOf(powerManager).setIgnoringBatteryOptimizations(context.packageName, true)

        PowerStateLogger(context, backgroundScope).setup()
        runCurrent()

        Bugs.isDebug.value = true
        runCurrent()

        powerLines() shouldHaveSize 1
        powerLines().single() shouldBe "Power state (recording started): interactive=true, deviceIdle=false, " +
            "powerSave=false, ignoringBatteryOptimizations=true"
    }

    @Test
    fun `a screen-off is logged with the state that came with it`() = runTest {
        PowerStateLogger(context, backgroundScope).setup()
        runCurrent()
        Bugs.isDebug.value = true
        runCurrent()

        shadowOf(powerManager).setIsInteractive(false)
        broadcast(Intent.ACTION_SCREEN_OFF)

        powerLines() shouldHaveSize 2
        powerLines().last() shouldContain "Power state (${Intent.ACTION_SCREEN_OFF})"
        powerLines().last() shouldContain "interactive=false"
    }

    @Test
    fun `a stopped recording stops the logging`() = runTest {
        PowerStateLogger(context, backgroundScope).setup()
        runCurrent()
        Bugs.isDebug.value = true
        runCurrent()

        Bugs.isDebug.value = false
        runCurrent()

        broadcast(Intent.ACTION_SCREEN_OFF)

        powerLines() shouldHaveSize 1
    }

    /**
     * onReceive runs as an Android callback outside any flow, so a PowerManager that throws would
     * take the process down instead of costing a log line.
     */
    @Test
    fun `an unreadable power manager degrades the line instead of the process`() = runTest {
        val throwing = mockk<PowerManager>().apply {
            every { isInteractive } throws RuntimeException("vendor power manager")
            every { isDeviceIdleMode } throws RuntimeException("vendor power manager")
            every { isPowerSaveMode } throws RuntimeException("vendor power manager")
            every { isIgnoringBatteryOptimizations(any()) } throws RuntimeException("vendor power manager")
        }

        PowerStateLogger(ServiceOverrideContext(context, throwing), backgroundScope).setup()
        runCurrent()
        Bugs.isDebug.value = true
        runCurrent()

        broadcast(Intent.ACTION_SCREEN_OFF)

        powerLines() shouldHaveSize 2
        powerLines().last() shouldBe "Power state (${Intent.ACTION_SCREEN_OFF}): interactive=unavailable, " +
            "deviceIdle=unavailable, powerSave=unavailable, ignoringBatteryOptimizations=unavailable"
    }

    /**
     * A failure inside one recording must not end the collection of the recording flag itself,
     * otherwise every later recording in the process silently carries no power state at all.
     */
    @Test
    fun `a failed episode does not stop later recordings from logging`() = runTest {
        val flaky = FailFirstRegistrationContext(context)

        PowerStateLogger(flaky, backgroundScope).setup()
        runCurrent()

        Bugs.isDebug.value = true
        runCurrent()

        powerLines() shouldHaveSize 0
        logLines.any { it.startsWith("Power state logging failed") } shouldBe true

        Bugs.isDebug.value = false
        runCurrent()
        Bugs.isDebug.value = true
        runCurrent()

        powerLines() shouldHaveSize 1
        powerLines().single() shouldContain "Power state (recording started)"
    }

    private class ServiceOverrideContext(
        base: Context,
        private val powerManager: PowerManager,
    ) : ContextWrapper(base) {
        override fun getSystemService(name: String): Any? = when (name) {
            Context.POWER_SERVICE -> powerManager
            else -> super.getSystemService(name)
        }
    }

    private class FailFirstRegistrationContext(base: Context) : ContextWrapper(base) {
        private var failNext = true

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            if (failNext) {
                failNext = false
                throw SecurityException("receiver registration denied")
            }
            return super.registerReceiver(receiver, filter)
        }
    }
}
