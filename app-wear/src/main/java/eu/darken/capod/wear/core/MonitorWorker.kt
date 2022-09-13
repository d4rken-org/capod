package eu.darken.capod.wear.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoints
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.MonitorComponent
import eu.darken.capod.monitor.core.MonitorCoroutineScope
import eu.darken.capod.monitor.core.PodMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    monitorComponentBuilder: MonitorComponent.Builder,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val permissionTool: PermissionTool,
    private val podMonitor: PodMonitor,
    private val bluetoothManager: BluetoothManager2,
) : CoroutineWorker(context, params) {

    private val workerScope = MonitorCoroutineScope()
    private val monitorComponent = monitorComponentBuilder
        .coroutineScope(workerScope)
        .build()

    private val entryPoint by lazy {
        EntryPoints.get(monitorComponent, MonitorWorkerEntryPoint::class.java)
    }

    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        doDoWork()

        val duration = System.currentTimeMillis() - start

        log(TAG, VERBOSE) { "Execution finished after ${duration}ms, $inputData" }

        Result.success(inputData)
    } catch (e: Throwable) {
        if (e !is CancellationException) {
            Bugs.report(tag = TAG, "Execution failed", exception = e)
            finishedWithError = true
            Result.failure(inputData)
        } else {
            Result.success()
        }
    } finally {
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() = withContext(dispatcherProvider.IO) {
        val permissionsMissingOnStart = permissionTool.missingPermissions.first()
        if (permissionsMissingOnStart.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing permissions: $permissionsMissingOnStart" }
            return@withContext
        }

        val monitorJob = podMonitor.mainDevice
            .filterNotNull()
            .take(5)
            .setupCommonEventHandlers(TAG) { "monitorJob" }
            .launchIn(workerScope)

        try {
            withTimeout(60 * 1000) {
                monitorJob.join()
            }
            log(TAG) { "Monitor job quit after a few takes." }
        } catch (e: TimeoutCancellationException) {
            log(TAG) { "Monitor job quit after finding nothing." }
        }
    }

    companion object {
        val TAG = logTag("Monitor", "Worker")
    }
}
