package eu.darken.capod.monitor.core.worker

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import androidx.core.app.NotificationCompat
import eu.darken.capod.monitor.ui.MonitorNotifications
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every `startForegroundService()` re-arms the `startForeground()` obligation, so each
 * `onStartCommand()` has to satisfy it again — on every exit path.
 *
 * NOTE: `create()` already runs the real `onCreate()` (early promotion + failing Hilt injection),
 * so shadow state is non-trivial before a test acts. Assertions are deltas against the state
 * captured right before the call under test, never absolutes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class MonitorServiceTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun MonitorService.setField(name: String, value: Any?) {
        MonitorService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    private fun notification(title: String): Notification =
        NotificationCompat.Builder(context, MonitorNotifications.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .build()

    private fun createService(): MonitorService {
        val service = Robolectric.buildService(MonitorService::class.java).create().get()
        // Hilt injection fails on a plain Application, so wire what the tests need by hand and
        // reset the flags onCreate() left behind.
        service.notificationManager = context.getSystemService(NotificationManager::class.java)
        service.setField("foregroundStartFailed", false)
        service.setField("injectionComplete", false)
        service.setField("monitoringJob", null)
        service.setField("lastNotification", null)
        return service
    }

    private fun MonitorService.readyForMonitoring() {
        setField("injectionComplete", true)
        setField("monitoringJob", Job())
        setField("foregroundStartFailed", false)
    }

    @Test
    fun `repeated non-force starts promote to foreground every time`() {
        val service = createService()
        service.readyForMonitoring()

        val first = notification("first")
        service.setField("lastNotification", first)

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY
        shadowOf(service).lastForegroundNotification shouldBeSameInstanceAs first
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID

        val second = notification("second")
        service.setField("lastNotification", second)

        service.onStartCommand(MonitorService.intent(context), 0, 2) shouldBe Service.START_STICKY
        shadowOf(service).lastForegroundNotification shouldBeSameInstanceAs second
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
    }

    @Test
    fun `foreground-denied start still satisfies the obligation before stopping`() {
        val service = createService()
        service.setField("foregroundStartFailed", true)
        service.setField("lastNotification", null)
        val beforeCall = shadowOf(service).lastForegroundNotification

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_NOT_STICKY

        shadowOf(service).lastForegroundNotification shouldNotBeSameInstanceAs beforeCall
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
    }

    @Test
    fun `re-promotion falls back to the early notification when nothing was posted`() {
        val service = createService()
        service.readyForMonitoring()
        service.setField("lastNotification", null)
        val beforeCall = shadowOf(service).lastForegroundNotification

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY

        shadowOf(service).lastForegroundNotification shouldNotBeSameInstanceAs beforeCall
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
    }

    @Test
    fun `re-promotion uses the notification posted by the monitor flow`() {
        val service = createService()
        service.readyForMonitoring()

        val dynamic = notification("dynamic")
        service.postPrimaryNotification(dynamic)

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY

        shadowOf(service).lastForegroundNotification shouldBeSameInstanceAs dynamic
        shadowOf(service).lastForegroundNotificationId shouldBe MonitorNotifications.NOTIFICATION_ID
    }
}
