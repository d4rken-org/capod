package eu.darken.capod

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.HiltAndroidApp
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.autoreport.AutoReporting
import eu.darken.capod.common.debug.logging.*
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.wear.core.MonitorWorker
import kotlinx.coroutines.CoroutineScope
import java.time.Duration
import javax.inject.Inject

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoReporting: AutoReporting
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var workManager: WorkManager
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Logging.install(LogCatLogger())

        ReLinker
            .log { message -> log(TAG) { "ReLinker: $message" } }
            .loadLibrary(this, "bugsnag-plugin-android-anr")

        generalSettings.apply {
            scannerMode.value = ScannerMode.BALANCED
        }

        autoReporting.setup()

        setupWorker()

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    private fun setupWorker() {
        log(TAG) { "setupWorker()" }
        val workRequest = PeriodicWorkRequestBuilder<MonitorWorker>(
            Duration.ofMinutes(15),
            Duration.ofMinutes(5)
        ).apply {
            setInputData(Data.Builder().build())
        }.build()

        log(TAG, VERBOSE) { "Worker request: $workRequest" }

        val operation = workManager.enqueueUniquePeriodicWork(
            "${BuildConfigWrap.APPLICATION_ID}.monitor.worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )

        operation.result.get()
        log(TAG) { "Monitor start request send." }
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.VERBOSE)
        .setWorkerFactory(workerFactory)
        .build()

    companion object {
        internal val TAG = logTag("CAP")
    }
}
