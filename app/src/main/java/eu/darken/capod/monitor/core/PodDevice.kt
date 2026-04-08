package eu.darken.capod.monitor.core

import android.content.Context
import androidx.compose.runtime.Stable
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.cache.CachedDeviceState
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.devices.HasDualMicrophone
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetectionDual
import java.time.Duration
import java.time.Instant

/**
 * Unified device facade combining BLE scan data and AAP connection data.
 * All properties are dynamically resolved from the best available source.
 * Consumers don't need to know whether data came from BLE or AAP.
 */
@Stable
data class PodDevice(
    val profileId: String?,
    val label: String? = null,
    internal val ble: BlePodSnapshot?,
    internal val aap: AapPodState?,
    internal val cached: CachedDeviceState? = null,
    /** Bonded BR/EDR address from the profile — current source of truth, preferred over cache snapshot. */
    internal val profileAddress: BluetoothAddress? = null,
    /** Pod model from the profile — current source of truth, preferred over cache snapshot. */
    internal val profileModel: PodModel? = null,
    /**
     * Key state derived from the profile's stored IRK/ENC. Used as the source of truth for the
     * badge icons when the device is live — per-scan IRK matching would otherwise evaporate
     * every time the BLE scanner misses the next advertisement batch.
     */
    internal val profileKeyState: BleKeyState = BleKeyState.NONE,
) {
    val model: PodModel get() = ble?.model ?: profileModel ?: cached?.model ?: PodModel.UNKNOWN
    /** Bonded BR/EDR address (from profile). Used for AAP commands. */
    val address: BluetoothAddress? get() = ble?.meta?.profile?.address ?: profileAddress ?: cached?.address
    /** BLE scan address (RPA, rotates). */
    val bleAddress: BluetoothAddress? get() = ble?.address
    val identifier: BlePodSnapshot.Id? get() = ble?.identifier

    /** True when at least one live data source (BLE or AAP) is present. */
    val isLive: Boolean get() = ble != null || aap != null

    // Capabilities from Model.Features
    val hasCase: Boolean get() = model.features.hasCase
    val hasDualPods: Boolean get() = model.features.hasDualPods
    val hasEarDetection: Boolean get() = model.features.hasEarDetection
    val hasAncControl: Boolean get() = model.features.hasAncControl
    val hasDualMicrophone: Boolean get() = ble is HasDualMicrophone

    // Signal / timing
    val seenLastAt: Instant? get() = ble?.seenLastAt ?: cached?.lastSeenAt
    val seenFirstAt: Instant? get() = ble?.seenFirstAt
    val signalQuality: Float
        get() {
            val bleQuality = ble?.signalQuality ?: 0f
            return (bleQuality + computeAapBoost(Instant.now())).coerceAtMost(1f)
        }

    /**
     * Quality value for the signal bar display. Prefers live BLE RSSI; when BLE is absent
     * (common for a device that's actively connected to us via classic BT — we stop receiving
     * its BLE advertisements), falls back to a full-bars constant as long as AAP is READY.
     *
     * Note: we don't try to graduate this off AAP message age. The AirPods only push messages
     * on state changes (battery %, ear detection, setting toggles, user taps). A user quietly
     * listening to music can go minutes without any AAP traffic, which is still a perfectly
     * healthy connection. A quiet channel is not a weak channel.
     */
    val rssiQuality: Float
        get() {
            ble?.rssiQuality?.let { return it }
            val state = aap ?: return 0f
            return if (state.connectionState == AapPodState.ConnectionState.READY) 1.0f else 0f
        }

    val rssi: Int get() = ble?.rssi ?: 0

    /** Snapshot of connection-related state for badges and bars. */
    val connectionState: ConnectionState
        get() = ConnectionState(
            hasBleData = ble != null,
            bleKeyState = bleKeyState,
            isAapConnected = isAapConnected,
            rssiQuality = rssiQuality,
        )

    internal fun computeAapBoost(now: Instant): Float {
        val state = aap ?: return 0f
        if (state.connectionState != AapPodState.ConnectionState.READY) return 0.05f
        val lastMessage = state.lastMessageAt ?: return 0.05f
        val ageSeconds = Duration.between(lastMessage, now).seconds
        return when {
            ageSeconds < 0 -> 0.05f   // future timestamp (defensive)
            ageSeconds < 10 -> 0.15f  // fresh
            ageSeconds < 30 -> 0.10f  // warm
            else -> 0.05f             // stale
        }
    }

    // Battery — AAP preferred, BLE fallback, then cached
    val batteryLeft: Float?
        get() = aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent ?: cached?.left?.percent

    val batteryRight: Float?
        get() = aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent ?: cached?.right?.percent

    val batteryCase: Float?
        get() = aap?.batteryCase ?: (ble as? HasCase)?.batteryCasePercent ?: cached?.case?.percent

    val batteryHeadset: Float?
        get() = aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent ?: cached?.headset?.percent

    /** True when at least one displayed battery value was filled from cache (not live). */
    val isBatteryCached: Boolean
        get() {
            if (cached == null) return false
            val usedLeft = aap?.batteryLeft == null && (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent == null && cached.left != null
            val usedRight = aap?.batteryRight == null && (ble as? DualBlePodSnapshot)?.batteryRightPodPercent == null && cached.right != null
            val usedCase = aap?.batteryCase == null && (ble as? HasCase)?.batteryCasePercent == null && cached.case != null
            val usedHeadset = aap?.batteryHeadset == null && (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent == null && cached.headset != null
            return usedLeft || usedRight || usedCase || usedHeadset
        }

    /** Oldest per-slot timestamp among battery values that fell through to cache. Null if all live. */
    val cachedBatteryAt: Instant?
        get() {
            if (cached == null) return null
            return listOfNotNull(
                cached.left?.updatedAt.takeIf { aap?.batteryLeft == null && (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent == null },
                cached.right?.updatedAt.takeIf { aap?.batteryRight == null && (ble as? DualBlePodSnapshot)?.batteryRightPodPercent == null },
                cached.case?.updatedAt.takeIf { aap?.batteryCase == null && (ble as? HasCase)?.batteryCasePercent == null },
                cached.headset?.updatedAt.takeIf { aap?.batteryHeadset == null && (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent == null },
            ).minOrNull()
        }

    // Charging — AAP preferred, BLE fallback, then cached
    val isLeftPodCharging: Boolean?
        get() = aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging ?: cached?.isLeftCharging

    val isRightPodCharging: Boolean?
        get() = aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging ?: cached?.isRightCharging

    val isCaseCharging: Boolean?
        get() = aap?.isCaseCharging ?: (ble as? HasCase)?.isCaseCharging ?: cached?.isCaseCharging

    val isHeadsetBeingCharged: Boolean?
        get() = aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged ?: cached?.isHeadsetCharging

    // Resolved primary pod: AAP cmd 0x08 preferred, BLE bit 5 fallback.
    private val resolvedPrimaryPod: DualBlePodSnapshot.Pod?
        get() = aap?.aapPrimaryPod?.pod?.let { aapPod ->
            when (aapPod) {
                AapSetting.PrimaryPod.Pod.LEFT -> DualBlePodSnapshot.Pod.LEFT
                AapSetting.PrimaryPod.Pod.RIGHT -> DualBlePodSnapshot.Pod.RIGHT
            }
        } ?: (ble as? DualApplePods)?.primaryPod

    // Ear detection — AAP preferred (lower latency), BLE fallback.
    // AAP reports primary/secondary; resolvedPrimaryPod tells us which physical pod is primary.
    val isLeftInEar: Boolean?
        get() {
            val earDetection = aap?.aapEarDetection
            val primary = resolvedPrimaryPod
            if (earDetection != null && primary != null) {
                return if (primary == DualBlePodSnapshot.Pod.LEFT) {
                    earDetection.primaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
                } else {
                    earDetection.secondaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
                }
            }
            return (ble as? HasEarDetectionDual)?.isLeftPodInEar
        }

    val isRightInEar: Boolean?
        get() {
            val earDetection = aap?.aapEarDetection
            val primary = resolvedPrimaryPod
            if (earDetection != null && primary != null) {
                return if (primary == DualBlePodSnapshot.Pod.RIGHT) {
                    earDetection.primaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
                } else {
                    earDetection.secondaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
                }
            }
            return (ble as? HasEarDetectionDual)?.isRightPodInEar
        }

    val isBeingWorn: Boolean?
        get() {
            val earDetection = aap?.aapEarDetection
            if (earDetection != null) {
                return earDetection.primaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
                    && earDetection.secondaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
            }
            return (ble as? HasEarDetection)?.isBeingWorn
        }

    val isEitherPodInEar: Boolean?
        get() = aap?.isEitherPodInEar ?: (ble as? HasEarDetectionDual)?.isEitherPodInEar

    val caseLidState: DualApplePods.LidState?
        get() = (ble as? DualApplePods)?.caseLidState

    // Microphone — AAP preferred (from primary pod identity), BLE fallback
    val isLeftPodMicrophone: Boolean?
        get() {
            aap?.aapPrimaryPod?.let { return it.pod == AapSetting.PrimaryPod.Pod.LEFT }
            return (ble as? HasDualMicrophone)?.isLeftPodMicrophone
        }

    val isRightPodMicrophone: Boolean?
        get() {
            aap?.aapPrimaryPod?.let { return it.pod == AapSetting.PrimaryPod.Pod.RIGHT }
            return (ble as? HasDualMicrophone)?.isRightPodMicrophone
        }

    // Icons / labels
    val iconRes: Int get() = ble?.iconRes ?: model.iconRes

    val leftPodIcon: Int
        get() = (ble as? DualBlePodSnapshot)?.leftPodIcon
            ?: model.leftPodIconRes
            ?: R.drawable.device_airpods_gen1_left

    val rightPodIcon: Int
        get() = (ble as? DualBlePodSnapshot)?.rightPodIcon
            ?: model.rightPodIconRes
            ?: R.drawable.device_airpods_gen1_right

    val caseIcon: Int
        get() = (ble as? HasCase)?.caseIcon
            ?: model.caseIconRes
            ?: R.drawable.device_airpods_gen1_case

    fun getLabel(context: Context): String = ble?.getLabel(context) ?: model.label

    // Debug
    val rawDataHex: List<String> get() = ble?.rawDataHex ?: emptyList()

    // AAP controls
    val ancMode: AapSetting.AncMode?
        get() = aap?.setting()

    val pendingAncMode: AapSetting.AncMode.Value?
        get() = aap?.pendingAncMode

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

    val pressSpeed: AapSetting.PressSpeed?
        get() = aap?.setting()

    val pressHoldDuration: AapSetting.PressHoldDuration?
        get() = aap?.setting()

    val volumeSwipeLength: AapSetting.VolumeSwipeLength?
        get() = aap?.setting()

    val endCallMuteMic: AapSetting.EndCallMuteMic?
        get() = aap?.setting()

    val microphoneMode: AapSetting.MicrophoneMode?
        get() = aap?.setting()

    val earDetectionEnabled: AapSetting.EarDetectionEnabled?
        get() = aap?.setting()

    val listeningModeCycle: AapSetting.ListeningModeCycle?
        get() = aap?.setting()

    val allowOffOption: AapSetting.AllowOffOption?
        get() = aap?.setting()

    val stemConfig: AapSetting.StemConfig?
        get() = aap?.setting()

    val sleepDetection: AapSetting.SleepDetection?
        get() = aap?.setting()

    val connectedDevices: AapSetting.ConnectedDevices?
        get() = aap?.setting()

    val audioSource: AapSetting.AudioSource?
        get() = aap?.setting()

    val eqBands: AapSetting.EqBands?
        get() = aap?.setting()

    val deviceInfo: AapDeviceInfo?
        get() = aap?.deviceInfo ?: cached?.deviceInfo

    val isAapConnected: Boolean get() = aap != null

    val isAapReady: Boolean get() = aap?.connectionState == AapPodState.ConnectionState.READY

    /**
     * Badge key state for the overview card. Profile-stored keys are the source of truth while
     * the device is live: a profile that has an IRK (and optionally ENC) keeps showing those
     * icons whether the current scan happens to include a fresh BLE advertisement or not. For
     * anonymous BLE pods (no profile), we still derive the state from the live scan result.
     */
    val bleKeyState: BleKeyState
        get() {
            if (!isLive) return BleKeyState.NONE
            if (profileKeyState != BleKeyState.NONE) return profileKeyState
            val applePod = ble as? ApplePods ?: return BleKeyState.NONE
            if (!applePod.meta.isIRKMatch) return BleKeyState.NONE
            return if (applePod.payload.private != null) BleKeyState.IRK_AND_ENCRYPTED else BleKeyState.IRK_ONLY
        }
}

enum class BleKeyState { NONE, IRK_ONLY, IRK_AND_ENCRYPTED }

data class ConnectionState(
    val hasBleData: Boolean,
    val bleKeyState: BleKeyState,
    val isAapConnected: Boolean,
    val rssiQuality: Float,
)
