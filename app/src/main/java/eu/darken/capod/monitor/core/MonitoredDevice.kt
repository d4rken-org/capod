package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting

/**
 * Unified device facade combining BLE scan data and AAP connection data.
 * All properties are dynamically resolved from the best available source.
 * Consumers don't need to know whether data came from BLE or AAP.
 *
 * TODO: Rename to PodDevice once old PodDevice interface is renamed to BlePodSnapshot.
 */
data class MonitoredDevice(
    internal val ble: PodDevice?,
    internal val aap: AapPodState?,
) {
    // Identity
    val model: PodDevice.Model get() = ble?.model ?: PodDevice.Model.UNKNOWN
    val address: BluetoothAddress? get() = ble?.address

    // Capabilities from Model.Features
    val hasCase: Boolean get() = model.features.hasCase
    val hasDualPods: Boolean get() = model.features.hasDualPods
    val hasEarDetection: Boolean get() = model.features.hasEarDetection
    val hasAncControl: Boolean get() = model.features.hasAncControl

    // Battery — best available source
    val batteryLeft: Float?
        get() = (ble as? DualPodDevice)?.batteryLeftPodPercent

    val batteryRight: Float?
        get() = (ble as? DualPodDevice)?.batteryRightPodPercent

    val batteryCase: Float?
        get() = (ble as? HasCase)?.batteryCasePercent

    val batteryHeadset: Float?
        get() = (ble as? SinglePodDevice)?.batteryHeadsetPercent

    // State
    val isLeftInEar: Boolean?
        get() = (ble as? HasEarDetectionDual)?.isLeftPodInEar

    val isRightInEar: Boolean?
        get() = (ble as? HasEarDetectionDual)?.isRightPodInEar

    val caseLidState: DualApplePods.LidState?
        get() = (ble as? DualApplePods)?.caseLidState

    // AAP controls
    val ancMode: AapSetting.AncMode?
        get() = aap?.setting()

    val isAapConnected: Boolean get() = aap != null
}
