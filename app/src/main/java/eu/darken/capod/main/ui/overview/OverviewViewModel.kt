package eu.darken.capod.main.ui.overview

import android.app.Activity
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val monitorControl: MonitorControl,
    private val podMonitor: PodMonitor,
    private val permissionTool: PermissionTool,
    private val generalSettings: GeneralSettings,
    debugSettings: DebugSettings,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothManager: BluetoothManager2,
    private val profilesRepo: DeviceProfilesRepo,
) : ViewModel4(dispatcherProvider) {

    init {
        if (!generalSettings.isOnboardingDone.value) {
            navTo(Nav.Main.Onboarding, popUpTo = Nav.Main.Overview, inclusive = true)
        }
    }

    val requestPermissionEvent = SingleEventFlow<Permission>()
    val launchUpgradeFlow = SingleEventFlow<(Activity) -> Unit>()

    private val showUnmatchedDevices = MutableStateFlow(false)

    val workerAutolaunch = permissionTool.missingPermissions
        .onEach { permissions ->
            if (permissions.isNotEmpty()) {
                log(TAG) { "Missing permissions: $permissions" }
                return@onEach
            }

            val shouldStart = when (generalSettings.monitorMode.value) {
                MonitorMode.MANUAL -> false
                MonitorMode.AUTOMATIC -> {
                    val devices = withTimeoutOrNull(5_000) { bluetoothManager.connectedDevices.first() }
                    devices?.isNotEmpty() == true
                }
                MonitorMode.ALWAYS -> true
            }
            if (shouldStart) {
                log(TAG) { "Starting monitor" }
                monitorControl.startMonitor()
            }
        }
        .shareLatest(scope = vmScope)

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    private val pods = permissionTool.missingPermissions
        .flatMapLatest { permissions ->
            if (permissions.isNotEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }
            podMonitor.devices
        }
        .catch { errorEvents.emitBlocking(it) }
        .throttleLatest(1000)

    val state = combine(
        updateTicker,
        permissionTool.missingPermissions,
        pods,
        debugSettings.isDebugModeEnabled.flow,
        bluetoothManager.isBluetoothEnabled,
        profilesRepo.profiles,
        upgradeRepo.upgradeInfo,
        showUnmatchedDevices,
    ) { _, permissions, devices, isDebugMode, isBluetoothEnabled, profiles, upgradeInfo, showUnmatched ->
        State(
            now = Instant.now(),
            permissions = permissions,
            devices = devices,
            isDebugMode = isDebugMode,
            isBluetoothEnabled = isBluetoothEnabled,
            profiles = profiles,
            upgradeInfo = upgradeInfo,
            showUnmatchedDevices = showUnmatched,
        )
    }.shareLatest(scope = vmScope)

    data class State(
        val now: Instant,
        val permissions: Set<Permission>,
        val devices: List<PodDevice>,
        val isDebugMode: Boolean,
        val isBluetoothEnabled: Boolean,
        val profiles: List<DeviceProfile>,
        val upgradeInfo: UpgradeRepo.Info,
        val showUnmatchedDevices: Boolean,
    ) {
        val profiledDevices: List<PodDevice> get() = devices.filter { it.meta.profile != null }
        val unmatchedDevices: List<PodDevice> get() = devices.filter { it.meta.profile == null }
    }

    fun onPermissionResult(@Suppress("UNUSED_PARAMETER") granted: Boolean) {
        permissionTool.recheck()
    }

    fun goToSettings() {
        navTo(Nav.Settings.Index)
    }

    fun goToDeviceManager() {
        navTo(Nav.Main.DeviceManager)
    }

    fun onUpgrade() = launch {
        launchUpgradeFlow.tryEmit { upgradeRepo.launchBillingFlow(it) }
    }

    fun toggleUnmatchedDevices() {
        showUnmatchedDevices.value = !showUnmatchedDevices.value
    }

    fun requestPermission(permission: Permission) {
        requestPermissionEvent.tryEmit(permission)
    }

    companion object {
        private val TAG = logTag("Overview", "VM")
    }
}
