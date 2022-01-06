package eu.darken.capod.monitor.core.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoints
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isRequired
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.monitor.core.MonitorComponent
import eu.darken.capod.monitor.core.MonitorCoroutineScope
import eu.darken.capod.monitor.ui.MonitorNotifications
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
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
    private val mediaControl: MediaControl,
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
    private var monitorFlowError: Throwable? = null
    private var podFlowError: Throwable? = null

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        coroutineScope {
            doDoWork()
        }
        monitorFlowError?.let { throw it }
        podFlowError?.let { throw it }

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
        val missingPermissions = Permission.values().filter { it.isRequired(context) }
        if (missingPermissions.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing permissions: $missingPermissions" }
            return
        }

        generalSettings.monitorMode.flow
            .flatMapLatest { monitorMode ->
                bluetoothManager2
                    .isBluetoothEnabled
                    .flatMapLatest { bluetoothManager2.connectedDevices() }
                    .map { devices ->
                        devices.filter { device ->
                            ContinuityProtocol.BLE_FEATURE_UUIDS.any { feature ->
                                device.hasFeature(feature)
                            }
                        }
                    }
                    .map { knownDevices -> monitorMode to knownDevices }
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
                    MonitorMode.AUTOMATIC -> flow<Unit> {
                        if (devices.isNotEmpty()) {
                            log(TAG) { "Pods are connected, aborting any timeout." }
                        } else {
                            log(TAG) { "No Pods are connected, canceling worker soon." }
                            delay(60 * 1000)
                            log(TAG) { "Canceling worker now, still no Pods connected." }

                            workerScope.coroutineContext.cancelChildren()
                        }
                    }
                }
            }
            .catch {
                monitorFlowError = it
                log(TAG, WARN) { "MonitorMode Flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        val monitorJob = podMonitor.mainDevice
            .setupCommonEventHandlers(TAG) { "PodMonitor" }
            .onStart {
                setForeground(monitorNotifications.getForegroundInfo(null))
            }
            .distinctUntilChanged()
            .onEach { currentDevice ->
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    monitorNotifications.getNotification(currentDevice)
                )
            }
            .withPrevious()
            .onEach { (previous, current) ->
                if (previous is HasEarDetection && current is HasEarDetection) {
                    log(TAG) { "previous=${previous.isBeingWorn}, current=${current.isBeingWorn}" }
                    log(TAG) { "previous-id=${previous.identifier}, current-id=${current.identifier}" }
                    if (previous.identifier == current.identifier && previous.isBeingWorn != current.isBeingWorn) {
                        if (generalSettings.autoPlay.value && !mediaControl.isPlaying) {
                            mediaControl.sendPlay()
                        } else if (generalSettings.autoPause.value && mediaControl.isPlaying) {
                            mediaControl.sendPause()
                        }
                    }
                }
            }
            .catch {
                podFlowError = it
                log(TAG, WARN) { "Pod Flow failed:\n${it.asLog()}" }
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
