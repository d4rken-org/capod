package eu.darken.capod.monitor.core.worker

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.valueBlocking
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
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.MonitorCoroutineScope
import eu.darken.capod.monitor.core.MonitorModeResolver
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.monitor.ui.MonitorNotifications
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.autoconnect.AutoConnect
import eu.darken.capod.reaction.core.playpause.PlayPause
import eu.darken.capod.reaction.core.popup.PopUpReaction
import eu.darken.capod.reaction.core.sleep.SleepReaction
import eu.darken.capod.reaction.ui.popup.PopUpWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
    @Inject lateinit var blePodMonitor: BlePodMonitor
    @Inject lateinit var deviceMonitor: DeviceMonitor
    @Inject lateinit var bluetoothManager: BluetoothManager2
    @Inject lateinit var playPause: PlayPause
    @Inject lateinit var autoConnect: AutoConnect
    @Inject lateinit var popUpReaction: PopUpReaction
    @Inject lateinit var sleepReaction: SleepReaction
    @Inject lateinit var popUpWindow: PopUpWindow
    @Inject lateinit var profilesRepo: DeviceProfilesRepo
    @Inject lateinit var aapConnectionManager: AapConnectionManager
    @Inject lateinit var monitorModeResolver: MonitorModeResolver

    private val monitorScope = MonitorCoroutineScope()
    private var monitoringJob: Job? = null
    @Volatile private var monitorGeneration = 0
    private var foregroundStartFailed = false
    private var injectionComplete = false

    @SuppressLint("InlinedApi")
    private fun promoteToForeground(notification: Notification): Boolean {
        return try {
            if (hasApiLevel(29)) {
                startForeground(
                    MonitorNotifications.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(MonitorNotifications.NOTIFICATION_ID, notification)
            }
            true
        } catch (e: IllegalStateException) {
            @Suppress("NewApi")
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                log(TAG, WARN) { "Foreground service start denied by OS: ${e.message}" }
                false
            } else {
                throw e
            }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Foreground service start denied (security): ${e.message}" }
            false
        }
    }

    override fun onCreate() {
        // Promote to foreground BEFORE Hilt DI (triggered by super.onCreate()) to avoid
        // ForegroundServiceDidNotStartInTimeException when DI is slow on backgrounded cold starts.
        MonitorNotifications.ensureChannel(this)
        if (!promoteToForeground(MonitorNotifications.createEarlyNotification(this))) {
            foregroundStartFailed = true
            stopSelf()
            try {
                super.onCreate()
            } catch (e: Exception) {
                log(TAG, WARN) { "Hilt DI failed in onCreate() (foreground denied): ${e.asLog()}" }
                Bugs.report(tag = TAG, "Hilt DI failed in onCreate() (foreground denied)", exception = e)
            }
            return
        }

        try {
            super.onCreate()
        } catch (e: Exception) {
            log(TAG, WARN) { "Hilt DI failed in onCreate(), stopping service: ${e.asLog()}" }
            Bugs.report(tag = TAG, "Hilt DI failed in onCreate()", exception = e)
            foregroundStartFailed = true
            stopSelf()
            return
        }
        injectionComplete = true
        log(TAG, VERBOSE) { "onCreate()" }

        // Replace early notification with the full one from injected MonitorNotifications.
        // Failure here is non-fatal — the service is already foreground from the early call.
        promoteToForeground(notifications.getStartupNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log(TAG, VERBOSE) { "onStartCommand(intent=$intent, flags=$flags, startId=$startId)" }

        if (foregroundStartFailed) {
            log(TAG, WARN) { "Skipping monitor start, foreground promotion was denied." }
            stopSelf(startId)
            return START_NOT_STICKY
        }

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
        val permissionsMissingOnStart = permissionTool.missingScanPermissions.first()
        if (permissionsMissingOnStart.isNotEmpty()) {
            log(TAG, WARN) { "Aborting, missing scan permissions: $permissionsMissingOnStart" }
            return
        }

        val monitorJob = deviceMonitor.primaryDevice()
            .setupCommonEventHandlers(TAG) { "BlePodMonitor" }
            .distinctUntilChangedBy { it?.toNotificationKey() }
            .throttleLatest(1000)
            .onEach { currentDevice ->
                val useExtraNotification = generalSettings.useExtraMonitorNotification.valueBlocking
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    notifications.getNotification(currentDevice, showHint = useExtraNotification),
                )
                if (generalSettings.useExtraMonitorNotification.valueBlocking && currentDevice != null) {
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

        permissionTool.missingScanPermissions
            .flatMapLatest { missingPermsFlow ->
                if (missingPermsFlow.isNotEmpty()) {
                    log(TAG, WARN) { "Aborting, scan permissions are missing: $missingPermsFlow" }
                    monitorScope.coroutineContext.cancelChildren()
                    emptyFlow()
                } else {
                    combine(
                        monitorModeResolver.effectiveMode,
                        profilesRepo.profiles,
                        bluetoothManager.connectedDevices,
                        aapConnectionManager.allStates,
                    ) { mode, profiles, devices, aapStates ->
                        buildMonitorModeState(mode, profiles, devices, aapStates)
                    }
                }
            }
            .distinctUntilChanged()
            .setupCommonEventHandlers(TAG) { "MonitorMode" }
            .flatMapLatest { state ->
                log(TAG) { "Monitor mode: ${state.mode}" }
                log(TAG) { "connectedAddresses: ${state.connectedAddresses}" }
                log(TAG) { "knownAddresses: ${state.knownAddresses}" }

                when (state.mode) {
                    MonitorMode.MANUAL -> flow<Unit> {
                        monitorScope.coroutineContext.cancelChildren()
                    }

                    MonitorMode.ALWAYS -> emptyFlow()
                    MonitorMode.AUTOMATIC -> flow {
                        when {
                            !state.hasProfiles && state.connectedAddresses.isNotEmpty() -> {
                                log(TAG, WARN) { "Main device address not set, staying alive while any is connected" }
                            }

                            state.knownAddresses.any { it in state.connectedAddresses } || state.hasAapSession -> {
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

        sleepReaction.monitor()
            .setupCommonEventHandlers(TAG) { "sleepReaction" }
            .catch { log(TAG, WARN) { "sleepReaction failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }
    }

    override fun onDestroy() {
        log(TAG, VERBOSE) { "onDestroy()" }
        monitorScope.cancel("Service destroyed")

        if (injectionComplete) {
            if (generalSettings.useExtraMonitorNotification.valueBlocking && !generalSettings.keepConnectedNotificationAfterDisconnect.valueBlocking) {
                try {
                    notificationManager.cancel(MonitorNotifications.NOTIFICATION_ID_CONNECTED)
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to cancel connected notification: ${e.message}" }
                }
            }
        } else {
            log(TAG, WARN) { "onDestroy: Skipping notification cleanup, injection was incomplete." }
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

internal data class MonitorModeState(
    val mode: MonitorMode,
    val hasProfiles: Boolean,
    val knownAddresses: Set<BluetoothAddress>,
    val connectedAddresses: Set<BluetoothAddress>,
    val hasAapSession: Boolean,
)

internal fun buildMonitorModeState(
    mode: MonitorMode,
    profiles: List<DeviceProfile>,
    devices: Collection<BluetoothDevice2>,
    aapStates: Map<BluetoothAddress, AapPodState>,
): MonitorModeState = MonitorModeState(
    mode = mode,
    hasProfiles = profiles.isNotEmpty(),
    knownAddresses = profiles.mapNotNull { it.address }.toSet(),
    connectedAddresses = devices.map { it.address }.toSet(),
    hasAapSession = aapStates.isNotEmpty(),
)

private data class NotificationDeviceKey(
    val profileId: String?,
    val label: String?,
    val model: PodModel,
    val hasDualPods: Boolean,
    val hasCase: Boolean,
    val hasEarDetection: Boolean,
    val batteryLeft: Float?,
    val batteryRight: Float?,
    val batteryCase: Float?,
    val batteryHeadset: Float?,
    val isLeftPodCharging: Boolean?,
    val isRightPodCharging: Boolean?,
    val isCaseCharging: Boolean?,
    val isHeadsetBeingCharged: Boolean?,
    val isLeftInEar: Boolean?,
    val isRightInEar: Boolean?,
    val isBeingWorn: Boolean?,
    val iconRes: Int,
    val leftPodIcon: Int,
    val rightPodIcon: Int,
    val caseIcon: Int,
)

private fun PodDevice.toNotificationKey(): NotificationDeviceKey = NotificationDeviceKey(
    profileId = profileId,
    label = label,
    model = model,
    hasDualPods = hasDualPods,
    hasCase = hasCase,
    hasEarDetection = hasEarDetection,
    batteryLeft = batteryLeft,
    batteryRight = batteryRight,
    batteryCase = batteryCase,
    batteryHeadset = batteryHeadset,
    isLeftPodCharging = isLeftPodCharging,
    isRightPodCharging = isRightPodCharging,
    isCaseCharging = isCaseCharging,
    isHeadsetBeingCharged = isHeadsetBeingCharged,
    isLeftInEar = isLeftInEar,
    isRightInEar = isRightInEar,
    isBeingWorn = isBeingWorn,
    iconRes = iconRes,
    leftPodIcon = leftPodIcon,
    rightPodIcon = rightPodIcon,
    caseIcon = caseIcon,
)
