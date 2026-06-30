package eu.darken.capod.main.ui.overview

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.Bugs
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
import eu.darken.capod.monitor.core.MonitorModeResolver
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.monitor.core.battery.BatteryEstimator
import eu.darken.capod.monitor.core.tierRank
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
import kotlinx.coroutines.flow.combine as combineFlows
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val monitorControl: MonitorControl,
    private val deviceMonitor: DeviceMonitor,
    private val permissionTool: PermissionTool,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothManager: BluetoothManager2,
    private val profilesRepo: DeviceProfilesRepo,
    private val aapManager: AapConnectionManager,
    private val monitorModeResolver: MonitorModeResolver,
    private val batteryEstimator: BatteryEstimator,
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

    private data class OverviewUiSettings(
        val reactionsHintDismissed: Boolean,
        val hideUnmatchedDevices: Boolean,
        val showTroubleshootSuggestion: Boolean,
        val batteryEstimateEnabled: Boolean,
        val batteryEstimates: Map<String, BatteryEstimate>,
    )

    /**
     * True when a profile is connected to the system (audio) but CAPod has *no* live data for any
     * device — the classic "broadcasts are being dropped by the ROM" symptom (e.g. some HyperOS
     * phones, see #603). Surfaced as a hint pointing at the Troubleshooter, which can probe and
     * persist a working compatibility combo. Debounced so it doesn't flash during the brief window
     * between an audio connection and the first BLE broadcast. Suppressed whenever any pod is live,
     * so it never claims "no data" while data is visibly arriving (incl. the #603 duplicate state).
     */
    private val troubleshootSuggestion = combineFlows(
        profilesRepo.profiles,
        bluetoothManager.connectedDevices.onStart { emit(emptyList()) },
        deviceMonitor.devices,
        permissionTool.missingScanPermissions,
    ) { profiles, connectedDevices, devices, missingScanPermissions ->
        val connectedAddresses = connectedDevices.mapTo(mutableSetOf()) { it.address }
        val anyProfileConnected = profiles.any { it.address != null && it.address in connectedAddresses }
        val anyLivePod = devices.any { it.isLive }
        missingScanPermissions.isEmpty() && anyProfileConnected && !anyLivePod
    }
        .distinctUntilChanged()
        .flatMapLatest { conditionMet ->
            if (conditionMet) flow {
                delay(TROUBLESHOOT_SUGGESTION_DELAY_MS)
                emit(true)
            } else flowOf(false)
        }
        .onStart { emit(false) }
        .distinctUntilChanged()

    private val overviewUiSettings = combineFlows(
        generalSettings.reactionsHintDismissed.flow,
        generalSettings.hideUnmatchedDevices.flow,
        troubleshootSuggestion,
        generalSettings.batteryEstimateEnabled.flow,
        batteryEstimator.estimates,
    ) { reactionsHintDismissed, hideUnmatched, showTroubleshootSuggestion, batteryEstimateEnabled, batteryEstimates ->
        OverviewUiSettings(
            reactionsHintDismissed = reactionsHintDismissed,
            hideUnmatchedDevices = hideUnmatched,
            showTroubleshootSuggestion = showTroubleshootSuggestion,
            batteryEstimateEnabled = batteryEstimateEnabled,
            batteryEstimates = batteryEstimates,
        )
    }

    init {
        // When the persistent "hide unmatched" setting is enabled, reset the in-session expand
        // toggle so the section reappears collapsed if the user later disables the setting.
        launch {
            generalSettings.hideUnmatchedDevices.flow
                .filter { it }
                .collect { showUnmatchedDevices.value = false }
        }
    }

    val workerAutolaunch = combine(
        permissionTool.missingScanPermissions,
        monitorModeResolver.effectiveMode,
        // BluetoothManager2.connectedDevices is gated by a slow HEADSET-profile lookup. Without
        // an onStart seed the combine wouldn't fire until that profile is bound, so ALWAYS mode
        // would silently delay autolaunch until the first connected-device emission.
        bluetoothManager.connectedDevices
            .onStart { emit(emptyList()) }
            .map { it.isNotEmpty() }
            .distinctUntilChanged(),
    ) { missing, mode, anyConnected -> Triple(missing, mode, anyConnected) }
        .distinctUntilChanged()
        .onEach { (missing, mode, anyConnected) ->
            if (missing.isNotEmpty()) {
                log(TAG) { "Missing scan permissions: $missing" }
                return@onEach
            }
            val shouldStart = when (mode) {
                MonitorMode.MANUAL -> false
                MonitorMode.AUTOMATIC -> anyConnected
                MonitorMode.ALWAYS -> true
            }
            if (shouldStart) {
                log(TAG) { "Starting monitor (mode=$mode)" }
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
        Bugs.isDebug,
        bluetoothManager.isBluetoothEnabled,
        profilesRepo.profiles,
        upgradeRepo.upgradeInfo,
        showUnmatchedDevices,
        userExpansionOverrides,
        overviewUiSettings,
        profilesRepo.hadLegacyReactionData,
    ) { _, permissions, devices, isDebug, isBluetoothEnabled, profiles, upgradeInfo, showUnmatched, expandedIds, uiSettings, hadLegacyReactionData ->
        // Prune stale overrides (profiles that no longer exist)
        val currentProfileIds = profiles.map { it.id }.toSet()
        val prunedExpandedIds = expandedIds.filter { it in currentProfileIds }.toSet()

        State(
            now = timeSource.now(),
            permissions = permissions,
            devices = devices,
            isDebug = isDebug,
            isBluetoothEnabled = isBluetoothEnabled,
            profiles = profiles,
            upgradeInfo = upgradeInfo,
            showUnmatchedDevices = showUnmatched,
            userExpandedIds = prunedExpandedIds,
            showReactionsHint = hadLegacyReactionData && !uiSettings.reactionsHintDismissed,
            hideUnmatchedDevices = uiSettings.hideUnmatchedDevices,
            showTroubleshootSuggestion = uiSettings.showTroubleshootSuggestion,
            batteryEstimateEnabled = uiSettings.batteryEstimateEnabled,
            batteryEstimates = uiSettings.batteryEstimates,
        )
    }.asLiveState()

    enum class BluetoothIconState { HIDDEN, DISABLED, NEARBY, CONNECTED }

    data class State(
        val now: Instant,
        val permissions: Set<Permission>,
        val devices: List<PodDevice>,
        val isDebug: Boolean,
        val isBluetoothEnabled: Boolean,
        val profiles: List<DeviceProfile>,
        val upgradeInfo: UpgradeRepo.Info,
        val showUnmatchedDevices: Boolean,
        val userExpandedIds: Set<String> = emptySet(),
        val showReactionsHint: Boolean = false,
        val hideUnmatchedDevices: Boolean = false,
        val showTroubleshootSuggestion: Boolean = false,
        val batteryEstimateEnabled: Boolean = true,
        val batteryEstimates: Map<String, BatteryEstimate> = emptyMap(),
    ) {
        val isScanBlocked: Boolean get() = permissions.any { it.isScanBlocking }

        /**
         * Time-remaining estimate to show for [device], or null when the user disabled the feature,
         * the device isn't live (no estimate for cached/offline cards), or no rate has been learned.
         */
        fun estimateFor(device: PodDevice): BatteryEstimate? {
            if (!batteryEstimateEnabled) return null
            if (!device.isLive) return null
            val profileId = device.profileId ?: return null
            return batteryEstimates[profileId]
        }

        /** Profile list order used as tiebreaker within each connection tier. */
        private val profileOrder: Map<String, Int> by lazy {
            profiles.mapIndexed { index, profile -> profile.id to index }.toMap()
        }

        val profiledDevices: List<PodDevice> by lazy {
            devices.filter { it.profileId != null }.sortedWith(
                compareBy<PodDevice> { it.tierRank() }
                    .thenBy { profileOrder[it.profileId] ?: Int.MAX_VALUE }
            )
        }

        val visibleProfiledDevices: List<PodDevice>
            get() = if (upgradeInfo.isPro) profiledDevices else profiledDevices.take(FREE_DEVICE_LIMIT)
        val hiddenProfiledDeviceCount: Int get() = profiledDevices.size - visibleProfiledDevices.size
        val unmatchedDevices: List<PodDevice> get() = devices.filter { it.profileId == null }

        /** Unmatched devices actually shown on the dashboard (empty when the hide setting is on). */
        val visibleUnmatchedDevices: List<PodDevice>
            get() = if (hideUnmatchedDevices) emptyList() else unmatchedDevices
        val shouldShowUnmatchedSection: Boolean get() = visibleUnmatchedDevices.isNotEmpty()

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

    fun goToTroubleShooter() {
        log(TAG, INFO) { "goToTroubleShooter()" }
        navTo(Nav.Main.TroubleShooter)
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

    fun dismissReactionsHint() {
        log(TAG, INFO) { "dismissReactionsHint()" }
        launch {
            generalSettings.reactionsHintDismissed.value(true)
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

        /**
         * How long the "connected but no live data" condition must hold before the troubleshooter
         * hint appears, so it doesn't flash during the gap between an audio connection and the first
         * BLE broadcast.
         */
        private const val TROUBLESHOOT_SUGGESTION_DELAY_MS = 15_000L

        private val TAG = logTag("Overview", "VM")
    }
}
