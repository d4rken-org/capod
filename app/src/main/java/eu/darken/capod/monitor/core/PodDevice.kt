package eu.darken.capod.monitor.core

import android.content.Context
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.pods.core.BlePodSnapshot
import eu.darken.capod.pods.core.DualBlePodSnapshot
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasDualMicrophone
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodModel
import eu.darken.capod.pods.core.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState
import eu.darken.capod.pods.core.apple.protocol.aap.AapSetting
import java.time.Instant

/**
 * Unified device facade combining BLE scan data and AAP connection data.
 * All properties are dynamically resolved from the best available source.
 * Consumers don't need to know whether data came from BLE or AAP.
 */
data class PodDevice(
    internal val ble: BlePodSnapshot?,
    internal val aap: AapPodState?,
) {
    // Identity
    val model: PodModel get() = ble?.model ?: PodModel.UNKNOWN
    /** Bonded BR/EDR address (from profile). Used for AAP commands. */
    val address: BluetoothAddress? get() = ble?.meta?.profile?.address
    /** BLE scan address (RPA, rotates). */
    val bleAddress: BluetoothAddress? get() = ble?.address
    val identifier: BlePodSnapshot.Id? get() = ble?.identifier
    val meta: BlePodSnapshot.Meta? get() = ble?.meta

    // Capabilities from Model.Features
    val hasCase: Boolean get() = model.features.hasCase
    val hasDualPods: Boolean get() = model.features.hasDualPods
    val hasEarDetection: Boolean get() = model.features.hasEarDetection
    val hasAncControl: Boolean get() = model.features.hasAncControl
    val hasDualMicrophone: Boolean get() = ble is HasDualMicrophone

    // Signal / timing
    val seenLastAt: Instant? get() = ble?.seenLastAt
    val seenFirstAt: Instant? get() = ble?.seenFirstAt
    val signalQuality: Float get() = ble?.signalQuality ?: 0f
    val rssi: Int get() = ble?.rssi ?: 0

    // Battery — AAP preferred, BLE fallback
    val batteryLeft: Float?
        get() = aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent

    val batteryRight: Float?
        get() = aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent

    val batteryCase: Float?
        get() = aap?.batteryCase ?: (ble as? HasCase)?.batteryCasePercent

    val batteryHeadset: Float?
        get() = aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent

    // Charging — AAP preferred, BLE fallback
    val isLeftPodCharging: Boolean?
        get() = aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging

    val isRightPodCharging: Boolean?
        get() = aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging

    val isCaseCharging: Boolean?
        get() = aap?.isCaseCharging ?: (ble as? HasCase)?.isCaseCharging

    val isHeadsetBeingCharged: Boolean?
        get() = aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged

    // Ear detection
    val isLeftInEar: Boolean?
        get() = (ble as? HasEarDetectionDual)?.isLeftPodInEar

    val isRightInEar: Boolean?
        get() = (ble as? HasEarDetectionDual)?.isRightPodInEar

    val isBeingWorn: Boolean?
        get() = (ble as? HasEarDetection)?.isBeingWorn

    val isEitherPodInEar: Boolean?
        get() = (ble as? HasEarDetectionDual)?.isEitherPodInEar

    val caseLidState: DualApplePods.LidState?
        get() = (ble as? DualApplePods)?.caseLidState

    // Microphone
    val isLeftPodMicrophone: Boolean?
        get() = (ble as? HasDualMicrophone)?.isLeftPodMicrophone

    val isRightPodMicrophone: Boolean?
        get() = (ble as? HasDualMicrophone)?.isRightPodMicrophone

    // Icons / labels
    val iconRes: Int get() = ble?.iconRes ?: model.iconRes

    val leftPodIcon: Int?
        get() = (ble as? DualBlePodSnapshot)?.leftPodIcon

    val rightPodIcon: Int?
        get() = (ble as? DualBlePodSnapshot)?.rightPodIcon

    val caseIcon: Int?
        get() = (ble as? HasCase)?.caseIcon

    fun getLabel(context: Context): String = ble?.getLabel(context) ?: model.label

    // Debug
    val rawDataHex: List<String> get() = ble?.rawDataHex ?: emptyList()

    // AAP controls
    val ancMode: AapSetting.AncMode?
        get() = aap?.setting()

    val conversationalAwareness: AapSetting.ConversationalAwareness?
        get() = aap?.setting()

    val toneVolume: AapSetting.ToneVolume?
        get() = aap?.setting()

    val personalizedVolume: AapSetting.PersonalizedVolume?
        get() = aap?.setting()

    val volumeSwipe: AapSetting.VolumeSwipe?
        get() = aap?.setting()

    val ncWithOneAirPod: AapSetting.NcWithOneAirPod?
        get() = aap?.setting()

    val adaptiveAudioNoise: AapSetting.AdaptiveAudioNoise?
        get() = aap?.setting()

    val isAapConnected: Boolean get() = aap != null
}
