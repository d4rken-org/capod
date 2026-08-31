package eu.darken.capod.monitor.core.worker

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import androidx.core.app.NotificationCompat
import eu.darken.capod.monitor.ui.MonitorNotifications
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
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

    private fun MonitorService.getField(name: String): Any? =
        MonitorService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)

    private fun MonitorService.startSignal(): Long = (getField("startSignal") as StateFlow<*>).value as Long

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

    /**
     * A built Notification's `when` is frozen, so re-promoting the previous session's last frame to
     * open a new one re-posts stale content under its original timestamp — the way an "unknown
     * device" placeholder survives a teardown/restart cycle.
     */
    @Test
    fun `a start with no live monitor does not reuse the previous notification`() {
        val service = createService()
        service.readyForMonitoring()

        val stale = notification("stale")
        service.postPrimaryNotification(stale)

        // Monitor session has ended. Production never nulls monitoringJob, it leaves the completed
        // job in place, so a finished Job is the faithful state here.
        service.setField("monitoringJob", Job().apply { complete() })

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY

        shadowOf(service).lastForegroundNotification shouldNotBeSameInstanceAs stale
        service.getField("lastNotification") shouldNotBeSameInstanceAs stale
    }

    /**
     * Invalidation is deliberately at session launch, not next to the promote: a collector on
     * Dispatchers.Default can post between the read and a write there, so clearing at promote time
     * could discard a frame that is genuinely current.
     */
    @Test
    fun `launching a new session drops the previous session's notification`() {
        val service = createService()
        service.readyForMonitoring()
        service.postPrimaryNotification(notification("previous"))

        // forceStart bypasses the "already monitoring" early return, so a new session is launched.
        service.onStartCommand(MonitorService.intent(context, forceStart = true), 0, 1) shouldBe
            Service.START_STICKY

        service.getField("lastNotification").shouldBeNull()
    }

    /**
     * The FGS notification can outlive `stopSelf()`. Leaving it up strands whatever content was last
     * posted — including the unknown-device placeholder built before the first BLE scan batch landed.
     *
     * Asserted as an interaction, not as shadow end-state: Robolectric's `ShadowService.onDestroy()`
     * tears the foreground notification down by itself, so an end-state check passes either way.
     */
    @Test
    fun `onDestroy retracts the monitor notification`() {
        val service = createService()
        service.readyForMonitoring()
        val manager = mockk<NotificationManager>(relaxed = true)
        service.notificationManager = manager

        service.onDestroy()

        verify { manager.cancel(MonitorNotifications.NOTIFICATION_ID) }
    }

    /**
     * Scope cancellation doesn't await the collectors, so one already past its suspension point can
     * still post — re-creating the very notification onDestroy just took down.
     */
    @Test
    fun `a post that lands after onDestroy is dropped`() {
        val service = createService()
        service.readyForMonitoring()
        service.onDestroy()

        val manager = mockk<NotificationManager>(relaxed = true)
        service.notificationManager = manager

        service.postPrimaryNotification(notification("late"))

        verify(exactly = 0) { manager.notify(any<Int>(), any()) }
        service.getField("lastNotification").shouldBeNull()
    }

    /**
     * A start request that finds a live session is acknowledged without touching it — including a
     * teardown countdown that is already running. Bumping the signal is what re-arms that window.
     */
    @Test
    fun `a short-circuited start bumps the start signal`() {
        val service = createService()
        service.readyForMonitoring()

        val before = service.startSignal()

        service.onStartCommand(MonitorService.intent(context), 0, 1) shouldBe Service.START_STICKY

        service.startSignal() shouldBe before + 1
    }

    @Test
    fun `onDestroy skips notification cleanup when injection never completed`() {
        val service = createService()
        service.setField("injectionComplete", false)
        val manager = mockk<NotificationManager>(relaxed = true)
        service.notificationManager = manager

        service.onDestroy()

        verify(exactly = 0) { manager.cancel(any<Int>()) }
    }
}
