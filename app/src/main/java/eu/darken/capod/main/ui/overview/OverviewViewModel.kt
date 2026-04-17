package eu.darken.capod.main.ui.overview

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
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
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val monitorControl: MonitorControl,
    private val deviceMonitor: DeviceMonitor,
    private val permissionTool: PermissionTool,
    private val generalSettings: GeneralSettings,
    debugSettings: DebugSettings,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothManager: BluetoothManager2,
    private val profilesRepo: DeviceProfilesRepo,
    private val aapManager: AapConnectionManager,
    private val timeSource: TimeSource,
) : ViewModel4(dispatcherProvider) {

    val requestPermissionEvent = SingleEventFlow<Permission>()

    sealed interface Event {
        data object OffModeRejectedByDevice : Event
    }

    val events = SingleEventFlow<Event>()

    init {
        launch {
            aapManager.offRejectedEvents.collect {
                events.tryEmit(Event.OffModeRejectedByDevice)
            }
        }
    }

    private val showUnmatchedDevices = MutableStateFlow(false)
    private val userExpansionOverrides = MutableStateFlow<Set<String>>(emptySet())

    val workerAutolaunch = permissionTool.missingScanPermissions
        .onEach { permissions ->
            if (permissions.isNotEmpty()) {
                log(TAG) { "Missing scan permissions: $permissions" }
                return@onEach
            }

            val shouldStart = when (generalSettings.monitorMode.valueBlocking) {
                MonitorMode.MANUAL -> false
                MonitorMode.AUTOMATIC -> {
                    try {
                        val devices = withTimeoutOrNull(5_000) { bluetoothManager.connectedDevices.first() }
                        devices?.isNotEmpty() == true
                    } catch (e: SecurityException) {
                        log(TAG) { "Can't check connected devices without BLUETOOTH_CONNECT: ${e.message}" }
                        false
                    }
                }

                MonitorMode.ALWAYS -> true
            }
            if (shouldStart) {
                log(TAG) { "Starting monitor" }
                monitorControl.startMonitor()
            }
        }
        .asLiveState()

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    private val pods = permissionTool.missingScanPermissions
        .flatMapLatest { permissions ->
            if (permissions.isNotEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }
            deviceMonitor.devices
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
        userExpansionOverrides,
    ) { _, permissions, devices, isDebugMode, isBluetoothEnabled, profiles, upgradeInfo, showUnmatched, expandedIds ->
        // Prune stale overrides (profiles that no longer exist)
        val currentProfileIds = profiles.map { it.id }.toSet()
        val prunedExpandedIds = expandedIds.filter { it in currentProfileIds }.toSet()

        State(
            now = timeSource.now(),
            permissions = permissions,
            devices = devices,
            isDebugMode = isDebugMode,
            isBluetoothEnabled = isBluetoothEnabled,
            profiles = profiles,
            upgradeInfo = upgradeInfo,
            showUnmatchedDevices = showUnmatched,
            userExpandedIds = prunedExpandedIds,
        )
    }.asLiveState()

    enum class BluetoothIconState { HIDDEN, DISABLED, NEARBY, CONNECTED }

    data class State(
        val now: Instant,
        val permissions: Set<Permission>,
        val devices: List<PodDevice>,
        val isDebugMode: Boolean,
        val isBluetoothEnabled: Boolean,
        val profiles: List<DeviceProfile>,
        val upgradeInfo: UpgradeRepo.Info,
        val showUnmatchedDevices: Boolean,
        val userExpandedIds: Set<String> = emptySet(),
    ) {
        val isScanBlocked: Boolean get() = permissions.any { it.isScanBlocking }

        /** Profile list order used as tiebreaker within each connection tier. */
        private val profileOrder: Map<String, Int> by lazy {
            profiles.mapIndexed { index, profile -> profile.id to index }.toMap()
        }

        val profiledDevices: List<PodDevice> by lazy {
            devices.filter { it.profileId != null }.sortedWith(
                compareBy<PodDevice> { deviceTierRank(it) }
                    .thenBy { profileOrder[it.profileId] ?: Int.MAX_VALUE }
            )
        }

        val visibleProfiledDevices: List<PodDevice>
            get() = if (upgradeInfo.isPro) profiledDevices else profiledDevices.take(FREE_DEVICE_LIMIT)
        val hiddenProfiledDeviceCount: Int get() = profiledDevices.size - visibleProfiledDevices.size
        val unmatchedDevices: List<PodDevice> get() = devices.filter { it.profileId == null }

        val bluetoothIconState: BluetoothIconState
            get() = when {
                isScanBlocked -> BluetoothIconState.HIDDEN
                profiles.isEmpty() -> BluetoothIconState.HIDDEN
                !isBluetoothEnabled -> BluetoothIconState.DISABLED
                profiledDevices.any { it.isSystemConnected } -> BluetoothIconState.CONNECTED
                profiledDevices.any { it.isLive } -> BluetoothIconState.NEARBY
                else -> BluetoothIconState.HIDDEN
            }

        fun isPinned(device: PodDevice, index: Int): Boolean =
            device.isSystemConnected || index == 0

        fun isExpanded(device: PodDevice, index: Int): Boolean =
            isPinned(device, index) || device.profileId in userExpandedIds

        fun isToggleable(device: PodDevice, index: Int): Boolean =
            !isPinned(device, index)
    }

    fun onPermissionResult() {
        permissionTool.recheck()
    }

    fun onSettingsPermissionResult() {
        permissionTool.recheck()

        launch {
            delay(1000L)
            permissionTool.recheck()
        }
    }

    fun goToSettings() {
        log(TAG, INFO) { "goToSettings()" }
        navTo(Nav.Settings.Index)
    }

    fun goToDeviceManager() {
        log(TAG, INFO) { "goToDeviceManager()" }
        navTo(Nav.Main.DeviceManager)
    }

    fun goToDeviceSettings(device: PodDevice) {
        val profileId = device.profileId ?: return
        log(TAG, INFO) { "goToDeviceSettings(profileId=$profileId)" }
        navTo(Nav.Main.DeviceSettings(profileId))
    }

    fun goToEditProfile(device: PodDevice) {
        val profileId = device.profileId ?: return
        log(TAG, INFO) { "goToEditProfile(profileId=$profileId)" }
        navTo(Nav.Main.DeviceProfileCreation(profileId = profileId))
    }

    fun onUpgrade() {
        log(TAG, INFO) { "onUpgrade()" }
        navTo(Nav.Main.Upgrade)
    }

    fun toggleUnmatchedDevices() {
        log(TAG, INFO) { "toggleUnmatchedDevices()" }
        showUnmatchedDevices.value = !showUnmatchedDevices.value
    }

    fun toggleDeviceExpansion(profileId: String) {
        log(TAG, INFO) { "toggleDeviceExpansion(profileId=$profileId)" }
        userExpansionOverrides.value = userExpansionOverrides.value.let { current ->
            if (profileId in current) current - profileId else current + profileId
        }
    }

    fun requestPermission(permission: Permission) {
        log(TAG, INFO) { "requestPermission($permission)" }
        requestPermissionEvent.tryEmit(permission)
    }

    fun setAncMode(device: PodDevice, mode: AapSetting.AncMode.Value) {
        val address = device.address ?: return
        launch {
            try {
                aapManager.sendCommand(address, AapCommand.SetAncMode(mode))
                log(TAG, INFO) { "ANC mode set to $mode for $address" }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to set ANC mode: ${e.message}" }
            }
        }
    }

    companion object {
        private const val FREE_DEVICE_LIMIT = 1
        private val TAG = logTag("Overview", "VM")

        /** Connection tier rank for sorting: lower = higher priority. */
        internal fun deviceTierRank(device: PodDevice): Int = when {
            device.isSystemConnected -> 0
            device.isLive -> 1
            else -> 2
        }
    }
}
