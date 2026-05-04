package eu.darken.capod.main.ui.widget

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

/**
 * Snapshot of the [PodDevice] fields that affect widget rendering. Used to
 * gate widget refreshes so RSSI / reliability / scan-counter / timestamp churn
 * doesn't cause a refresh on every BLE advertisement.
 */
internal data class WidgetDeviceKey(
    val profileId: String?,
    val profileLabel: String?,
    val model: PodModel,
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
    val isAapConnected: Boolean,
    val isAapReady: Boolean,
    val hasBleAdvertisement: Boolean,
    val ancMode: AapSetting.AncMode.Value?,
    val pendingAncMode: AapSetting.AncMode.Value?,
    val visibleAncModes: List<AapSetting.AncMode.Value>,
)

internal fun PodDevice.toWidgetKey(): WidgetDeviceKey = WidgetDeviceKey(
    profileId = profileId,
    profileLabel = label,
    model = model,
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
    isAapConnected = isAapConnected,
    isAapReady = isAapReady,
    hasBleAdvertisement = ble != null,
    ancMode = ancMode?.current,
    pendingAncMode = pendingAncMode,
    visibleAncModes = visibleAncModes,
)
