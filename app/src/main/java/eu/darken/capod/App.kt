package eu.darken.capod

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.LogCatLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.ui.widget.WidgetManager
import eu.darken.capod.main.ui.widget.toWidgetKey
import eu.darken.capod.monitor.core.DeviceMonitor

import eu.darken.capod.monitor.core.devicesWithProfiles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CapodUncaughtExceptionHandler(
                previousHandler = oldHandler,
                cancelBeforeDelegate = { throwable ->
                    // Best-effort shutdown: the system handler may terminate the process immediately,
                    // but cancellation can still close sockets if it gets a scheduling window.
                    if (::appScope.isInitialized) {
                        appScope.cancel(CancellationException("Uncaught exception", throwable))
                    }
                },
            )
        )

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
    }
}
