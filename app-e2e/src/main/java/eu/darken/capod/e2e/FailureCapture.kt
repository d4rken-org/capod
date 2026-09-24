package eu.darken.capod.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/** A failed step leaves the screen and its accessibility tree behind for diagnosis. */
class FailureCapture(private val device: UiDevice) : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        device.takeScreenshot(File(dir, "${description.methodName}.png"))
        device.dumpWindowHierarchy(File(dir, "${description.methodName}.xml"))
    }
}
