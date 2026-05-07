package eu.darken.capod.main.core

import eu.darken.capod.common.permissions.Permission
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PermissionToolTest : BaseTest() {

    @Test
    fun `IGNORE_BATTERY_OPTIMIZATION is applicable only in ALWAYS mode`() {
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.ALWAYS,
            anyPopupEnabled = false,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.AUTOMATIC,
            anyPopupEnabled = false,
        ) shouldBe false
        PermissionTool.isApplicable(
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            MonitorMode.MANUAL,
            anyPopupEnabled = false,
        ) shouldBe false
    }

    @Test
    fun `ACCESS_BACKGROUND_LOCATION is applicable only in ALWAYS mode`() {
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.ALWAYS,
            anyPopupEnabled = false,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.AUTOMATIC,
            anyPopupEnabled = false,
        ) shouldBe false
        PermissionTool.isApplicable(
            Permission.ACCESS_BACKGROUND_LOCATION,
            MonitorMode.MANUAL,
            anyPopupEnabled = false,
        ) shouldBe false
    }

    @Test
    fun `SYSTEM_ALERT_WINDOW is applicable only when popups are enabled`() {
        PermissionTool.isApplicable(
            Permission.SYSTEM_ALERT_WINDOW,
            MonitorMode.AUTOMATIC,
            anyPopupEnabled = true,
        ) shouldBe true
        PermissionTool.isApplicable(
            Permission.SYSTEM_ALERT_WINDOW,
            MonitorMode.ALWAYS,
            anyPopupEnabled = false,
        ) shouldBe false
    }

    @Test
    fun `popup permission gating is independent of monitor mode`() {
        // SYSTEM_ALERT_WINDOW gating depends on popups, not on mode.
        for (mode in MonitorMode.entries) {
            PermissionTool.isApplicable(
                Permission.SYSTEM_ALERT_WINDOW,
                mode,
                anyPopupEnabled = true,
            ) shouldBe true
        }
    }

    @Test
    fun `mode-gated permissions don't depend on popup state`() {
        // IGNORE_BATTERY_OPTIMIZATION and ACCESS_BACKGROUND_LOCATION gate on mode only.
        listOf(Permission.IGNORE_BATTERY_OPTIMIZATION, Permission.ACCESS_BACKGROUND_LOCATION).forEach { perm ->
            PermissionTool.isApplicable(perm, MonitorMode.ALWAYS, anyPopupEnabled = true) shouldBe true
            PermissionTool.isApplicable(perm, MonitorMode.ALWAYS, anyPopupEnabled = false) shouldBe true
            PermissionTool.isApplicable(perm, MonitorMode.AUTOMATIC, anyPopupEnabled = true) shouldBe false
            PermissionTool.isApplicable(perm, MonitorMode.AUTOMATIC, anyPopupEnabled = false) shouldBe false
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
}
