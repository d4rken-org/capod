package eu.darken.capod

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.PowerStateLogger
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
import eu.darken.capod.monitor.core.battery.BatteryEstimator
import eu.darken.capod.monitor.core.battery.displayKey
import eu.darken.capod.monitor.core.battery.estimateFor

import eu.darken.capod.monitor.core.devicesWithProfiles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoReporting: AutomaticBugReporter
    @Inject lateinit var powerStateLogger: PowerStateLogger
    @Inject lateinit var deviceMonitor: DeviceMonitor
    @Inject lateinit var widgetManager: WidgetManager
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var batteryEstimator: BatteryEstimator
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        // Must stay ABOVE super.onCreate(): that call performs the Hilt injection which constructs
        // our singletons. Anything they log while being created is dropped if no logger is installed
        // yet. Do not "tidy" this line back below super.onCreate().
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())
        super.onCreate()

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
        powerStateLogger.setup()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }

        appScope.launch { widgetManager.refreshWidgets() }

        combine(
            deviceMonitor.devicesWithProfiles(),
            batteryEstimator.estimates,
        ) { devices, estimates -> devices to estimates }
            // Estimate display values can change while the device key is stable (e.g. a charge
            // ETA gets suppressed after a stall) — refresh on those too, but only on visible ones.
            .distinctUntilChangedBy { (devices, estimates) ->
                devices.map { device -> device.toWidgetKey() to estimates.estimateFor(device)?.displayKey(device) }
            }
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

    // WorkManager 2.7.1 (see Dependencies.addWorkerManager) still declares Configuration.Provider
    // as getWorkManagerConfiguration(); the `workManagerConfiguration` property form only exists
    // from 2.9.0 onwards.
    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(
            when {
                BuildConfigWrap.DEBUG -> android.util.Log.VERBOSE
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV -> android.util.Log.DEBUG
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.BETA -> android.util.Log.INFO
                BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE -> android.util.Log.WARN
                else -> android.util.Log.VERBOSE
            }
        )
        .setWorkerFactory(workerFactory)
        .build()

    companion object {
        internal val TAG = logTag("CAP")
    }
}
