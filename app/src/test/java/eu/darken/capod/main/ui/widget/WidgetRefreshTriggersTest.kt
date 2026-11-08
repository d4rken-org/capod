package eu.darken.capod.main.ui.widget

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Subscribing to the device flow keeps BLE scanning alive. The widget refresher used to hold that
 * subscription for the whole process lifetime, scanning in the background after monitoring stopped.
 */
class WidgetRefreshTriggersTest : BaseTest() {

    private val devices = MutableSharedFlow<List<PodDevice>>(replay = 1)
    private val estimates = MutableStateFlow<Map<String, BatteryEstimate>>(emptyMap())

    @Test
    fun `inactive gate does not subscribe to devices`() = runTest {
        val job = widgetRefreshTriggers(
            isActive = MutableStateFlow(false),
            devices = devices,
            estimates = estimates,
        ).launchIn(this)
        runCurrent()

        devices.subscriptionCount.value shouldBe 0

        job.cancel()
    }

    @Test
    fun `gate controls the devices subscription`() = runTest {
        val isActive = MutableStateFlow(false)
        var triggers = 0
        val job = widgetRefreshTriggers(
            isActive = isActive,
            devices = devices,
            estimates = estimates,
        )
            .onEach { triggers++ }
            .launchIn(this)
        runCurrent()
        devices.subscriptionCount.value shouldBe 0

        isActive.value = true
        runCurrent()
        devices.subscriptionCount.value shouldBe 1

        devices.emit(emptyList())
        runCurrent()
        triggers shouldBe 1

        isActive.value = false
        runCurrent()
        devices.subscriptionCount.value shouldBe 0

        job.cancel()
    }
}
