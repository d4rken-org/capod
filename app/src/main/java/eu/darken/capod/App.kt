package eu.darken.capod

import android.app.Application
import android.os.Looper
import dagger.hilt.android.HiltAndroidApp
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.LogCatLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.ui.widget.WidgetManager
import eu.darken.capod.main.ui.widget.toWidgetKey
import eu.darken.capod.monitor.core.DeviceMonitor

import eu.darken.capod.monitor.core.devicesWithProfiles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
open class App : Application() {

    @Inject lateinit var autoReporting: AutomaticBugReporter
    @Inject lateinit var deviceMonitor: DeviceMonitor
    @Inject lateinit var widgetManager: WidgetManager
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        val foregroundExceptionHandled = AtomicBoolean(false)
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isTimingExc = throwable.isForegroundServiceTimingException()
            val isMain = thread === Looper.getMainLooper().thread
            if (isTimingExc && isMain && foregroundExceptionHandled.compareAndSet(false, true)) {
                runCatching {
                    log(TAG, WARN) { "Suppressed foreground service timing exception: ${throwable.asLog()}" }
                    Bugs.report(tag = TAG, "Foreground service timing exception suppressed", exception = throwable)
                }
                Looper.loop()
                return@setDefaultUncaughtExceptionHandler
            }
            runCatching { log(TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" } }
            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable) else exitProcess(1)
        }

        autoReporting.setup(this)

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }

        appScope.launch { widgetManager.refreshWidgets() }

        deviceMonitor.devicesWithProfiles()
            .distinctUntilChangedBy { devices -> devices.map { it.toWidgetKey() } }
            .throttleLatest(1000)
            .onEach {
                log(TAG, VERBOSE) { "Devices changed, refreshing widgets." }
                widgetManager.refreshWidgets()
            }
            .launchIn(appScope)

        upgradeRepo.upgradeInfo
            .map { it.isPro }
            .distinctUntilChanged()
            .onEach {
                log(TAG) { "Pro status changed, refreshing widgets." }
                widgetManager.refreshWidgets()
            }
            .launchIn(appScope)
    }

    companion object {
        internal val TAG = logTag("CAP")

        private fun Throwable.isForegroundServiceTimingException(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                if (current.javaClass.simpleName == "ForegroundServiceDidNotStartInTimeException") return true
                current = current.cause
            }
            return false
        }
    }
}
