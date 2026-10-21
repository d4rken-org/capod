package eu.darken.capod.main.core

import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PermissionToolTest : BaseTest() {

    private fun profile(
        popUpOnCaseOpen: Boolean = false,
        stemActions: StemActionsConfig = StemActionsConfig(),
    ) = AppleDeviceProfile(
        label = "Test",
        showPopUpOnCaseOpen = popUpOnCaseOpen,
        stemActions = stemActions,
    )

    @Test
    fun `IGNORE_BATTERY_OPTIMIZATION is applicable only in ALWAYS mode`() {
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.ALWAYS,
            overlayNeeded = false,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.AUTOMATIC,
            overlayNeeded = false,
        ) shouldBe false
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.MANUAL,
            overlayNeeded = false,
        ) shouldBe false
    }

    @Test
    fun `ACCESS_BACKGROUND_LOCATION is applicable only in ALWAYS mode`() {
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.ALWAYS,
            overlayNeeded = false,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.AUTOMATIC,
            overlayNeeded = false,
        ) shouldBe false
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.MANUAL,
            overlayNeeded = false,
        ) shouldBe false
    }

    @Test
    fun `SYSTEM_ALERT_WINDOW is applicable only when an overlay is needed`() {
        PermissionTool.isApplicable(
            Permission.SYSTEM_ALERT_WINDOW,
            MonitorMode.AUTOMATIC,
            overlayNeeded = true,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.SYSTEM_ALERT_WINDOW,
            MonitorMode.ALWAYS,
            overlayNeeded = false,
        ) shouldBe false
    }

    @Test
    fun `popup permission gating is independent of monitor mode`() {
        // SYSTEM_ALERT_WINDOW gating depends on overlay need, not on mode.
        for (mode in MonitorMode.entries) {
            PermissionTool.isApplicable(
                Permission.SYSTEM_ALERT_WINDOW,
                mode,
                overlayNeeded = true,
            ) shouldBe true
        }
    }

    @Test
    fun `mode-gated permissions don't depend on popup state`() {
        // IGNORE_BATTERY_OPTIMIZATION and ACCESS_BACKGROUND_LOCATION gate on mode only.
        listOf(Permission.IGNORE_BATTERY_OPTIMIZATION, Permission.ACCESS_BACKGROUND_LOCATION).forEach { perm ->
            PermissionTool.isApplicable(perm, MonitorMode.ALWAYS, overlayNeeded = true) shouldBe true
            PermissionTool.isApplicable(perm, MonitorMode.ALWAYS, overlayNeeded = false) shouldBe true
            PermissionTool.isApplicable(perm, MonitorMode.AUTOMATIC, overlayNeeded = true) shouldBe false
            PermissionTool.isApplicable(perm, MonitorMode.AUTOMATIC, overlayNeeded = false) shouldBe false
        }
    }

    @Test
    fun `unconditional permissions are always applicable`() {
        // BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS, etc.
        // are not mode/popup-gated.
        val unconditional = Permission.entries.filter {
            it != Permission.IGNORE_BATTERY_OPTIMIZATION &&
                it != Permission.ACCESS_BACKGROUND_LOCATION &&
                it != Permission.SYSTEM_ALERT_WINDOW
        }
        for (perm in unconditional) {
            for (mode in MonitorMode.entries) {
                for (popups in listOf(true, false)) {
                    PermissionTool.isApplicable(perm, mode, popups) shouldBe true
                }
            }
        }
    }

    @Test
    fun `overlay is needed when a popup is enabled`() {
        PermissionTool.needsOverlay(listOf(profile(popUpOnCaseOpen = true))) shouldBe true
        PermissionTool.needsOverlay(listOf(AppleDeviceProfile(label = "Test", showPopUpOnConnection = true))) shouldBe true
    }

    @Test
    fun `overlay is needed when a press is mapped to the voice assistant`() {
        PermissionTool.needsOverlay(
            listOf(profile(stemActions = StemActionsConfig(leftDouble = StemAction.VoiceAssistant)))
        ) shouldBe true
    }

    @Test
    fun `overlay is not needed without popups or voice assistant`() {
        PermissionTool.needsOverlay(
            listOf(profile(stemActions = StemActionsConfig(leftSingle = StemAction.PlayPause)))
        ) shouldBe false
    }

    @Test
    fun `overlay is needed if any profile maps the voice assistant`() {
        PermissionTool.needsOverlay(
            listOf(
                profile(stemActions = StemActionsConfig(leftSingle = StemAction.PlayPause)),
                profile(stemActions = StemActionsConfig(rightLong = StemAction.VoiceAssistant)),
            )
        ) shouldBe true
    }

    @Test
    fun `overlay is not needed without profiles`() {
        PermissionTool.needsOverlay(emptyList()) shouldBe false
    }
}
