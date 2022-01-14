package eu.darken.capod.monitor.core.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoints
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.*
import eu.darken.capod.monitor.ui.MonitorNotifications
import eu.darken.capod.reaction.core.ReactionHub
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    monitorComponentBuilder: MonitorComponent.Builder,
    private val monitorNotifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
    private val generalSettings: GeneralSettings,
    private val permissionTool: PermissionTool,
    private val podMonitor: PodMonitor,
    private val reactionHub: ReactionHub,
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
        log(TAG, ERROR) { "Execution failed:\n${e.asLog()}" }
        Bugs.report(e)
        finishedWithError = true
        Result.failure(inputData)
    } finally {
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() {
        val permissionsMissingOnStart = permissionTool.missingPermissions()
        if (permissionsMissingOnStart.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing permissions: $permissionsMissingOnStart" }
            return
        }

        val monitorJob = podMonitor.mainDevice
            .setupCommonEventHandlers(TAG) { "PodMonitor" }
            .onStart { setForeground(monitorNotifications.getForegroundInfo(null)) }
            .distinctUntilChanged()
            .onEach { currentDevice ->
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    monitorNotifications.getNotification(currentDevice)
                )
            }
            .catch {
                log(TAG, WARN) { "Pod Flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        generalSettings.monitorMode.flow
            .flatMapLatest { monitorMode ->
                val missingPermsFlow = permissionTool.missingPermissions()
                if (missingPermsFlow.isNotEmpty()) {
                    log(TAG, WARN) { "Aborting, permissions are missing for $monitorMode: $missingPermsFlow" }
                    workerScope.coroutineContext.cancelChildren()
                    return@flatMapLatest emptyFlow()
                }

                bluetoothManager.connectedDevices().map { knownDevices ->
                    monitorMode to knownDevices
                }
            }
            .setupCommonEventHandlers(TAG) { "MonitorMode" }
            .flatMapLatest { (monitorMode, devices) ->
                log(TAG) { "Monitor mode: $monitorMode" }
                when (monitorMode) {
                    MonitorMode.MANUAL -> flow<Unit> {
                        // Cancel worker, ui scans manually
                        workerScope.coroutineContext.cancelChildren()
                    }
                    MonitorMode.ALWAYS -> emptyFlow()
                    MonitorMode.AUTOMATIC -> flow {
                        if (devices.isNotEmpty()) {
                            log(TAG) { "Pods are connected, aborting any timeout." }
                        } else {
                            log(TAG) { "No Pods are connected, canceling worker soon." }
                            delay(30 * 1000)
                            log(TAG) { "Canceling worker now, still no Pods connected." }

                            workerScope.coroutineContext.cancelChildren()
                        }
                    }
                }
            }
            .catch {
                log(TAG, WARN) { "MonitorMode Flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        reactionHub.monitor()
            .catch {
                log(TAG, WARN) { "Pod reactions failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }
    }

    companion object {
        val TAG = logTag("Monitor", "Worker")
    }
}
