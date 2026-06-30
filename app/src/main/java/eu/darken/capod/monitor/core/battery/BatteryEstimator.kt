package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Learns each device's battery drain rate from observed levels over time and turns it into a
 * time-remaining estimate for the earbuds. Rates are learned per ANC mode (drain differs sharply
 * between OFF / ON / Transparency / Adaptive) and persisted via [BatteryDrainStore] so an estimate
 * is available immediately on reconnect.
 *
 * Lifecycle: [monitor] is launched by the foreground monitor service (see
 * `MonitorService.doMonitor`), NOT eagerly in `init`. `DeviceMonitor.devices` starts BLE scanning
 * while it has a subscriber, so a permanent subscription would keep scanning alive forever; gating
 * on the service keeps sampling tied to active monitoring. All mutable state below is touched only
 * from the single `monitor()` collector; [estimates] is the read-only output other components observe.
 */
@Singleton
class BatteryEstimator @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val drainStore: BatteryDrainStore,
    private val timeSource: TimeSource,
) {

    private val _estimates = MutableStateFlow<Map<ProfileId, BatteryEstimate>>(emptyMap())
    val estimates: StateFlow<Map<ProfileId, BatteryEstimate>> = _estimates

    private enum class Slot { LEFT, RIGHT, HEADSET }

    private class SlotHistory {
        private val samples = ArrayDeque<DrainSample>()

        val lastFraction: Float? get() = samples.lastOrNull()?.fraction
        val size: Int get() = samples.size

        fun record(sample: DrainSample) {
            samples.addLast(sample)
            while (samples.size > RING_SIZE) samples.removeFirst()
        }

        fun clear() = samples.clear()

        fun toList(): List<DrainSample> = samples.toList()
    }

    private class DeviceTracker {
        var modeBucket: String = MODE_UNKNOWN
        val slots: Map<Slot, SlotHistory> = Slot.entries.associateWith { SlotHistory() }

        /** Smoothed displayed minutes, per pod. */
        val lastMinutes: MutableMap<Slot, Int> = mutableMapOf()
        var lastUpdateMs: Long? = null

        // Keyed by "<bucket>/<slot>".
        val lastPersistAtMs: MutableMap<String, Long> = mutableMapOf()

        /**
         * Pre-session stored rate captured once per (bucket, slot), so repeated periodic persists
         * during a single session blend against a fixed baseline instead of runaway-converging.
         */
        val sessionBaseline: MutableMap<String, Float?> = mutableMapOf()

        fun clearSlots() = slots.values.forEach { it.clear() }

        fun resetWindow() {
            clearSlots()
            lastMinutes.clear()
            sessionBaseline.clear()
        }
    }

    private val trackers = mutableMapOf<ProfileId, DeviceTracker>()

    fun monitor(): Flow<Unit> = deviceMonitor.devices
        .onEach { devices -> process(devices) }
        .onCompletion {
            // The collector stops with the monitor service; drop session state so the UI can't
            // keep showing a stale estimate and the next session re-seeds from persistence.
            trackers.clear()
            _estimates.value = emptyMap()
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "batteryEstimator" }

    private suspend fun process(devices: List<PodDevice>) {
        // Only profiles with a single, unambiguous live candidate. DeviceMonitor keeps multiple
        // same-profile devices when there's no IRK-verified match, and blending two physical
        // devices' levels would be garbage — skip those.
        val unambiguous = devices
            .filter { it.profileId != null && it.isLive }
            .groupBy { it.profileId!! }
            .filter { (_, group) -> group.size == 1 }
            .mapValues { (_, group) -> group.single() }

        val next = _estimates.value.toMutableMap()
        // Drop estimates for profiles no longer live/unambiguous this emission (offline gating).
        next.keys.retainAll(unambiguous.keys)

        for ((profileId, device) in unambiguous) {
            val estimate = updateTracker(profileId, device)
            if (estimate != null) next[profileId] = estimate else next.remove(profileId)
        }

        _estimates.value = next
    }

    private suspend fun updateTracker(profileId: ProfileId, device: PodDevice): BatteryEstimate? {
        val tracker = trackers.getOrPut(profileId) { DeviceTracker() }
        val nowMs = timeSource.elapsedRealtime()
        val bucket = device.modeBucket()

        // A long gap since the last update means the device was out of range / reconnected — the
        // prior window is stale and must not be extended across the absence.
        val gap = tracker.lastUpdateMs?.let { nowMs - it }
        if (gap != null && gap > STALE_GAP_MS) tracker.resetWindow()
        tracker.lastUpdateMs = nowMs

        // A mode change invalidates the current window — flush what we learned to the OLD bucket,
        // then start fresh so OFF-rate samples never blend into ON-rate.
        if (tracker.modeBucket != bucket) {
            persistFromWindow(profileId, tracker, tracker.modeBucket, nowMs, force = true)
            tracker.resetWindow()
            tracker.modeBucket = bucket
        }

        for (slot in Slot.entries) {
            val history = tracker.slots.getValue(slot)
            val charging = device.liveCharging(slot)
            val fraction = device.liveFraction(slot)

            when {
                charging == true -> { // charging → battery is rising, not draining
                    history.clear()
                    tracker.lastMinutes.remove(slot) // jump breaks continuity → drop this pod's smoothing
                }
                fraction == null -> history.clear() // unavailable reading → just drop this slot's window
                else -> {
                    val last = history.lastFraction
                    when {
                        last == null -> history.record(DrainSample(nowMs, fraction))
                        fraction > last + EPSILON -> { // level went UP (reseat/swap) → reset
                            history.clear()
                            history.record(DrainSample(nowMs, fraction))
                            tracker.lastMinutes.remove(slot)
                        }
                        fraction < last - EPSILON -> history.record(DrainSample(nowMs, fraction)) // a drop
                        else -> Unit // ~unchanged, no new information
                    }
                }
            }
        }

        persistFromWindow(profileId, tracker, bucket, nowMs, force = false)
        return computeEstimate(profileId, tracker, device, bucket)
    }

    /** Computes an independent estimate for each pod (left / right / headset). */
    private fun computeEstimate(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        bucket: String,
    ): BatteryEstimate? {
        val estimate = BatteryEstimate(
            left = slotEstimate(profileId, tracker, device, bucket, Slot.LEFT),
            right = slotEstimate(profileId, tracker, device, bucket, Slot.RIGHT),
            headset = slotEstimate(profileId, tracker, device, bucket, Slot.HEADSET),
        )
        return estimate.takeIf { it.hasAny }
    }

    private fun slotEstimate(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        bucket: String,
        slot: Slot,
    ): BatteryEstimate.Pod? {
        if (device.liveCharging(slot) == true) return null
        val fraction = device.liveFraction(slot) ?: return null

        val liveRate = DrainModel.slopeFractionPerHour(tracker.slots.getValue(slot).toList())
        val rate = liveRate ?: learnedRate(profileId, bucket, slot) ?: return null
        val minutes = DrainModel.minutesRemaining(fraction, rate) ?: return null

        val smoothed = DrainModel.blendMinutes(tracker.lastMinutes[slot], minutes)
        tracker.lastMinutes[slot] = smoothed
        return BatteryEstimate.Pod(
            minutesRemaining = smoothed,
            fractionPerHour = rate,
            isLearned = liveRate == null,
        )
    }

    /**
     * Persists each pod's live drain rate under its (bucket, slot) key, at most once per
     * [PERSIST_INTERVAL_MS] (mirrors the cache's periodic-save cadence) unless [force]d (mode change).
     */
    private suspend fun persistFromWindow(
        profileId: ProfileId,
        tracker: DeviceTracker,
        bucket: String,
        nowMs: Long,
        force: Boolean,
    ) {
        val existing = drainStore.profiles.value[profileId] ?: DrainProfile()
        var rates = existing.rates
        var changed = false

        for (slot in Slot.entries) {
            val history = tracker.slots.getValue(slot)
            val liveRate = DrainModel.slopeFractionPerHour(history.toList()) ?: continue

            val key = rateKey(bucket, slot)
            val lastPersist = tracker.lastPersistAtMs[key]
            if (!force && lastPersist != null && nowMs - lastPersist < PERSIST_INTERVAL_MS) continue
            tracker.lastPersistAtMs[key] = nowMs

            // Blend against the rate stored when this session began, captured once, so a single long
            // session's repeated writes can't dominate prior history by re-blending their own output.
            if (!tracker.sessionBaseline.containsKey(key)) {
                tracker.sessionBaseline[key] = rates[key]?.fractionPerHour
            }
            val blended = DrainModel.blendRate(tracker.sessionBaseline[key], liveRate)
            rates = rates + (key to DrainProfile.LearnedRate(
                fractionPerHour = blended,
                sampleCount = history.size,
                updatedAt = timeSource.now(),
            ))
            changed = true
            log(TAG, VERBOSE) { "Persisting learned rate for $profileId [$key]: ${"%.3f".format(blended)}/hr" }
        }

        if (changed) drainStore.save(profileId, existing.copy(rates = rates))
    }

    private fun learnedRate(profileId: ProfileId, bucket: String, slot: Slot): Float? {
        val profile = drainStore.profiles.value[profileId] ?: return null
        return (profile.rates[rateKey(bucket, slot)] ?: profile.rates[rateKey(MODE_UNKNOWN, slot)])?.fractionPerHour
    }

    private fun rateKey(bucket: String, slot: Slot): String = "$bucket/${slot.name}"

    private fun PodDevice.modeBucket(): String = ancMode?.current?.name ?: MODE_UNKNOWN

    // RAW LIVE extraction — must NOT use device.batteryLeft/isLeftPodCharging (which fall back to
    // cache); learning from a re-stamped stale reading would poison the rate.
    private fun PodDevice.liveFraction(slot: Slot): Float? {
        val value = when (slot) {
            Slot.LEFT -> aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent
            Slot.RIGHT -> aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent
            Slot.HEADSET -> aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent
        }
        return value?.takeIf { isKnownBattery(it) }
    }

    private fun PodDevice.liveCharging(slot: Slot): Boolean? = when (slot) {
        Slot.LEFT -> aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging
        Slot.RIGHT -> aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging
        Slot.HEADSET -> aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged
    }

    companion object {
        private val TAG = logTag("Monitor", "BatteryEstimator")
        private const val RING_SIZE = 32
        private const val EPSILON = 0.001f
        private const val MODE_UNKNOWN = "UNKNOWN"
        private const val PERSIST_INTERVAL_MS = 5 * 60_000L

        /** A gap longer than this between updates means the device was away — reset its window. */
        private const val STALE_GAP_MS = 15 * 60_000L
    }
}
