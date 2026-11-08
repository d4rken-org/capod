package eu.darken.capod.monitor.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class MonitorNotificationsTest {

    private fun earbuds(
        left: Float,
        right: Float,
        leftCharging: Boolean? = null,
        rightCharging: Boolean? = null,
    ): PodDevice = mockk {
        every { hasDualPods } returns true
        every { batteryLeft } returns left
        every { batteryRight } returns right
        every { isLeftPodCharging } returns leftCharging
        every { isRightPodCharging } returns rightCharging
    }

    @Test
    fun `early notification is user presentable`() {
        // It can be the notification the user sees for the whole lifetime of a service that never
        // finishes starting up, so it has to stand on its own.
        val notification = MonitorNotifications.createEarlyNotification(RuntimeEnvironment.getApplication())

        notification.extras.getString(Notification.EXTRA_TITLE).isNullOrBlank() shouldBe false
        notification.extras.getString(Notification.EXTRA_TEXT).isNullOrBlank() shouldBe false
        notification.smallIcon shouldNotBe null
        notification.contentIntent shouldNotBe null
    }

    @Test
    fun `header name prefers the profile label`() {
        val device = mockk<PodDevice> {
            every { label } returns "My AirPods"
            every { getLabel(any()) } returns "AirPods Pro"
        }

        device.notificationHeaderName(RuntimeEnvironment.getApplication()) shouldBe "My AirPods"
    }

    @Test
    fun `header name falls back to the model label`() {
        val device = mockk<PodDevice> {
            every { label } returns null
            every { getLabel(any()) } returns "AirPods Pro"
        }

        device.notificationHeaderName(RuntimeEnvironment.getApplication()) shouldBe "AirPods Pro"
    }

    @Test
    fun `device notification shows the device name only in the private header`() {
        val context = RuntimeEnvironment.getApplication()
        val notifications = MonitorNotifications(
            context,
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
            MonitorNotificationViewFactory(context),
        )
        val device = mockk<PodDevice>(relaxed = true) {
            every { hasDualPods } returns true
            every { hasCase } returns false
            every { model } returns PodModel.AIRPODS_PRO
            every { label } returns "My AirPods"
            every { getLabel(any()) } returns "AirPods Pro"
        }

        val notification = notifications.getNotification(device)

        notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() shouldBe "My AirPods"
        notification.publicVersion.extras.getCharSequence(Notification.EXTRA_SUB_TEXT) shouldBe null
    }

    @Test
    fun `lock screen version shows the battery as plain text without custom views`() {
        val context = RuntimeEnvironment.getApplication()
        val notifications = MonitorNotifications(
            context,
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
            MonitorNotificationViewFactory(context),
        )
        val device = mockk<PodDevice>(relaxed = true) {
            every { hasDualPods } returns true
            every { hasCase } returns false
            every { model } returns PodModel.AIRPODS_PRO
            every { getLabel(any()) } returns "AirPods Pro"
        }

        val notification = notifications.getNotification(device)

        notification.contentView shouldNotBe null
        notification.publicVersion.contentView shouldBe null
        notification.publicVersion.bigContentView shouldBe null
        notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() shouldBe false
    }

    @Test
    fun `status bar battery of earbuds ignores the headset level`() {
        earbuds(left = 0.8f, right = 0.35f).statusBarBatteryPercent() shouldBe 35
    }

    @Test
    fun `status bar battery skips the pod that is charging`() {
        earbuds(left = 0.2f, right = 0.6f, leftCharging = true).statusBarBatteryPercent() shouldBe 60
        earbuds(left = 0.6f, right = 0.2f, rightCharging = true).statusBarBatteryPercent() shouldBe 60
    }

    @Test
    fun `status bar battery picks the lower pod when both are charging`() {
        earbuds(left = 0.2f, right = 0.6f, leftCharging = true, rightCharging = true)
            .statusBarBatteryPercent() shouldBe 20
    }

    @Test
    fun `status bar battery falls back to the charging pod when the other is unknown`() {
        earbuds(left = 0.2f, right = -1f, leftCharging = true).statusBarBatteryPercent() shouldBe 20
    }

    @Test
    fun `status bar battery of a headset ignores the pod levels`() {
        val device = mockk<PodDevice> {
            every { hasDualPods } returns false
            every { batteryHeadset } returns 0.72f
        }

        device.statusBarBatteryPercent() shouldBe 72
    }

    @Test
    fun `status bar battery picks the lower known pod`() {
        lowestKnownPercent(0.8f, 0.35f) shouldBe 35
        lowestKnownPercent(-1f, 0.6f) shouldBe 60
        lowestKnownPercent(1f) shouldBe 100
    }

    @Test
    fun `status bar battery is absent without a known level`() {
        lowestKnownPercent(-1f, -1f) shouldBe null
        lowestKnownPercent(Float.NaN) shouldBe null
    }
}
