package eu.darken.capod.monitor.ui

import android.app.Application
import android.app.Notification
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class MonitorNotificationsTest {

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
}
