package eu.darken.capod.main.ui.tile

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

/**
 * Pure data → data mapper. Decides which [AncTileState] to render given a snapshot
 * of inputs. Mirrors [AncWidgetRenderStateMapper]'s precedence so the two surfaces
 * stay in sync, while remaining unit-testable without Android resources.
 *
 * Note: cached/offline-tier devices land in [AncTileState.NotConnected] (not
 * [AncTileState.Connecting]) — without an active AAP session, "Connecting…" would
 * be misleading because nothing is in progress.
 */
object AncTileStateMapper {

    fun map(
        device: PodDevice?,
        isPro: Boolean,
        isBluetoothEnabled: Boolean,
        missingPermissions: Set<Permission>,
    ): AncTileState {
        if (!isPro) return AncTileState.NotPro
        if (missingPermissions.any { it.isTileBlocking }) return AncTileState.PermissionRequired
        if (!isBluetoothEnabled) return AncTileState.BluetoothOff
        if (device == null) return AncTileState.NoDevice
        if (!device.hasAncControl) return AncTileState.NoAncSupport
        if (!device.isAapConnected) return AncTileState.NotConnected
        if (!device.isAapReady) return AncTileState.Connecting

        val ancMode = device.ancMode ?: return AncTileState.Connecting
        val visible = device.visibleAncModes
        if (visible.isEmpty()) return AncTileState.Connecting

        return AncTileState.Active(
            current = ancMode.current,
            pending = device.pendingAncMode,
            visible = visible,
            deviceLabel = device.label,
            deviceAddress = device.address,
        )
    }
}

/**
 * A permission whose absence prevents the tile from working. Scan-blocking permissions
 * gate BLE; [Permission.BLUETOOTH_CONNECT] gates the AAP L2CAP socket — without either,
 * the tile cannot do useful work.
 */
private val Permission.isTileBlocking: Boolean
    get() = isScanBlocking || this == Permission.BLUETOOTH_CONNECT

sealed interface AncTileState {
    data object NotPro : AncTileState
    data object PermissionRequired : AncTileState
    data object BluetoothOff : AncTileState
    data object NoDevice : AncTileState
    data object NoAncSupport : AncTileState
    data object NotConnected : AncTileState
    data object Connecting : AncTileState
    data class Active(
        val current: AapSetting.AncMode.Value,
        val pending: AapSetting.AncMode.Value?,
        val visible: List<AapSetting.AncMode.Value>,
        val deviceLabel: String?,
        val deviceAddress: BluetoothAddress?,
    ) : AncTileState
}

/**
 * Cycles to the next mode in [visible]. Anchors on [pending] when it's still in
 * [visible] so rapid taps walk forward through the list rather than oscillate against
 * the device-echoed [current]. A [pending] that has been filtered out (e.g. user toggled
 * Allow Off mid-cycle) falls through to [current].
 */
internal fun pickNextMode(
    visible: List<AapSetting.AncMode.Value>,
    current: AapSetting.AncMode.Value?,
    pending: AapSetting.AncMode.Value?,
): AapSetting.AncMode.Value? {
    if (visible.isEmpty()) return null
    val anchor = pending?.takeIf { it in visible } ?: current ?: return visible.first()
    val idx = visible.indexOf(anchor)
    return if (idx < 0) visible.first() else visible[(idx + 1) % visible.size]
}
