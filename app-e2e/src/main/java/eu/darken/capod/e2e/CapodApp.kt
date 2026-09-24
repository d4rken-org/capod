package eu.darken.capod.e2e

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * Drives the installed app the way a user does. Selectors resolve the app's own string resources,
 * so they follow the device locale.
 */
class CapodApp {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val resources = instrumentation.context.packageManager.getResourcesForApplication(PKG)

    fun resetToFirstRunState() {
        val result = device.executeShellCommand("pm clear $PKG").trim()
        if (result != "Success") throw AssertionError("pm clear $PKG failed: $result")
        // A disabled adapter replaces the dashboard's monitoring cards with an enable-Bluetooth prompt.
        device.executeShellCommand("svc bluetooth enable")
    }

    /** Starts the app, or brings its existing task back to the front without restarting it. */
    fun launch() {
        val component = instrumentation.context.packageManager.getLaunchIntentForPackage(PKG)?.component
            ?: throw AssertionError("$PKG has no launcher activity")
        val result = device.executeShellCommand(
            "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                "-n ${component.flattenToShortString()}"
        )
        if (result.contains("Error")) throw AssertionError("Launching $PKG failed: $result")
    }

    fun forceStop() {
        device.executeShellCommand("am force-stop $PKG")
        awaitGone(By.pkg(PKG))
    }

    fun completeOnboarding() {
        click(text("general_continue_action"))
        awaitOverview()
    }

    fun awaitOverview(): UiObject2 = await(desc("settings_devices_label"))

    /** Grants every permission the dashboard asks for through its own cards and the system dialog. */
    fun grantDashboardPermissions() {
        val grant = text("general_grant_permission_action")
        repeat(MAX_PERMISSION_REQUESTS) {
            // Cards recompose after each answer, and a permission whose group was just granted
            // disappears without a dialog.
            if (!device.wait(Until.hasObject(grant), PERMISSION_SETTLE_MS)) return
            click(grant)
            device.wait(Until.findObject(PERMISSION_ALLOW), PERMISSION_SETTLE_MS)?.click()
            awaitGone(PERMISSION_ALLOW)
        }
        if (device.hasObject(grant)) {
            throw AssertionError("Permission cards still shown after $MAX_PERMISSION_REQUESTS requests")
        }
    }

    fun openSettings() = click(desc("settings_general_label"))

    fun text(name: String): BySelector = By.text(string(name))

    /** Matches a format string resource with any argument, e.g. "Supporter since %s". */
    fun formattedText(name: String): BySelector = By.text(
        Pattern.compile(string(name).split(FORMAT_ARG).joinToString(".*") { Pattern.quote(it) }, Pattern.DOTALL)
    )

    fun desc(name: String): BySelector = By.desc(string(name))

    fun await(selector: BySelector, timeoutMs: Long = TIMEOUT_MS): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs)
            ?: throw AssertionError("Timed out after ${timeoutMs}ms waiting for $selector")

    fun awaitGone(selector: BySelector) {
        if (!device.wait(Until.gone(selector), TIMEOUT_MS)) {
            throw AssertionError("Timed out after ${TIMEOUT_MS}ms waiting for $selector to disappear")
        }
    }

    fun assertAbsent(selector: BySelector) {
        if (device.hasObject(selector)) throw AssertionError("Unexpectedly found $selector")
    }

    /** Scrolls down until [selector] is on screen, waiting for content that is still loading. */
    fun scrollTo(selector: BySelector, timeoutMs: Long = TIMEOUT_MS): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            // Waiting first keeps a screen that is still sliding in from being swiped past its target.
            device.wait(Until.findObject(selector), SCROLL_POLL_MS)?.let { return it }
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms scrolling to $selector")
            }
            // API 30 marks no Compose list as scrollable, so swipe the screen instead of the list.
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 2 / 3, x, device.displayHeight / 3, SWIPE_STEPS)
        }
    }

    fun click(selector: BySelector) = click(await(selector))

    // Screens slide in, and a click at a moving node lands where it was a frame earlier.
    fun click(node: UiObject2) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var bounds = node.visibleBounds
        while (true) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("$node never stopped moving")
            SystemClock.sleep(SETTLE_POLL_MS)
            val current = node.visibleBounds
            if (current == bounds && !current.isEmpty) break
            bounds = current
        }
        node.click()
    }

    fun string(name: String): String {
        val id = resources.getIdentifier(name, "string", PKG)
        if (id == 0) throw AssertionError("$PKG has no string resource '$name'")
        return resources.getString(id)
    }

    companion object {
        const val PKG = "eu.darken.capod"
        private const val TIMEOUT_MS = 30_000L
        private const val SETTLE_POLL_MS = 150L
        private const val SCROLL_POLL_MS = 1_000L
        private const val SWIPE_STEPS = 20
        private const val PERMISSION_SETTLE_MS = 5_000L
        private const val MAX_PERMISSION_REQUESTS = 5
        private val FORMAT_ARG = Regex("%(\\d+\\$)?[sd]")

        // Location asks "While using the app" (API 30), everything else plainly "Allow".
        private val PERMISSION_ALLOW = By.res(
            Pattern.compile("com.android.permissioncontroller:id/permission_allow(_foreground_only)?_button")
        )
    }
}
