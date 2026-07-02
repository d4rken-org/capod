package eu.darken.capod.monitor.core.battery

import android.media.AudioManager
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
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
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
    private val audioManager: AudioManager,
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
        fun matches(direction: Direction, source: DataSource): Boolean =
            this.direction == direction && this.source == source

        fun realign(direction: Direction, source: DataSource) {
            if (!matches(direction, source)) samples.clear()
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

        /**
         * Parallel drain windows fed ONLY while the pod is worn, audio is playing, and this device
         * is the system's audio sink — pure listening segments, the basis for battery health.
         */
        val listeningSlots: Map<Slot, SlotHistory> = Slot.entries.associateWith { SlotHistory() }

        /** Fit + sample count of a just-closed listening segment, persisted on the next pass. */
        val pendingListeningFits: MutableMap<Slot, Pair<Float, Int>> = mutableMapOf()

        /** CASE charge window — kept out of the pod slot maps (different gating, no drain side). */
        val caseHistory: SlotHistory = SlotHistory()
        var caseLastRiseMs: Long? = null

        /** Open docked-discharge window for the case transfer observation. */
        var transferWindow: TransferWindow? = null

        /** A closed window's health-corrected transfer ratio, persisted on the next pass. */
        var pendingTransferRatio: Float? = null

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
            listeningSlots.values.forEach { it.clear() }
            pendingListeningFits.clear()
            caseHistory.clear()
            caseLastRiseMs = null
            transferWindow = null
            pendingTransferRatio = null
            lastMinutes.clear()
            lastRiseMs.clear()
            sessionBaseline.clear()
            sessionBaselineCounts.clear()
        }
    }

    /** Snapshot state for one open case transfer window (docked pods drawing from the case). */
    private class TransferWindow(
        val caseStart: Float,
        var lastCase: Float,
        /** Fraction gained per pod — kept per slot so each pod's own health can correct its share. */
        val slotGains: MutableMap<Slot, Float> = mutableMapOf(),
        val lastPodFractions: MutableMap<Slot, Float>,
    )

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

        // Sampled once per emission — the gate for health-grade "listening" segments.
        val musicActive = audioManager.isMusicActive

        for ((profileId, device) in unambiguous) {
            val estimate = updateTracker(profileId, device, musicActive)
            if (estimate != null) next[profileId] = estimate else next.remove(profileId)
        }

        _estimates.value = next
    }

    private suspend fun updateTracker(profileId: ProfileId, device: PodDevice, musicActive: Boolean): BatteryEstimate? {
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

            // Health-grade listening window: only pure segments count — the pod worn, audio playing,
            // and this device the audio sink (isMusicActive alone would count phone-speaker
            // playback). The moment the gate breaks, the finished segment's fit is captured for
            // persistence and the window cleared; mixed idle/listening samples would flatten the
            // slope and re-dilute health.
            val listening = charging != true && reading != null &&
                musicActive && device.isSystemConnected && device.wornForSlot(slot)
            val listeningHistory = tracker.listeningSlots.getValue(slot)
            if (listening) {
                val (fraction, source) = reading!!
                // An AAP<->BLE flip mid-listening still ends a PURE segment — flush it rather
                // than letting realign silently discard it.
                if (!listeningHistory.matches(SlotHistory.Direction.DRAIN, source)) {
                    captureListeningSegment(tracker, slot)
                }
                listeningHistory.realign(SlotHistory.Direction.DRAIN, source)
                val last = listeningHistory.lastFraction
                when {
                    last == null -> listeningHistory.record(DrainSample(nowMs, fraction))
                    fraction > last + EPSILON -> { // reseat mid-listening → fresh segment
                        listeningHistory.clear()
                        listeningHistory.record(DrainSample(nowMs, fraction))
                    }
                    fraction < last - EPSILON -> listeningHistory.record(DrainSample(nowMs, fraction))
                    else -> Unit
                }
            } else {
                captureListeningSegment(tracker, slot)
            }
        }

        updateCaseTracker(profileId, tracker, device, nowMs)

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
            caseMinutesUntilCharged = caseChargeEstimate(profileId, tracker, device, nowMs),
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
        val ring = if (history.direction == SlotHistory.Direction.CHARGE) history.toList() else emptyList()
        val liveScalar = if (ring.isNotEmpty()) DrainModel.chargeSlopeFractionPerHour(ring) else null
        val learnedScalar = learnedChargeRate(profileId, device, slot.name)
        val specRate = device.model.batterySpec?.chargeFractionPerHour

        // Per band: this session's in-band fit, then the learned band rate, then the scalar
        // fallbacks, then Apple's quick-charge claim with the band's taper haircut (the claim
        // measures the bulk phase; scalars already average what was actually observed).
        fun rateFor(band: DrainModel.ChargeBand): Float? =
            (if (ring.isNotEmpty()) DrainModel.chargeBandSlopeFractionPerHour(ring, band) else null)
                ?: learnedChargeBand(profileId, device, slot.name, band)
                ?: liveScalar
                ?: learnedScalar
                ?: specRate?.let { it * band.specMultiplier }

        val currentBand = DrainModel.ChargeBand.entries.firstOrNull { fraction < it.to }
            ?: DrainModel.ChargeBand.TRICKLE
        val stallRate = rateFor(currentBand) ?: return null
        val lastRise = tracker.lastRiseMs[slot] ?: return null
        val step = if (device.liveReading(slot)?.second == DataSource.AAP) STEP_AAP else STEP_BLE
        if (nowMs - lastRise > DrainModel.chargeStallThresholdMs(stallRate, step)) return null

        return DrainModel.minutesUntilFull(fraction, ::rateFor)
    }

    private fun learnedChargeBand(
        profileId: ProfileId,
        device: PodDevice,
        slotName: String,
        band: DrainModel.ChargeBand,
    ): Float? = storedProfileFor(profileId, device)?.chargeBands[slotName]?.get(band.name)?.fractionPerHour

    // ---- CASE (experimental) ----
    // The case only reports genuine data while a pod is docked, and its discharge is idle-then-burst
    // (recharging pods) — so it gets a charge ETA and a transfer-efficiency health, never an hourly
    // drain rate.

    private fun updateCaseTracker(profileId: ProfileId, tracker: DeviceTracker, device: PodDevice, nowMs: Long) {
        val reading = device.caseReading()
        val charging = device.caseCharging()
        val history = tracker.caseHistory

        when {
            reading == null || charging != true -> { // no LIVE data, or not charging → no charge window
                history.clear()
                tracker.caseLastRiseMs = null
            }
            else -> {
                val (fraction, source) = reading
                history.realign(SlotHistory.Direction.CHARGE, source)
                val last = history.lastFraction
                when {
                    last == null -> {
                        history.record(DrainSample(nowMs, fraction))
                        tracker.caseLastRiseMs = nowMs
                    }
                    fraction > last + EPSILON -> {
                        history.record(DrainSample(nowMs, fraction))
                        tracker.caseLastRiseMs = nowMs
                    }
                    fraction < last - EPSILON -> { // dropped while charging → restart window
                        history.clear()
                        history.record(DrainSample(nowMs, fraction))
                        tracker.caseLastRiseMs = nowMs
                    }
                    else -> Unit
                }
            }
        }

        updateTransferWindow(profileId, tracker, device)
    }

    /**
     * Tracks a docked-discharge window: the case (live via AAP only — 1% granularity; BLE's 10%
     * steps are too coarse for a health-grade ratio) spending its battery into charging pods while
     * unplugged. On close, the observed transfer ratio (summed pod fraction gained per case
     * fraction spent) is queued for persistence — the basis of the case battery health.
     */
    private fun updateTransferWindow(profileId: ProfileId, tracker: DeviceTracker, device: PodDevice) {
        val aap = device.aap?.takeIf { it.caseIsLive }
        val caseFraction = aap?.batteryCase?.takeIf { isKnownBattery(it) }?.coerceIn(0f, 1f)
        val caseCharging = aap?.caseChargingState == AapPodState.ChargingState.CHARGING
        // Pod inputs come from the SAME AAP state as the case — never the BLE fallback, whose 10%
        // steps would inject coarse jumps into a health-grade ratio.
        fun podFraction(slot: Slot): Float? = when (slot) {
            Slot.LEFT -> aap?.batteryLeft
            Slot.RIGHT -> aap?.batteryRight
            else -> null
        }?.takeIf { isKnownBattery(it) }?.coerceIn(0f, 1f)

        val chargingPods = listOf(Slot.LEFT, Slot.RIGHT).filter { slot ->
            when (slot) {
                Slot.LEFT -> aap?.isLeftCharging == true
                Slot.RIGHT -> aap?.isRightCharging == true
                else -> false
            }
        }

        if (caseFraction == null || caseCharging || chargingPods.isEmpty()) {
            closeTransferWindow(profileId, tracker, device)
            return
        }
        val window = tracker.transferWindow
        if (window == null) {
            tracker.transferWindow = TransferWindow(
                caseStart = caseFraction,
                lastCase = caseFraction,
                lastPodFractions = chargingPods
                    .mapNotNull { slot -> podFraction(slot)?.let { slot to it } }
                    .toMap(mutableMapOf()),
            )
            return
        }
        if (caseFraction > window.lastCase + EPSILON) { // case rose while "unplugged" → untrustworthy
            tracker.transferWindow = null
            return
        }
        window.lastCase = caseFraction
        for (slot in chargingPods) {
            val fraction = podFraction(slot) ?: continue
            val previous = window.lastPodFractions[slot]
            when {
                previous == null -> Unit // pod joined mid-window; gains count from here on
                fraction < previous - EPSILON -> { // pod level dropped mid-charge (reseat) → discard
                    tracker.transferWindow = null
                    return
                }
                fraction > previous + EPSILON ->
                    window.slotGains[slot] = (window.slotGains[slot] ?: 0f) + (fraction - previous)
            }
            window.lastPodFractions[slot] = fraction
        }
    }

    private fun closeTransferWindow(profileId: ProfileId, tracker: DeviceTracker, device: PodDevice) {
        val window = tracker.transferWindow ?: return
        tracker.transferWindow = null
        val caseDrop = window.caseStart - window.lastCase
        val totalGain = window.slotGains.values.sum()
        if (caseDrop < MIN_TRANSFER_CASE_DROP || totalGain <= 0f) return

        // Degraded pods gain percent FASTER than healthy ones (less capacity behind each percent),
        // which would flatter the case — scale each pod's own share by ITS health when known.
        val podHealth = BatteryHealth.estimate(storedProfileFor(profileId, device), device.model)
        val correctedGain = window.slotGains.entries.sumOf { (slot, gain) ->
            val health = when (slot) {
                Slot.LEFT -> podHealth?.left
                Slot.RIGHT -> podHealth?.right
                else -> null
            }
            (gain * ((health ?: 100) / 100f)).toDouble()
        }.toFloat()

        val ratio = correctedGain / caseDrop
        tracker.pendingTransferRatio = ratio
            .takeIf { it.isFinite() && it in TRANSFER_RATIO_MIN..TRANSFER_RATIO_MAX }
        log(TAG, VERBOSE) { "Transfer window closed: drop=$caseDrop gain=$totalGain ratio=$ratio" }
    }

    /** Minutes until the case is full — live/learned rates only; Apple publishes no case charge spec. */
    private fun caseChargeEstimate(profileId: ProfileId, tracker: DeviceTracker, device: PodDevice, nowMs: Long): Int? {
        if (device.caseCharging() != true) return null
        val (fraction, source) = device.caseReading() ?: return null
        val history = tracker.caseHistory
        val ring = if (history.direction == SlotHistory.Direction.CHARGE) history.toList() else emptyList()
        val liveScalar = if (ring.isNotEmpty()) DrainModel.chargeSlopeFractionPerHour(ring) else null
        val learnedScalar = learnedChargeRate(profileId, device, CASE_KEY)

        fun rateFor(band: DrainModel.ChargeBand): Float? =
            (if (ring.isNotEmpty()) DrainModel.chargeBandSlopeFractionPerHour(ring, band) else null)
                ?: learnedChargeBand(profileId, device, CASE_KEY, band)
                ?: liveScalar
                ?: learnedScalar

        val currentBand = DrainModel.ChargeBand.entries.firstOrNull { fraction < it.to }
            ?: DrainModel.ChargeBand.TRICKLE
        val stallRate = rateFor(currentBand) ?: return null
        val lastRise = tracker.caseLastRiseMs ?: return null
        val step = if (source == DataSource.AAP) STEP_AAP else STEP_BLE
        if (nowMs - lastRise > DrainModel.chargeStallThresholdMs(stallRate, step)) return null

        return DrainModel.minutesUntilFull(fraction, ::rateFor)
    }

    /** LIVE case reading only — both transports silently freeze the last value once pods undock. */
    private fun PodDevice.caseReading(): Pair<Float, DataSource>? {
        val aapValue = aap?.takeIf { it.caseIsLive }?.batteryCase
        val (value, source) = when {
            aapValue != null -> aapValue to DataSource.AAP
            else -> (ble as? DualApplePods)?.batteryCaseLivePercent?.let { it to DataSource.BLE }
                ?: return null
        }
        return value.takeIf { isKnownBattery(it) }?.coerceIn(0f, 1f)?.let { it to source }
    }

    private fun PodDevice.caseCharging(): Boolean? =
        aap?.takeIf { it.caseIsLive }?.let { it.caseChargingState == AapPodState.ChargingState.CHARGING }
            ?: (ble as? DualApplePods)?.isCaseChargingLive

    /**
     * Closes [slot]'s current listening segment: a valid fit is queued for persistence (the next
     * persist pass writes it, bypassing cadence) and the window cleared either way.
     */
    private fun captureListeningSegment(tracker: DeviceTracker, slot: Slot) {
        val history = tracker.listeningSlots.getValue(slot)
        if (history.size == 0) return
        DrainModel.slopeFractionPerHour(history.toList())?.let {
            tracker.pendingListeningFits[slot] = it to history.size
        }
        history.clear()
    }

    /** Whether the pod in [slot] is being worn — per-pod for buds, whole-device for headsets. */
    private fun PodDevice.wornForSlot(slot: Slot): Boolean = when (slot) {
        Slot.LEFT -> isLeftInEar
        Slot.RIGHT -> isRightInEar
        Slot.HEADSET -> isBeingWorn
    } == true

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

        var chargeBands = existing.chargeBands
        var listeningRates = existing.listeningRates

        // Blend against the rate stored when this session began, captured once per key, so a single
        // long session's repeated writes can't dominate prior history by re-blending their own
        // output. The captured updateCount keeps a whole session counting as ONE accumulated update.
        fun blended(cadenceKey: String, stored: DrainProfile.LearnedRate?, fit: Float, samples: Int): DrainProfile.LearnedRate {
            if (!tracker.sessionBaseline.containsKey(cadenceKey)) {
                tracker.sessionBaseline[cadenceKey] = stored?.fractionPerHour
                tracker.sessionBaselineCounts[cadenceKey] = stored?.updateCount ?: 0
            }
            return DrainProfile.LearnedRate(
                fractionPerHour = DrainModel.blendRate(tracker.sessionBaseline[cadenceKey], fit),
                sampleCount = samples,
                updateCount = (tracker.sessionBaselineCounts[cadenceKey] ?: 0) + 1,
                updatedAt = timeSource.now(),
            )
        }

        // True at most once per PERSIST_INTERVAL_MS per key (bypassed on [force] or [always]).
        fun cadenceOk(cadenceKey: String, always: Boolean = false): Boolean {
            val last = tracker.lastPersistAtMs[cadenceKey]
            if (!always && !force && last != null && nowMs - last < PERSIST_INTERVAL_MS) return false
            tracker.lastPersistAtMs[cadenceKey] = nowMs
            return true
        }

        for (slot in Slot.entries) {
            val history = tracker.slots.getValue(slot)

            if (history.direction == SlotHistory.Direction.CHARGE) {
                // Whole-session scalar — the fallback basis and the stall-threshold reference.
                DrainModel.chargeSlopeFractionPerHour(history.toList())?.let { fit ->
                    val key = chargeRateKey(slot)
                    if (cadenceOk(key)) {
                        chargeRates = chargeRates + (slot.name to blended(key, chargeRates[slot.name], fit, history.size))
                        changed = true
                        log(TAG, VERBOSE) { "Persisting charge rate for $profileId [$key]: ${"%.3f".format(fit)}/hr" }
                    }
                }
                // Per-band rates — charging is CC/CV, each regime learns its own speed.
                for (band in DrainModel.ChargeBand.entries) {
                    val fit = DrainModel.chargeBandSlopeFractionPerHour(history.toList(), band) ?: continue
                    val key = "${chargeRateKey(slot)}/${band.name}"
                    if (!cadenceOk(key)) continue
                    val stored = chargeBands[slot.name]?.get(band.name)
                    val slotBands = chargeBands[slot.name].orEmpty() + (band.name to blended(key, stored, fit, history.size))
                    chargeBands = chargeBands + (slot.name to slotBands)
                    changed = true
                    log(TAG, VERBOSE) { "Persisting charge band for $profileId [$key]: ${"%.3f".format(fit)}/hr" }
                }
            } else {
                // Same model-aware plausibility gate as display, so an implausibly fast fit isn't learned.
                DrainModel.slopeFractionPerHour(history.toList())
                    ?.takeIf { plausibleForModel(it, spec) }
                    ?.let { fit ->
                        val key = rateKey(bucket, slot)
                        if (cadenceOk(key)) {
                            rates = rates + (key to blended(key, rates[key], fit, history.size))
                            changed = true
                            log(TAG, VERBOSE) { "Persisting learned rate for $profileId [$key]: ${"%.3f".format(fit)}/hr" }
                        }
                    }
            }

            // Listening rates (health basis): a just-closed segment persists immediately — it would
            // be lost otherwise, the window is already cleared. An ongoing window follows cadence.
            val pending = tracker.pendingListeningFits.remove(slot)
            val (fit, samples) = when {
                pending != null -> pending
                else -> DrainModel.slopeFractionPerHour(tracker.listeningSlots.getValue(slot).toList())
                    ?.let { it to tracker.listeningSlots.getValue(slot).size } ?: (null to 0)
            }
            if (fit != null && plausibleForModel(fit, spec)) {
                val key = rateKey(bucket, slot)
                val cadenceKey = "LISTEN/$key"
                if (cadenceOk(cadenceKey, always = pending != null)) {
                    listeningRates = listeningRates + (key to blended(cadenceKey, listeningRates[key], fit, samples))
                    changed = true
                    log(TAG, VERBOSE) { "Persisting listening rate for $profileId [$key]: ${"%.3f".format(fit)}/hr" }
                }
            }
        }

        // CASE: charge fits from the dedicated case window, plus a closed transfer observation.
        var caseTransfer = existing.caseTransfer
        if (tracker.caseHistory.direction == SlotHistory.Direction.CHARGE) {
            DrainModel.chargeSlopeFractionPerHour(tracker.caseHistory.toList())?.let { fit ->
                val key = "CHARGE/$CASE_KEY"
                if (cadenceOk(key)) {
                    chargeRates = chargeRates + (CASE_KEY to blended(key, chargeRates[CASE_KEY], fit, tracker.caseHistory.size))
                    changed = true
                    log(TAG, VERBOSE) { "Persisting case charge rate for $profileId: ${"%.3f".format(fit)}/hr" }
                }
            }
            for (band in DrainModel.ChargeBand.entries) {
                val fit = DrainModel.chargeBandSlopeFractionPerHour(tracker.caseHistory.toList(), band) ?: continue
                val key = "CHARGE/$CASE_KEY/${band.name}"
                if (!cadenceOk(key)) continue
                val stored = chargeBands[CASE_KEY]?.get(band.name)
                chargeBands = chargeBands +
                    (CASE_KEY to (chargeBands[CASE_KEY].orEmpty() + (band.name to blended(key, stored, fit, tracker.caseHistory.size))))
                changed = true
            }
        }
        tracker.pendingTransferRatio?.let { observed ->
            tracker.pendingTransferRatio = null
            val key = "TRANSFER"
            if (!tracker.sessionBaseline.containsKey(key)) {
                tracker.sessionBaseline[key] = existing.caseTransfer?.ratio
                tracker.sessionBaselineCounts[key] = existing.caseTransfer?.updateCount ?: 0
            }
            caseTransfer = DrainProfile.TransferRatio(
                ratio = DrainModel.blendRate(tracker.sessionBaseline[key], observed),
                updateCount = (tracker.sessionBaselineCounts[key] ?: 0) + 1,
                updatedAt = timeSource.now(),
            )
            changed = true
            log(TAG, VERBOSE) { "Persisting case transfer ratio for $profileId: ${"%.2f".format(observed)}" }
        }

        if (changed) {
            drainStore.save(
                profileId,
                existing.copy(
                    model = device.model.name,
                    rates = rates,
                    chargeRates = chargeRates,
                    chargeBands = chargeBands,
                    listeningRates = listeningRates,
                    caseTransfer = caseTransfer,
                ),
            )
        }
    }

    private fun learnedRate(profileId: ProfileId, device: PodDevice, bucket: String, slot: Slot): Float? {
        val profile = storedProfileFor(profileId, device) ?: return null
        return (profile.rates[rateKey(bucket, slot)] ?: profile.rates[rateKey(MODE_UNKNOWN, slot)])?.fractionPerHour
    }

    private fun learnedChargeRate(profileId: ProfileId, device: PodDevice, slotName: String): Float? =
        storedProfileFor(profileId, device)?.chargeRates[slotName]?.fractionPerHour

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

        /** Persistence key for the case's charge rates/bands (the pod slots use their enum names). */
        private const val CASE_KEY = "CASE"

        /** A transfer window must see at least this much case drop before its ratio is trusted. */
        private const val MIN_TRANSFER_CASE_DROP = 0.05f

        /** Plausibility band for a transfer ratio (nominal is ~4-10 depending on model). */
        private const val TRANSFER_RATIO_MIN = 0.5f
        private const val TRANSFER_RATIO_MAX = 20f

        /** A measured rate above this multiple of the model's rated drain is rejected as implausible. */
        private const val SPEC_BAND_MAX = 4f

        /** A gap longer than this between updates means the device was away — reset its window. */
        private const val STALE_GAP_MS = 15 * 60_000L
    }
}
