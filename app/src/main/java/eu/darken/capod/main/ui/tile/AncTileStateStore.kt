package eu.darken.capod.main.ui.tile

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.primaryByTier
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine as combineFlows

/**
 * Process-scoped state holder for the ANC QS tile.
 *
 * TileService instances are short-lived and can disappear between taps. Keeping
 * the latest state here lets a recreated service resolve clicks synchronously
 * while the app process is alive. A cold or hydrating process intentionally
 * reports [AncTileState.Connecting] so the tile UI is honest about not being
 * ready for input yet.
 */
@Singleton
class AncTileStateStore @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    deviceMonitor: DeviceMonitor,
    profilesRepo: DeviceProfilesRepo,
    upgradeRepo: UpgradeRepo,
    bluetoothManager: BluetoothManager2,
    permissionTool: PermissionTool,
    private val sendCoordinator: AncTileSendCoordinator,
) {

    private val rawState: StateFlow<AncTileState> = combine(
        deviceMonitor.devices,
        profilesRepo.profiles,
        upgradeRepo.upgradeInfo.map { it.isPro },
        bluetoothManager.isBluetoothEnabled,
        permissionTool.missingPermissions,
    ) { devices, profiles, isPro, isBluetoothEnabled, missingPermissions ->
        val profileOrder = profiles.mapIndexed { idx, p -> p.id to idx }.toMap()
        val device = devices.primaryByTier(profileOrder)
        AncTileStateMapper.map(
            device = device,
            isPro = isPro,
            isBluetoothEnabled = isBluetoothEnabled,
            missingPermissions = missingPermissions,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = AncTileState.Connecting,
        )

    init {
        appScope.launch {
            rawState.collect { state -> sendCoordinator.acknowledgeDeviceState(state) }
        }
    }

    val state: StateFlow<AncTileState> = combineFlows(
        rawState,
        sendCoordinator.pendingModes,
    ) { rawState, _ ->
        sendCoordinator.applyPendingTarget(rawState)
    }
        .distinctUntilChanged()
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = AncTileState.Connecting,
        )

    fun currentState(): AncTileState = sendCoordinator.applyPendingTarget(rawState.value)
}
