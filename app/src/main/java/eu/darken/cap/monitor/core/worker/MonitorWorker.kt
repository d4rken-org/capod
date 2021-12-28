package eu.darken.cap.monitor.core.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoints
import eu.darken.cap.common.debug.logging.Logging.Priority.ERROR
import eu.darken.cap.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.cap.common.debug.logging.asLog
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.debug.logging.logTag
import eu.darken.cap.common.flow.setupCommonEventHandlers
import eu.darken.cap.monitor.core.MonitorComponent
import eu.darken.cap.monitor.core.MonitorCoroutineScope
import eu.darken.cap.monitor.ui.MonitorNotifications
import eu.darken.cap.pods.core.airpods.ContinuityProtocol
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    monitorComponentBuilder: MonitorComponent.Builder,
    private val monitorNotifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, params) {

    private val workerScope = MonitorCoroutineScope()
    private val monitorComponent = monitorComponentBuilder
        .coroutineScope(workerScope)
        .build()

    private val entryPoint by lazy {
        EntryPoints.get(monitorComponent, MonitorWorkerEntryPoint::class.java)
    }

    private val podMonitor by lazy {
        entryPoint.podMonitor()
    }
    private val bluetoothManager2 by lazy {
        entryPoint.bluetoothManager2()
    }
    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        bluetoothManager2
            .isBluetoothEnabled
            .flatMapLatest { bluetoothManager2.connectedDevices() }
            .map { devices ->
                devices.any { device ->
                    ContinuityProtocol.BLE_FEATURE_UUIDS.any { feature ->
                        device.hasFeature(feature)
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "ConnectedDevices" }
            .flatMapLatest { arePodsConnected ->
                flow<Unit> {
                    if (arePodsConnected) {
                        log(TAG) { "Pods are connected, aborting any timeout." }
                    } else {
                        log(TAG) { "No Pods are connected, canceling worker soon." }
                        delay(60 * 1000)
                        log(TAG) { "Canceling worker now, still no Pods connected." }
                        // FIXME
//                        workerScope.coroutineContext.cancelChildren()
                    }
                }
            }
            .launchIn(workerScope)


        val monitorJob = podMonitor.pods
            .setupCommonEventHandlers(TAG) { "PodMonitor" }
            .onStart {
                setForeground(monitorNotifications.getForegroundInfo(null))
            }
            .onEach {
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    monitorNotifications.getNotification(it.firstOrNull())
                )
            }
            .launchIn(workerScope)

        log(TAG, VERBOSE) { "monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "monitor job quit" }

        val duration = System.currentTimeMillis() - start

        log(TAG, VERBOSE) { "Execution finished after ${duration}ms, $inputData" }

        Result.success(inputData)
    } catch (e: Throwable) {
        log(TAG, ERROR) { "Execution failed:\n${e.asLog()}" }
        finishedWithError = true
        // TODO update result?
        Result.failure(inputData)
    } finally {
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    companion object {
        val TAG = logTag("Monitor", "Worker")
    }
}
