package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    /** Which transport a battery reading came from — AAP is 1% granularity, BLE 10%. */
    private enum class DataSource { AAP, BLE }

    private class SlotHistory {
        enum class Direction { DRAIN, CHARGE }

        var direction: Direction = Direction.DRAIN
            private set
        private var source: DataSource? = null
        private val samples = ArrayDeque<DrainSample>()

        val lastFraction: Float? get() = samples.lastOrNull()?.fraction
        val size: Int get() = samples.size

        /**
         * Keeps the window only while it still describes the same thing: a direction flip
         * (drain <-> charge) obviously invalidates it, and so does an AAP <-> BLE source change —
         * the granularity jump (1% vs 10%) between transports would read as a fake level step.
         */
        fun realign(direction: Direction, source: DataSource) {
            if (this.direction != direction || this.source != source) samples.clear()
            this.direction = direction
            this.source = source
        }

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

        /** When each slot's level last visibly ROSE while charging — drives stall suppression. */
        val lastRiseMs: MutableMap<Slot, Long> = mutableMapOf()

        // Keyed by "<bucket>/<slot>" for drain rates, "CHARGE/<slot>" for charge rates.
        val lastPersistAtMs: MutableMap<String, Long> = mutableMapOf()

        /**
         * Pre-session stored rate captured once per (bucket, slot), so repeated periodic persists
         * during a single session blend against a fixed baseline instead of runaway-converging.
         * [sessionBaselineCounts] captures the matching pre-session updateCount, so repeated persists
         * within one session count as ONE update, not many.
         */
        val sessionBaseline: MutableMap<String, Float?> = mutableMapOf()
        val sessionBaselineCounts: MutableMap<String, Int> = mutableMapOf()

        fun clearSlots() = slots.values.forEach { it.clear() }

        fun resetWindow() {
            clearSlots()
            lastMinutes.clear()
            lastRiseMs.clear()
            sessionBaseline.clear()
            sessionBaselineCounts.clear()
        }
    }

    private val trackers = mutableMapOf<ProfileId, DeviceTracker>()

    // Serialises process() against reset() so an in-flight persist can't resurrect a just-wiped rate.
    private val mutex = Mutex()

    /**
     * Wipes ALL learned state for [profileId] — the in-memory tracker, the current estimate, and the
     * persisted rates — under the same lock as [process], so it's atomic w.r.t. sampling/persisting.
     * The next live emission re-seeds the device from its model rating. Safe to call when the monitor
     * isn't running (trackers/estimates are already empty; only the store delete has effect).
     */
    suspend fun reset(profileId: ProfileId) = withContext(NonCancellable) {
        // NonCancellable so a cancelled caller (e.g. the settings screen closing) can't leave the
        // wipe half-applied — memory cleared but persisted rates surviving to resurrect later.
        mutex.withLock {
            log(TAG) { "reset($profileId)" }
            trackers.remove(profileId)
            _estimates.value = _estimates.value - profileId
            drainStore.delete(profileId)
        }
    }

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

    private suspend fun process(devices: List<PodDevice>) = mutex.withLock {
        // Only profiles with a single, unambiguous live candidate that has the estimate enabled.
        // DeviceMonitor keeps multiple same-profile devices when there's no IRK-verified match, and
        // blending two physical devices' levels would be garbage — skip those. A device with the
        // feature disabled is skipped entirely (not sampled, not persisted).
        val unambiguous = devices
            .filter { it.profileId != null && it.isLive && it.batteryEstimateEnabled }
            .groupBy { it.profileId!! }
            .filter { (_, group) -> group.size == 1 }
            .mapValues { (_, group) -> group.single() }

        val next = _estimates.value.toMutableMap()
        // Drop estimates for profiles no longer live/unambiguous/enabled this emission (offline gating).
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
            persistFromWindow(profileId, tracker, device, tracker.modeBucket, nowMs, force = true)
            tracker.resetWindow()
            tracker.modeBucket = bucket
        }

        for (slot in Slot.entries) {
            val history = tracker.slots.getValue(slot)
            val charging = device.liveCharging(slot)
            val reading = device.liveReading(slot)

            when {
                reading == null -> { // unavailable reading → just drop this slot's window
                    history.clear()
                    tracker.lastRiseMs.remove(slot)
                    // A charging jump breaks discharge continuity even when the level is unreadable.
                    if (charging == true) tracker.lastMinutes.remove(slot)
                }
                charging == true -> { // battery rising → sample the CHARGE, never a drain
                    val (fraction, source) = reading
                    tracker.lastMinutes.remove(slot) // jump breaks continuity → drop discharge smoothing
                    if (device.liveChargingOptimized(slot)) {
                        // Optimized Battery Charging parks the level below full for hours while
                        // still flagged charging — fitting that plateau would learn garbage.
                        history.clear()
                        tracker.lastRiseMs.remove(slot)
                    } else {
                        history.realign(SlotHistory.Direction.CHARGE, source)
                        val last = history.lastFraction
                        when {
                            last == null -> { // charge session starts (or resumes) for this slot
                                history.record(DrainSample(nowMs, fraction))
                                tracker.lastRiseMs[slot] = nowMs
                            }
                            fraction > last + EPSILON -> {
                                history.record(DrainSample(nowMs, fraction))
                                tracker.lastRiseMs[slot] = nowMs
                            }
                            fraction < last - EPSILON -> { // level DROPPED while charging → reseat/swap
                                history.clear()
                                history.record(DrainSample(nowMs, fraction))
                                tracker.lastRiseMs[slot] = nowMs
                            }
                            else -> Unit // ~unchanged; stall detection judges the silence
                        }
                    }
                }
                else -> { // draining (or unknown charging state — treated as draining, as before)
                    val (fraction, source) = reading
                    tracker.lastRiseMs.remove(slot)
                    history.realign(SlotHistory.Direction.DRAIN, source)
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

        persistFromWindow(profileId, tracker, device, bucket, nowMs, force = false)
        return computeEstimate(profileId, tracker, device, bucket, nowMs)
    }

    /** Computes an independent estimate for each pod (left / right / headset). */
    private fun computeEstimate(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        bucket: String,
        nowMs: Long,
    ): BatteryEstimate? {
        val estimate = BatteryEstimate(
            left = slotEstimate(profileId, tracker, device, bucket, Slot.LEFT, nowMs),
            right = slotEstimate(profileId, tracker, device, bucket, Slot.RIGHT, nowMs),
            headset = slotEstimate(profileId, tracker, device, bucket, Slot.HEADSET, nowMs),
        )
        return estimate.takeIf { it.hasAny }
    }

    private fun slotEstimate(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        bucket: String,
        slot: Slot,
        nowMs: Long,
    ): BatteryEstimate.Pod? {
        val fraction = device.liveFraction(slot) ?: return null

        val spec = device.specRate(bucket)
        // While charging the battery is rising, so there's no live drain to fit — project the runtime
        // "if used now" from the learned rate or the model rating instead. Live sampling is skipped
        // here (and updateTracker already clears the window while charging), so a rising level is
        // never learned as a drain — we only surface a projection so the estimate stays visible in
        // the case, climbing as the pod charges.
        val charging = device.liveCharging(slot) == true
        val live = if (charging) {
            null
        } else {
            DrainModel.slopeFractionPerHour(tracker.slots.getValue(slot).toList())
                ?.takeIf { plausibleForModel(it, spec) }
        }
        val learned = learnedRate(profileId, device, bucket, slot)
        val displayRate = live ?: learned ?: spec ?: return null

        // Apple's rating is a hard ceiling on remaining life (a floor on the drain rate) for every
        // source: a degraded battery only ever drains FASTER than new-condition spec, so a measured
        // rate normally wins — spec only bites when a source implausibly implies MORE life than Apple.
        val effectiveRate = spec?.let { maxOf(displayRate, it) } ?: displayRate

        val minutes = DrainModel.minutesRemaining(fraction, effectiveRate) ?: return null
        // Smooth against the last displayed value — but while charging neither read nor write that
        // state: the in-case projection is shown raw and must not pollute the discharge history, else
        // the first estimate after undocking would blend against a stale charging projection.
        var smoothed = DrainModel.blendMinutes(if (charging) null else tracker.lastMinutes[slot], minutes)
        // Smoothing lags, so just after a drop it can sit above the ceiling — clamp the shown value to
        // the spec cap too, so we never DISPLAY more life than Apple rates even mid-transition.
        spec?.let { DrainModel.minutesRemaining(fraction, it) }?.let { smoothed = minOf(smoothed, it) }
        if (!charging) tracker.lastMinutes[slot] = smoothed

        val source = when {
            live != null -> BatteryEstimate.Source.LIVE
            learned != null -> BatteryEstimate.Source.LEARNED
            else -> BatteryEstimate.Source.SPEC
        }
        return BatteryEstimate.Pod(
            minutesRemaining = smoothed,
            fractionPerHour = effectiveRate,
            source = source,
            minutesUntilCharged = if (charging) chargeEstimate(profileId, tracker, device, slot, fraction, nowMs) else null,
        )
    }

    /**
     * Minutes until [slot] is full, or null when no usable charge rate exists, the pod is in an
     * Optimized Battery Charging hold, or the level has sat still longer than one visible step
     * should take (stall — trickle phase or an unreported hold; a linear ETA would just freeze).
     */
    private fun chargeEstimate(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        slot: Slot,
        fraction: Float,
        nowMs: Long,
    ): Int? {
        if (device.liveChargingOptimized(slot)) return null // held below full — an ETA would mislead
        val history = tracker.slots.getValue(slot)
        val live = if (history.direction == SlotHistory.Direction.CHARGE) {
            DrainModel.chargeSlopeFractionPerHour(history.toList())
        } else null
        // Rate preference mirrors the drain side: measured, then learned, then Apple's published
        // quick-charge claim ("5 minutes in the case = ~1 hour of listening") — so an ETA exists
        // even on the very first charge.
        val rate = live
            ?: learnedChargeRate(profileId, device, slot)
            ?: device.model.batterySpec?.chargeFractionPerHour
            ?: return null

        val lastRise = tracker.lastRiseMs[slot] ?: return null
        val step = if (device.liveReading(slot)?.second == DataSource.AAP) STEP_AAP else STEP_BLE
        if (nowMs - lastRise > DrainModel.chargeStallThresholdMs(rate, step)) return null

        return DrainModel.minutesUntilFull(fraction, rate)
    }

    /**
     * Persists each pod's live drain or charge rate (whichever direction its window currently
     * tracks), at most once per [PERSIST_INTERVAL_MS] (mirrors the cache's periodic-save cadence)
     * unless [force]d (mode change). Drain rates are keyed per (bucket, slot); charge rates per slot
     * only — the ANC mode doesn't apply inside the case.
     */
    private suspend fun persistFromWindow(
        profileId: ProfileId,
        tracker: DeviceTracker,
        device: PodDevice,
        bucket: String,
        nowMs: Long,
        force: Boolean,
    ) {
        // A profile tagged with DIFFERENT hardware means the user re-pointed it — its rates don't
        // describe this device, so learning starts over instead of blending into foreign history.
        val existing = drainStore.profiles.value[profileId]?.takeIf { it.matchesModel(device.model) }
            ?: DrainProfile()
        val spec = device.specRate(bucket)
        var rates = existing.rates
        var chargeRates = existing.chargeRates
        var changed = false

        for (slot in Slot.entries) {
            val history = tracker.slots.getValue(slot)
            val isCharge = history.direction == SlotHistory.Direction.CHARGE
            // Same model-aware plausibility gate as display, so an implausibly fast fit isn't learned.
            val liveRate = if (isCharge) {
                DrainModel.chargeSlopeFractionPerHour(history.toList())
            } else {
                DrainModel.slopeFractionPerHour(history.toList())?.takeIf { plausibleForModel(it, spec) }
            } ?: continue

            val key = if (isCharge) chargeRateKey(slot) else rateKey(bucket, slot)
            val lastPersist = tracker.lastPersistAtMs[key]
            if (!force && lastPersist != null && nowMs - lastPersist < PERSIST_INTERVAL_MS) continue
            tracker.lastPersistAtMs[key] = nowMs

            // Blend against the rate stored when this session began, captured once, so a single long
            // session's repeated writes can't dominate prior history by re-blending their own output.
            // The captured updateCount keeps a whole session counting as ONE accumulated update.
            val stored = if (isCharge) chargeRates[slot.name] else rates[key]
            if (!tracker.sessionBaseline.containsKey(key)) {
                tracker.sessionBaseline[key] = stored?.fractionPerHour
                tracker.sessionBaselineCounts[key] = stored?.updateCount ?: 0
            }
            val learned = DrainProfile.LearnedRate(
                fractionPerHour = DrainModel.blendRate(tracker.sessionBaseline[key], liveRate),
                sampleCount = history.size,
                updateCount = (tracker.sessionBaselineCounts[key] ?: 0) + 1,
                updatedAt = timeSource.now(),
            )
            if (isCharge) chargeRates = chargeRates + (slot.name to learned) else rates = rates + (key to learned)
            changed = true
            log(TAG, VERBOSE) { "Persisting learned rate for $profileId [$key]: ${"%.3f".format(learned.fractionPerHour)}/hr" }
        }

        if (changed) {
            drainStore.save(
                profileId,
                existing.copy(model = device.model.name, rates = rates, chargeRates = chargeRates),
            )
        }
    }

    private fun learnedRate(profileId: ProfileId, device: PodDevice, bucket: String, slot: Slot): Float? {
        val profile = storedProfileFor(profileId, device) ?: return null
        return (profile.rates[rateKey(bucket, slot)] ?: profile.rates[rateKey(MODE_UNKNOWN, slot)])?.fractionPerHour
    }

    private fun learnedChargeRate(profileId: ProfileId, device: PodDevice, slot: Slot): Float? =
        storedProfileFor(profileId, device)?.chargeRates[slot.name]?.fractionPerHour

    /** The stored profile, ignored entirely when its rates were learned on different hardware. */
    private fun storedProfileFor(profileId: ProfileId, device: PodDevice): DrainProfile? =
        drainStore.profiles.value[profileId]?.takeIf { it.matchesModel(device.model) }

    private fun rateKey(bucket: String, slot: Slot): String = "$bucket/${slot.name}"

    /** Session-state key for charge windows — namespaced so it can't collide with an ANC bucket. */
    private fun chargeRateKey(slot: Slot): String = "CHARGE/${slot.name}"

    private fun PodDevice.modeBucket(): String = ancMode?.current?.name ?: MODE_UNKNOWN

    /**
     * Apple's rated drain (fraction/hour) for this model in the given ANC bucket, or null when the
     * model has no published rating (Beats / unknown). Used to seed an estimate before any drain is
     * observed and as the upper bound on remaining life. When the mode isn't known yet (BLE-only, or
     * a just-connected AAP session) the shorter of the two ratings is used, so an unknown mode can't
     * over-promise.
     */
    private fun PodDevice.specRate(bucket: String): Float? {
        val spec = model.batterySpec ?: return null
        val on = spec.listeningHoursAncOn
        val off = spec.listeningHoursAncOff
        val hours = when (bucket) {
            AapSetting.AncMode.Value.OFF.name -> off ?: on
            MODE_UNKNOWN -> listOfNotNull(on, off).minOrNull()
            else -> on ?: off // ON / TRANSPARENCY / ADAPTIVE
        } ?: return null
        return if (hours.isFinite() && hours > 0f) 1f / hours else null
    }

    /**
     * A measured rate is trusted only when it isn't absurdly faster than the model's rated drain. The
     * slow side is intentionally NOT bounded: a genuinely gentle drain is real data worth learning,
     * and the spec ceiling already stops a slow rate from over-reporting on screen.
     */
    private fun plausibleForModel(rate: Float, spec: Float?): Boolean =
        spec == null || rate <= spec * SPEC_BAND_MAX

    // RAW LIVE extraction — must NOT use device.batteryLeft/isLeftPodCharging (which fall back to
    // cache); learning from a re-stamped stale reading would poison the rate.
    private fun PodDevice.liveFraction(slot: Slot): Float? = liveReading(slot)?.first

    /**
     * The slot's live battery fraction plus which transport reported it. The source matters because
     * the two granularities (AAP 1%, BLE 10%) can't share a fit window — see [SlotHistory.realign].
     */
    private fun PodDevice.liveReading(slot: Slot): Pair<Float, DataSource>? {
        val aapValue = when (slot) {
            Slot.LEFT -> aap?.batteryLeft
            Slot.RIGHT -> aap?.batteryRight
            Slot.HEADSET -> aap?.batteryHeadset
        }
        val (value, source) = when {
            aapValue != null -> aapValue to DataSource.AAP
            else -> when (slot) {
                Slot.LEFT -> (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent
                Slot.RIGHT -> (ble as? DualBlePodSnapshot)?.batteryRightPodPercent
                Slot.HEADSET -> (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent
            }?.let { it to DataSource.BLE } ?: return null
        }
        // coerce defends against a malformed >1 reading, which would otherwise beat the full-charge spec.
        return value.takeIf { isKnownBattery(it) }?.coerceIn(0f, 1f)?.let { it to source }
    }

    private fun PodDevice.liveCharging(slot: Slot): Boolean? = when (slot) {
        Slot.LEFT -> aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging
        Slot.RIGHT -> aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging
        Slot.HEADSET -> aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged
    }

    /** Only AAP reports the Optimized Battery Charging hold; BLE can't distinguish it. */
    private fun PodDevice.liveChargingOptimized(slot: Slot): Boolean = when (slot) {
        Slot.LEFT -> aap?.leftChargingState
        Slot.RIGHT -> aap?.rightChargingState
        Slot.HEADSET -> aap?.headsetChargingState
    } == AapPodState.ChargingState.CHARGING_OPTIMIZED

    companion object {
        private val TAG = logTag("Monitor", "BatteryEstimator")
        private const val RING_SIZE = 32
        private const val EPSILON = 0.001f
        private const val MODE_UNKNOWN = DrainProfile.BUCKET_UNKNOWN
        private const val PERSIST_INTERVAL_MS = 5 * 60_000L

        /** Visible battery step per transport — feeds the granularity-aware stall threshold. */
        private const val STEP_AAP = 0.01f
        private const val STEP_BLE = 0.10f

        /** A measured rate above this multiple of the model's rated drain is rejected as implausible. */
        private const val SPEC_BAND_MAX = 4f

        /** A gap longer than this between updates means the device was away — reset its window. */
        private const val STALE_GAP_MS = 15 * 60_000L
    }
}
