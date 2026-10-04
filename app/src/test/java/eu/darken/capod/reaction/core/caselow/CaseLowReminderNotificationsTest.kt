package eu.darken.capod.reaction.core.caselow

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import eu.darken.capod.main.ui.MainActivity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CaseLowReminderNotificationsTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var notificationManager: NotificationManager
    private lateinit var notifications: CaseLowReminderNotifications

    @Before
    fun setup() {
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notifications = CaseLowReminderNotifications(context, notificationManager)
    }

    private fun posted(profileId: String): Notification? =
        shadowOf(notificationManager).getNotification("caselow:$profileId", 5)

    @Test
    fun `show posts a tagged notification`() {
        notifications.show("p1", "My AirPods", 15)

        val notification = posted("p1").shouldNotBeNull()
        notification.extras.getString(Notification.EXTRA_TITLE).isNullOrBlank() shouldBe false
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("My AirPods") shouldBe true
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("15") shouldBe true
    }

    @Test
    fun `profiles coexist and cancel independently`() {
        notifications.show("p1", "One", 15)
        notifications.show("p2", "Two", 10)

        notifications.cancel("p1")

        posted("p1").shouldBeNull()
        posted("p2").shouldNotBeNull()
    }

    @Test
    fun `cancelAllExcept only removes unlisted case low reminders`() {
        notifications.show("p1", "One", 15)
        notifications.show("p2", "Two", 10)
        notifications.show("p3", "Three", 5)
        val foreign = NotificationCompat.Builder(context, "other")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        notificationManager.notify("charged:p1", 4, foreign)
        notificationManager.notify("caselow:p1", 4, foreign)

        notifications.cancelAllExcept(setOf("p2"))

        posted("p1").shouldBeNull()
        posted("p2").shouldNotBeNull()
        posted("p3").shouldBeNull()
        shadowOf(notificationManager).getNotification("charged:p1", 4).shouldNotBeNull()
        shadowOf(notificationManager).getNotification("caselow:p1", 4).shouldNotBeNull()
    }

    @Test
    fun `show posts nothing when notifications are disabled`() {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        notifications.show("p1", "One", 15)

        posted("p1").shouldBeNull()
    }

    @Test
    fun `channel has default importance`() {
        val channel = notificationManager.getNotificationChannel(CaseLowReminderNotifications.CHANNEL_ID)
            .shouldNotBeNull()

        channel.importance shouldBe NotificationManager.IMPORTANCE_DEFAULT
    }

    @Test
    fun `content intent opens the main activity`() {
        notifications.show("p1", "One", 15)

        val pendingIntent = posted("p1").shouldNotBeNull().contentIntent.shouldNotBeNull()
        shadowOf(pendingIntent).savedIntent.component shouldBe ComponentName(context, MainActivity::class.java)
    }
}
