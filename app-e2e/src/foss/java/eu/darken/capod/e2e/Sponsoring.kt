package eu.darken.capod.e2e

import android.os.SystemClock
import androidx.test.uiautomator.By

fun CapodApp.openUpgradeStatus() {
    openSettings()
    click(scrollTo(text("settings_upgrade_status_description")))
}

/**
 * Becomes a supporter the way a user does: the sponsor page opens in the browser, and coming back
 * after a real visit unlocks. Starts on the free status screen.
 */
fun CapodApp.becomeSupporter() {
    click(text("upgrade_screen_status_free_action"))
    click(scrollTo(text("upgrade_foss_sponsor_action")))
    awaitGone(By.pkg(CapodApp.PKG))
    SystemClock.sleep(SPONSOR_VISIT_MS)
    // Restarting the app instead would lose the pending visit it is waiting to see end.
    launch()
    await(text("upgrade_screen_status_upgraded_title"))
}

// The app only counts a visit that kept it in the background for more than five seconds.
private const val SPONSOR_VISIT_MS = 7_000L
