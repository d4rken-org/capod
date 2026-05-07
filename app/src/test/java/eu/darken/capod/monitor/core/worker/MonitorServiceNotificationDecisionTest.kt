package eu.darken.capod.monitor.core.worker

import eu.darken.capod.monitor.core.PodDevice
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MonitorServiceNotificationDecisionTest : BaseTest() {

    private fun device(isLive: Boolean): PodDevice = mockk {
        every { this@mockk.isLive } returns isLive
    }

    private fun settings(useExtra: Boolean, keepAfter: Boolean) = NotificationSettings(
        useExtraNotification = useExtra,
        keepAfterDisconnect = keepAfter,
    )

    @Test
    fun `extra off, null device - cancel`() {
        decideExtraNotificationAction(null, settings(useExtra = false, keepAfter = false)) shouldBe
            ExtraNotificationAction.Cancel
    }

    @Test
    fun `extra off, live device - cancel`() {
        decideExtraNotificationAction(device(isLive = true), settings(useExtra = false, keepAfter = false)) shouldBe
            ExtraNotificationAction.Cancel
    }

    @Test
    fun `extra off, cached-only device, keepAfter true - cancel (extra off wins)`() {
        decideExtraNotificationAction(device(isLive = false), settings(useExtra = false, keepAfter = true)) shouldBe
            ExtraNotificationAction.Cancel
    }

    @Test
    fun `extra on, live device, keepAfter false - post`() {
        val live = device(isLive = true)
        decideExtraNotificationAction(live, settings(useExtra = true, keepAfter = false)) shouldBe
            ExtraNotificationAction.Post(live)
    }

    @Test
    fun `extra on, live device, keepAfter true - post`() {
        val live = device(isLive = true)
        decideExtraNotificationAction(live, settings(useExtra = true, keepAfter = true)) shouldBe
            ExtraNotificationAction.Post(live)
    }

    @Test
    fun `extra on, cached-only device, keepAfter false - cancel`() {
        decideExtraNotificationAction(device(isLive = false), settings(useExtra = true, keepAfter = false)) shouldBe
            ExtraNotificationAction.Cancel
    }

    @Test
    fun `extra on, cached-only device, keepAfter true - keep existing`() {
        decideExtraNotificationAction(device(isLive = false), settings(useExtra = true, keepAfter = true)) shouldBe
            ExtraNotificationAction.KeepExisting
    }

    @Test
    fun `extra on, null device, keepAfter false - cancel`() {
        decideExtraNotificationAction(null, settings(useExtra = true, keepAfter = false)) shouldBe
            ExtraNotificationAction.Cancel
    }

    @Test
    fun `extra on, null device, keepAfter true - keep existing`() {
        decideExtraNotificationAction(null, settings(useExtra = true, keepAfter = true)) shouldBe
            ExtraNotificationAction.KeepExisting
    }
}
