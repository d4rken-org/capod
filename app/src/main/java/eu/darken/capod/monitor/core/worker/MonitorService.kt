package eu.darken.capod.monitor.core.worker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
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
import eu.darken.capod.common.hasApiLevel
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
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var notifications: MonitorNotifications
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var permissionTool: PermissionTool
    @Inject lateinit var podMonitor: PodMonitor
    @Inject lateinit var bluetoothManager: BluetoothManager2
    @Inject lateinit var playPause: PlayPause
    @Inject lateinit var autoConnect: AutoConnect
    @Inject lateinit var popUpReaction: PopUpReaction
    @Inject lateinit var popUpWindow: PopUpWindow
    @Inject lateinit var profilesRepo: DeviceProfilesRepo

    private val monitorScope = MonitorCoroutineScope()
    private var monitoringJob: Job? = null
    @Volatile private var monitorGeneration = 0

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        log(TAG, VERBOSE) { "onCreate()" }

        val notification = notifications.getStartupNotification()
        if (hasApiLevel(29)) {
            startForeground(
                MonitorNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(MonitorNotifications.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log(TAG, VERBOSE) { "onStartCommand(intent=$intent, flags=$flags, startId=$startId)" }

        val forceStart = intent?.getBooleanExtra(EXTRA_FORCE_START, false) ?: false

        if (monitoringJob?.isActive == true && !forceStart) {
            log(TAG) { "Already monitoring and forceStart=false, keeping current session." }
            return START_STICKY
        }

        val generation = ++monitorGeneration
        monitorScope.coroutineContext.cancelChildren()

        monitoringJob = monitorScope.launch {
            try {
                doMonitor()
            } catch (e: CancellationException) {
                log(TAG) { "Monitor cancelled." }
            } catch (e: Exception) {
                Bugs.report(tag = TAG, "Monitor failed", exception = e)
            } finally {
                if (monitorGeneration == generation) {
                    log(TAG) { "Monitor finished, stopping service." }
                    stopSelf()
                } else {
                    log(TAG) { "Monitor replaced, not stopping service." }
                }
            }
        }

        return START_STICKY
    }

    private suspend fun doMonitor() {
        val permissionsMissingOnStart = permissionTool.missingPermissions.first()
        if (permissionsMissingOnStart.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing permissions: $permissionsMissingOnStart" }
            return
        }

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
            .launchIn(monitorScope)

        permissionTool.missingPermissions
            .flatMapLatest { missingPermsFlow ->
                if (missingPermsFlow.isNotEmpty()) {
                    log(TAG, WARN) { "Aborting, permissions are missing: $missingPermsFlow" }
                    monitorScope.coroutineContext.cancelChildren()
                    emptyFlow()
                } else {
                    combine(
                        generalSettings.monitorMode.flow,
                        profilesRepo.profiles,
                        bluetoothManager.connectedDevices,
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
                        monitorScope.coroutineContext.cancelChildren()
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
                                log(TAG) { "No known Pods are connected, stopping service soon." }
                                delay(15 * 1000)
                                log(TAG) { "Stopping service now, still no Pods connected." }

                                monitorScope.coroutineContext.cancelChildren()
                            }
                        }
                    }
                }
            }
            .catch {
                log(TAG, WARN) { "MonitorMode Flow failed:\n${it.asLog()}" }
            }
            .launchIn(monitorScope)

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
            .launchIn(monitorScope)

        playPause.monitor()
            .setupCommonEventHandlers(TAG) { "playPause" }
            .catch { log(TAG, WARN) { "playPause failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        autoConnect.monitor()
            .setupCommonEventHandlers(TAG) { "autoConnect" }
            .catch { log(TAG, WARN) { "autoConnect failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }
    }

    override fun onDestroy() {
        log(TAG, VERBOSE) { "onDestroy()" }
        monitorScope.cancel("Service destroyed")

        if (generalSettings.useExtraMonitorNotification.value && !generalSettings.keepConnectedNotificationAfterDisconnect.value) {
            try {
                notificationManager.cancel(MonitorNotifications.NOTIFICATION_ID_CONNECTED)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to cancel connected notification: ${e.message}" }
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val TAG = logTag("Monitor", "Service")
        private const val EXTRA_FORCE_START = "extra.force_start"

        fun intent(context: Context, forceStart: Boolean = false): Intent {
            return Intent(context, MonitorService::class.java).apply {
                putExtra(EXTRA_FORCE_START, forceStart)
            }
        }
    }
}
