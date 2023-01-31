package eu.darken.capod

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.*
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.ui.widget.WidgetManager
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.worker.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoReporting: AutomaticBugReporter
    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var podMonitor: PodMonitor
    @Inject lateinit var widgetManager: WidgetManager
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        autoReporting.setup(this)

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }

        appScope.launch {
            monitorControl.startMonitor(forceStart = true)
        }

        podMonitor.mainDevice
            .distinctUntilChanged()
            .throttleLatest(1000)
            .onEach {
                log(TAG) { "Main device changed, refreshing widgets." }
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

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.VERBOSE)
        .setWorkerFactory(workerFactory)
        .build()

    companion object {
        internal val TAG = logTag("CAP")
    }
}
