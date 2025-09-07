package eu.darken.capod.monitor.core.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.MonitorCoroutineScope
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.monitor.ui.MonitorNotifications
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.autoconnect.AutoConnect
import eu.darken.capod.reaction.core.playpause.PlayPause
import eu.darken.capod.reaction.core.popup.PopUpReaction
import eu.darken.capod.reaction.ui.popup.PopUpWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val notifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
    private val generalSettings: GeneralSettings,
    private val permissionTool: PermissionTool,
    private val podMonitor: PodMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val playPause: PlayPause,
    private val autoConnect: AutoConnect,
    private val popUpReaction: PopUpReaction,
    private val popUpWindow: PopUpWindow,
    private val profilesRepo: DeviceProfilesRepo,
) : CoroutineWorker(context, params) {

    private val workerScope = MonitorCoroutineScope()

    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return notifications.getForegroundInfo(null)
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
        if (generalSettings.useExtraMonitorNotification.value && !generalSettings.keepConnectedNotificationAfterDisconnect.value) {
            try {
                notificationManager.cancel(MonitorNotifications.NOTIFICATION_ID_CONNECTED)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to cancel connected notification: ${e.message}" }
            }
        }
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() {
        val permissionsMissingOnStart = permissionTool.missingPermissions.first()
        if (permissionsMissingOnStart.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing permissions: $permissionsMissingOnStart" }
            return
        }

        setForeground(notifications.getForegroundInfo(null))

        val monitorJob = podMonitor.primaryDevice()
            .setupCommonEventHandlers(TAG) { "PodMonitor" }
            .distinctUntilChanged()
            .throttleLatest(1000)
            .onEach { currentDevice ->
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    notifications.getNotification(currentDevice),
                )
                if (generalSettings.useExtraMonitorNotification.value && currentDevice != null) {
                    notificationManager.notify(
                        MonitorNotifications.NOTIFICATION_ID_CONNECTED,
                        notifications.getNotificationConnected(currentDevice),
                    )
                }
            }
            .catch {
                log(TAG, WARN) { "Pod Flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        permissionTool.missingPermissions
            .flatMapLatest { missingPermsFlow ->
                if (missingPermsFlow.isNotEmpty()) {
                    log(TAG, WARN) { "Aborting, permissions are missing: $missingPermsFlow" }
                    workerScope.coroutineContext.cancelChildren()
                    emptyFlow()
                } else {
                    combine(
                        generalSettings.monitorMode.flow,
                        profilesRepo.profiles,
                        bluetoothManager.connectedDevices(),
                    ) { monitorMode, profiles, connectedDevices ->
                        listOf(monitorMode, profiles, connectedDevices)
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "MonitorMode" }
            .flatMapLatest { arguments ->
                val monitorMode = arguments[0] as MonitorMode

                @Suppress("UNCHECKED_CAST")
                val profiles = arguments[1] as List<DeviceProfile>
                @Suppress("UNCHECKED_CAST")
                val devices = arguments[2] as Collection<BluetoothDevice2>


                val connectedAddresses = devices.map { it.address }.toSet()
                val knownAddresses = profiles.mapNotNull { it.address }.toSet()
                log(TAG) { "Monitor mode: $monitorMode" }
                log(TAG) { "connectedAddresses: $connectedAddresses" }
                log(TAG) { "knownAddresses: $knownAddresses" }

                when (monitorMode) {
                    MonitorMode.MANUAL -> flow<Unit> {
                        // Cancel worker, ui scans manually
                        workerScope.coroutineContext.cancelChildren()
                    }

                    MonitorMode.ALWAYS -> emptyFlow()
                    MonitorMode.AUTOMATIC -> flow {
                        when {
                            profiles.isEmpty() && devices.isNotEmpty() -> {
                                log(TAG, WARN) { "Main device address not set, staying alive while any is connected" }
                            }

                            knownAddresses.any { it in connectedAddresses } -> {
                                log(TAG) { "A device is connected, aborting any timeout." }
                            }

                            else -> {
                                log(TAG) { "No known Pods are connected, canceling worker soon." }
                                delay(15 * 1000)
                                log(TAG) { "Canceling worker now, still no Pods connected." }

                                workerScope.coroutineContext.cancelChildren()
                            }
                        }
                    }
                }
            }
            .catch {
                log(TAG, WARN) { "MonitorMode Flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        popUpReaction.monitor()
            .onEach {
                withContext(dispatcherProvider.Main) {
                    when (it) {
                        is PopUpReaction.Event.PopupShow -> popUpWindow.show(it.device)
                        is PopUpReaction.Event.PopupHide -> popUpWindow.close()
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "popUpReaction" }
            .catch { log(TAG, WARN) { "popUpReaction failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        playPause.monitor()
            .setupCommonEventHandlers(TAG) { "playPause" }
            .catch { log(TAG, WARN) { "playPause failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        autoConnect.monitor()
            .setupCommonEventHandlers(TAG) { "autoConnect" }
            .catch { log(TAG, WARN) { "autoConnect failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }
    }

    companion object {
        val TAG = logTag("Monitor", "Worker")
    }
}
