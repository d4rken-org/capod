package eu.darken.capod.reaction.core.playpause

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.reaction.core.playpause.PlayPause.Companion.PAUSE_DEBOUNCE_SAMPLES
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayPause @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val mediaControl: MediaControl,
) {

    fun monitor() = run {
        var pendingPlayConfirmation: PendingPlayConfirmation? = null
        var pendingPauseDebounce: PendingPauseDebounce? = null
        var hasLastMonitorKey = false
        var lastMonitorKey: PlayPauseMonitorKey? = null

        deviceMonitor.primaryDevice()
            .map { device -> device?.reactions?.let { it.autoPlay || it.autoPause } == true }
            .distinctUntilChanged()
            .flatMapLatest { shouldMonitor ->
                if (shouldMonitor) {
                    bluetoothManager.connectedDevices
                } else {
                    pendingPlayConfirmation = null
                    pendingPauseDebounce = null
                    hasLastMonitorKey = false
                    lastMonitorKey = null
                    emptyFlow()
                }
            }
            .flatMapLatest { connected ->
                if (connected.isEmpty()) {
                    pendingPlayConfirmation = null
                    pendingPauseDebounce = null
                    hasLastMonitorKey = false
                    lastMonitorKey = null
                    log(TAG) { "No known devices connected." }
                    emptyFlow()
                } else {
                    log(TAG) { "Known devices connected: $connected" }
                    deviceMonitor.primaryDevice()
                }
            }
            // Cache persistence can update battery timestamps without changing any reaction-relevant state.
            .filter { device ->
                val key = device?.toPlayPauseMonitorKey()
                val shouldEmit = !hasLastMonitorKey ||
                    key != lastMonitorKey ||
                    device?.isPauseDebounceResetCandidate(pendingPauseDebounce) == true

                if (shouldEmit) {
                    hasLastMonitorKey = true
                    lastMonitorKey = key
                }
                shouldEmit
            }
            .onEach { device ->
                log(TAG, VERBOSE) { "Post-monitor-filter: profileId=${device?.profileId}" }
            }
            .withPrevious()
            .filter { (previous, current) ->
                if (previous == null || current == null) return@filter false
                // Use profileId (stable across BLE address rotations) rather than BLE identifier.
                // Only profiled devices reach this point (outer gate requires profile.autoPlay/autoPause).
                val match = previous.profileId != null && previous.profileId == current.profileId
                if (!match) {
                    log(TAG, WARN) { "Main device switched, skipping reaction." }
                    if (pendingPauseDebounce != null) {
                        log(TAG, DEBUG) { "Pause debounce reset: profile change" }
                        pendingPauseDebounce = null
                    }
                }
                match
            }
            .onEach { (previous, current) ->
                log(TAG, VERBOSE) {
                    "Checking: prev(left=${previous?.isLeftInEar}, right=${previous?.isRightInEar}, worn=${previous?.isBeingWorn}) " +
                        "-> cur(left=${current?.isLeftInEar}, right=${current?.isRightInEar}, worn=${current?.isBeingWorn})"
                }

                val reactions = current?.reactions
                if (reactions == null) {
                    log(TAG, VERBOSE) { "No reactions on current device, skipping reaction" }
                    pendingPlayConfirmation = null
                    if (pendingPauseDebounce != null) {
                        log(TAG, DEBUG) { "Pause debounce reset: no reactions on device" }
                        pendingPauseDebounce = null
                    }
                    return@onEach
                }

                // Skip the reaction when previous was a no-live-evidence emission (cache-only
                // baseline emitted at process start, or after a >20s BLE gap that evicted the
                // device from the live cache). PodDevice.isBeingWorn returns null for those,
                // and toEarDetectionState() coerces null -> false, which would produce a
                // fake "not-worn -> worn" transition the moment live BLE arrives — firing
                // an unwanted autoPlay on app start while the user is wearing the pods.
                if (previous?.earDetectionSource() == EarDetectionSource.NO_LIVE_BLE) {
                    log(TAG, VERBOSE) { "Previous emission has no live evidence; skipping reaction." }
                    pendingPlayConfirmation = null
                    if (pendingPauseDebounce != null) {
                        log(TAG, DEBUG) { "Pause debounce reset: previous emission lacked live evidence" }
                        pendingPauseDebounce = null
                    }
                    return@onEach
                }

                // Convert to EarDetectionState based on device capabilities
                val prevState: EarDetectionState
                val currState: EarDetectionState

                when {
                    previous!!.hasEarDetection && previous.hasDualPods &&
                        current.hasEarDetection && current.hasDualPods -> {
                        // Dual pod devices (AirPods, AirPods Pro, etc.)
                        // Per-side values may be null when AAP ear detection is present but
                        // resolvedPrimaryPod is unknown (cmd 0x0008 not received or cleared
                        // by role swap). Fall back to aggregate AAP state in that case.
                        prevState = previous.toEarDetectionState()
                        currState = current.toEarDetectionState()
                        log(TAG, VERBOSE) {
                            "Dual-pod device: left=${current.isLeftInEar}, right=${current.isRightInEar}, " +
                                "worn=${current.isBeingWorn}, either=${current.isEitherPodInEar}, " +
                                "aapEar=${current.aap?.aapEarDetection != null}, state=$currState"
                        }
                    }

                    previous.hasEarDetection && current.hasEarDetection -> {
                        // Single pod devices (AirPods Max, etc.)
                        log(TAG, VERBOSE) { "Single-pod device: worn=${current.isBeingWorn}" }
                        prevState = EarDetectionState.fromSinglePod(worn = previous.isBeingWorn ?: false)
                        currState = EarDetectionState.fromSinglePod(worn = current.isBeingWorn ?: false)
                    }

                    else -> {
                        log(TAG, VERBOSE) { "Device doesn't support ear detection: $current" }
                        pendingPlayConfirmation = null
                        if (pendingPauseDebounce != null) {
                            log(TAG, DEBUG) { "Pause debounce reset: device lost ear detection" }
                            pendingPauseDebounce = null
                        }
                        return@onEach
                    }
                }

                val isCurrentlyPlaying = mediaControl.isPlaying
                val wasRecentlyPausedByUs = mediaControl.wasRecentlyPausedByCap

                val source = current.earDetectionSource()

                // Evaluate what action to take
                val rawDecision = evaluatePlayPauseAction(
                    previous = prevState,
                    current = currState,
                    onePodMode = reactions.onePodMode,
                    isCurrentlyPlaying = isCurrentlyPlaying,
                    wasRecentlyPausedByUs = wasRecentlyPausedByUs,
                )

                // BLE-only autoplay confirmation only applies to UNAUTHENTICATED sources.
                // Trusted sources (AAP, BLE_IRK_MATCH) skip staging — symmetric to the
                // pause debounce, which also skips for these sources. Without this gate,
                // an IRK-matched device with no live AAP would stage a BLE-only confirmation
                // that never confirms (the second worn sample has no freshness on the
                // monitor key for IRK_MATCH so it gets collapsed).
                val shouldStageBleOnlyPlay = rawDecision.shouldPlay &&
                    reactions.autoPlay &&
                    !reactions.onePodMode &&
                    current.hasDualPods &&
                    (source == EarDetectionSource.BLE_PROFILE_FALLBACK ||
                        source == EarDetectionSource.BLE_ANONYMOUS)

                val confirmation = applyBleOnlyPlayConfirmation(
                    pending = pendingPlayConfirmation,
                    profileId = current.profileId,
                    onePodMode = reactions.onePodMode,
                    autoPlayEnabled = reactions.autoPlay,
                    rawDecision = rawDecision,
                    currentState = currState,
                    shouldStageBleOnlyPlay = shouldStageBleOnlyPlay,
                    isCurrentlyPlaying = isCurrentlyPlaying,
                    wasRecentlyPausedByUs = wasRecentlyPausedByUs,
                )
                pendingPlayConfirmation = confirmation.pending

                if (confirmation.stagedConfirmation) {
                    log(TAG, VERBOSE) { "Staging BLE-only autoplay confirmation for ${current.profileId}" }
                }
                if (confirmation.confirmedPendingPlay) {
                    log(TAG, VERBOSE) { "BLE-only autoplay confirmed by a follow-up state update" }
                }

                val debounceResult = applyPauseDebounce(
                    pending = pendingPauseDebounce,
                    profileId = current.profileId,
                    source = source,
                    rawDecision = confirmation.decision,
                    currentState = currState,
                    autoPauseEnabled = reactions.autoPause,
                )
                pendingPauseDebounce = debounceResult.pending

                when (debounceResult.event) {
                    PauseDebounceEvent.STARTED -> log(TAG, DEBUG) {
                        "Pause debounce started: source=$source, initialPodCount=${debounceResult.pending?.initialPodCount}, " +
                            "remaining=${debounceResult.pending?.confirmationsRemaining}"
                    }
                    PauseDebounceEvent.ADVANCED -> log(TAG, DEBUG) {
                        "Pause debounce advanced: remaining=${debounceResult.pending?.confirmationsRemaining}, " +
                            "currentPodCount=${currState.podCount}"
                    }
                    PauseDebounceEvent.RESET -> log(TAG, DEBUG) {
                        "Pause debounce reset: source=$source, currentPodCount=${currState.podCount}, " +
                            "rawShouldPlay=${confirmation.decision.shouldPlay}"
                    }
                    PauseDebounceEvent.COMMITTED -> log(TAG, DEBUG) {
                        "Pause debounce committed: source=$source confirmed pause"
                    }
                    PauseDebounceEvent.NONE -> {}
                }

                val decision = debounceResult.decision
                if (decision.usedRecentCapPauseOverride) {
                    log(TAG, VERBOSE) {
                        "Resume override: recent CAP pause window is active, allowing play despite playing=true"
                    }
                }
                log(TAG, VERBOSE) { "Decision: ${decision.reason}" }

                // Execute the decision
                when {
                    decision.shouldPlay && reactions.autoPlay -> {
                        log(TAG) { "autoPlay is triggered, sendPlay() - ${decision.reason}" }
                        mediaControl.sendPlay()
                    }

                    decision.shouldPlay && !reactions.autoPlay -> {
                        log(TAG, VERBOSE) { "autoPlay is disabled" }
                    }

                    decision.shouldPause && reactions.autoPause -> {
                        val pauseSent = mediaControl.sendPause()
                        log(TAG, INFO) {
                            "autoPause triggered: source=$source, " +
                                "wasWorn=${prevState.bothInEar}, isWorn=${currState.bothInEar}, " +
                                "podCount=${prevState.podCount}->${currState.podCount}, " +
                                "aapEar=${current.aap?.aapEarDetection != null}, " +
                                "aapConn=${current.aap?.connectionState}, " +
                                "pauseSent=$pauseSent, reason=${decision.reason}"
                        }
                    }

                    decision.shouldPause && !reactions.autoPause -> {
                        log(TAG, VERBOSE) { "autoPause is disabled" }
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "monitor" }
    }

    internal fun evaluatePlayPauseAction(
        previous: EarDetectionState,
        current: EarDetectionState,
        onePodMode: Boolean,
        isCurrentlyPlaying: Boolean,
        wasRecentlyPausedByUs: Boolean = false,
    ): PlayPauseDecision = if (onePodMode) {
        evaluateOnePodMode(previous, current, isCurrentlyPlaying, wasRecentlyPausedByUs)
    } else {
        evaluateNormalMode(previous, current, isCurrentlyPlaying, wasRecentlyPausedByUs)
    }

    private fun evaluateOnePodMode(
        previous: EarDetectionState,
        current: EarDetectionState,
        isCurrentlyPlaying: Boolean,
        wasRecentlyPausedByUs: Boolean,
    ): PlayPauseDecision {
        val netChange = current.podCount - previous.podCount

        return when {
            // Net decrease: pod(s) removed → pause
            netChange < 0 && isCurrentlyPlaying -> PlayPauseDecision(
                shouldPlay = false,
                shouldPause = true,
                reason = "One-pod mode: pod(s) removed (net change: $netChange)"
            )

            // Net increase: pod(s) inserted → play
            netChange > 0 && (!isCurrentlyPlaying || wasRecentlyPausedByUs) -> PlayPauseDecision(
                shouldPlay = true,
                shouldPause = false,
                reason = "One-pod mode: pod(s) inserted (net change: +$netChange)",
                usedRecentCapPauseOverride = isCurrentlyPlaying && wasRecentlyPausedByUs,
            )

            // No net change, or action not appropriate for current playing state
            else -> PlayPauseDecision(
                shouldPlay = false,
                shouldPause = false,
                reason = "One-pod mode: no action (net change: $netChange, playing: $isCurrentlyPlaying)"
            )
        }
    }

    private fun evaluateNormalMode(
        previous: EarDetectionState,
        current: EarDetectionState,
        isCurrentlyPlaying: Boolean,
        wasRecentlyPausedByCap: Boolean,
    ): PlayPauseDecision {
        val wasWorn = previous.bothInEar
        val isWorn = current.bothInEar

        return when {
            // Transition: not worn → worn, and not playing → play
            !wasWorn && isWorn && (!isCurrentlyPlaying || wasRecentlyPausedByCap) -> PlayPauseDecision(
                shouldPlay = true,
                shouldPause = false,
                reason = "Normal mode: both pods in ear",
                usedRecentCapPauseOverride = isCurrentlyPlaying && wasRecentlyPausedByCap,
            )

            // Transition: worn → not worn, and playing → pause
            wasWorn && !isWorn && isCurrentlyPlaying -> PlayPauseDecision(
                shouldPlay = false,
                shouldPause = true,
                reason = "Normal mode: not both pods in ear"
            )

            // No action needed
            else -> PlayPauseDecision(
                shouldPlay = false,
                shouldPause = false,
                reason = "Normal mode: no action (wasWorn: $wasWorn, isWorn: $isWorn, playing: $isCurrentlyPlaying)"
            )
        }
    }

    internal fun applyBleOnlyPlayConfirmation(
        pending: PendingPlayConfirmation?,
        profileId: String?,
        onePodMode: Boolean,
        autoPlayEnabled: Boolean,
        rawDecision: PlayPauseDecision,
        currentState: EarDetectionState,
        shouldStageBleOnlyPlay: Boolean,
        isCurrentlyPlaying: Boolean,
        wasRecentlyPausedByUs: Boolean,
    ): PlayConfirmationResult {
        val activePending = pending?.takeIf {
            it.profileId == profileId && it.onePodMode == onePodMode && autoPlayEnabled
        }

        if (activePending != null &&
            currentState == activePending.targetState &&
            (!isCurrentlyPlaying || wasRecentlyPausedByUs)
        ) {
            return PlayConfirmationResult(
                decision = PlayPauseDecision(
                    shouldPlay = true,
                    shouldPause = false,
                    reason = "${activePending.reason} (confirmed by a second state update)",
                    usedRecentCapPauseOverride = isCurrentlyPlaying && wasRecentlyPausedByUs,
                ),
                pending = null,
                confirmedPendingPlay = true,
                stagedConfirmation = false,
            )
        }

        if (shouldStageBleOnlyPlay && profileId != null) {
            return PlayConfirmationResult(
                decision = rawDecision.copy(
                    shouldPlay = false,
                    reason = "${rawDecision.reason} (waiting for BLE confirmation)",
                ),
                pending = PendingPlayConfirmation(
                    profileId = profileId,
                    onePodMode = onePodMode,
                    targetState = currentState,
                    reason = rawDecision.reason,
                ),
                confirmedPendingPlay = false,
                stagedConfirmation = true,
            )
        }

        return PlayConfirmationResult(
            decision = rawDecision,
            pending = null,
            confirmedPendingPlay = false,
            stagedConfirmation = false,
        )
    }

    /**
     * Sample-count debounce for pause decisions when the ear-detection source is an
     * unauthenticated BLE advertisement.
     *
     * RF interference can produce a single corrupt advert that decodes as not-worn,
     * triggering a false pause. With [PAUSE_DEBOUNCE_SAMPLES] = 2, a pause requires
     * 3 consecutive not-worn samples before firing.
     *
     * The helper advances [pending] from [currentState], NOT from [rawDecision.shouldPause]
     * — subsequent samples after the initial detection are not-worn → not-worn, and
     * [evaluateNormalMode] returns no action for those.
     *
     * Confirmation rule: a sample with `currentState.podCount <= pending.initialPodCount`
     * counts as a confirmation. An *increase* (pod returned) clears pending.
     *
     * Trusted sources ([EarDetectionSource.AAP], [EarDetectionSource.BLE_IRK_MATCH]) skip
     * debounce and pass through [rawDecision] unchanged; any active [pending] is cleared.
     */
    internal fun applyPauseDebounce(
        pending: PendingPauseDebounce?,
        profileId: String?,
        source: EarDetectionSource,
        rawDecision: PlayPauseDecision,
        currentState: EarDetectionState,
        autoPauseEnabled: Boolean,
    ): PauseDebounceResult {
        val needsDebounce = source == EarDetectionSource.BLE_PROFILE_FALLBACK ||
            source == EarDetectionSource.BLE_ANONYMOUS

        val activePending = pending?.takeIf { it.profileId == profileId }

        // NO_LIVE_BLE: no fresh evidence anywhere (ble == null, aap absent or no EarDetection).
        // Suppress shouldPause — a worn → not-worn transition derived from null/cached state is
        // not real removal evidence. Also clears any active pending since stale samples must not
        // advance confirmations.
        if (source == EarDetectionSource.NO_LIVE_BLE) {
            val event = if (activePending != null) PauseDebounceEvent.RESET else PauseDebounceEvent.NONE
            return PauseDebounceResult(
                decision = rawDecision.copy(
                    shouldPause = false,
                    reason = "${rawDecision.reason} (suppressed: no live BLE evidence)",
                ),
                pending = null,
                event = event,
            )
        }

        // Non-debounce-eligible trusted sources (AAP / BLE_IRK_MATCH) and disabled debounce:
        // pass raw decision through and clear any active pending. Special case: if a
        // pending was active and the trusted source still shows the not-worn condition
        // (currentState.podCount <= initialPodCount and no play decision), commit the
        // pause now — the trusted source corroborates what BLE-debounce was waiting on.
        if (!needsDebounce || !autoPauseEnabled || profileId == null) {
            if (activePending != null && autoPauseEnabled &&
                currentState.podCount <= activePending.initialPodCount &&
                !rawDecision.shouldPlay
            ) {
                return PauseDebounceResult(
                    decision = PlayPauseDecision(
                        shouldPlay = false,
                        shouldPause = true,
                        reason = "Pause confirmed by trusted source ($source) after BLE-debounce",
                    ),
                    pending = null,
                    event = PauseDebounceEvent.COMMITTED,
                )
            }
            val event = if (activePending != null) PauseDebounceEvent.RESET else PauseDebounceEvent.NONE
            return PauseDebounceResult(decision = rawDecision, pending = null, event = event)
        }

        // First detection: no active pending, raw decision wants to pause → start debounce.
        if (activePending == null) {
            if (!rawDecision.shouldPause) {
                return PauseDebounceResult(decision = rawDecision, pending = null, event = PauseDebounceEvent.NONE)
            }
            if (PAUSE_DEBOUNCE_SAMPLES <= 0) {
                return PauseDebounceResult(decision = rawDecision, pending = null, event = PauseDebounceEvent.NONE)
            }
            return PauseDebounceResult(
                decision = rawDecision.copy(
                    shouldPause = false,
                    reason = "${rawDecision.reason} (debouncing, $PAUSE_DEBOUNCE_SAMPLES confirmation(s) needed)",
                ),
                pending = PendingPauseDebounce(
                    profileId = profileId,
                    initialPodCount = currentState.podCount,
                    confirmationsRemaining = PAUSE_DEBOUNCE_SAMPLES,
                ),
                event = PauseDebounceEvent.STARTED,
            )
        }

        // Confirmation phase: pending exists.
        // Reset cases — checked in order of authority:
        //   1. Raw decision wants to play → genuine play signal, reset immediately.
        //   2. Pod count went up → tolerate one rebound sample (corrupt count-up
        //      protection), reset only on the second consecutive count-up.
        if (rawDecision.shouldPlay) {
            return PauseDebounceResult(decision = rawDecision, pending = null, event = PauseDebounceEvent.RESET)
        }
        if (currentState.podCount > activePending.initialPodCount) {
            if (activePending.resetTolerance > 0) {
                return PauseDebounceResult(
                    decision = rawDecision.copy(
                        shouldPause = false,
                        reason = "Debouncing pause (rebound tolerated)",
                    ),
                    pending = activePending.copy(resetTolerance = activePending.resetTolerance - 1),
                    event = PauseDebounceEvent.ADVANCED,
                )
            }
            return PauseDebounceResult(decision = rawDecision, pending = null, event = PauseDebounceEvent.RESET)
        }

        // Confirmation: count <= initialPodCount, decrement remaining.
        val remaining = activePending.confirmationsRemaining - 1
        if (remaining <= 0) {
            return PauseDebounceResult(
                decision = PlayPauseDecision(
                    shouldPlay = false,
                    shouldPause = true,
                    reason = "Debounced pause confirmed (initial count: ${activePending.initialPodCount}, current: ${currentState.podCount})",
                ),
                pending = null,
                event = PauseDebounceEvent.COMMITTED,
            )
        }
        return PauseDebounceResult(
            decision = rawDecision.copy(
                shouldPause = false,
                reason = "Debouncing pause ($remaining confirmation(s) remaining)",
            ),
            pending = activePending.copy(confirmationsRemaining = remaining),
            event = PauseDebounceEvent.ADVANCED,
        )
    }

    data class EarDetectionState(
        val leftInEar: Boolean?,    // null for single pod devices
        val rightInEar: Boolean?,   // null for single pod devices
        val isWorn: Boolean         // Single-pod: device worn state. Dual-pod: equivalent to bothInEar (left && right).
    ) {
        val isDualPod: Boolean get() = leftInEar != null && rightInEar != null
        val isSinglePod: Boolean get() = !isDualPod
        val eitherInEar: Boolean get() = if (isDualPod) leftInEar!! || rightInEar!! else isWorn
        val bothInEar: Boolean get() = if (isDualPod) leftInEar!! && rightInEar!! else isWorn
        val podCount: Int
            get() = if (isDualPod) {
                (if (leftInEar!!) 1 else 0) + (if (rightInEar!!) 1 else 0)
            } else {
                if (isWorn) 1 else 0
            }

        companion object {
            fun fromDualPod(left: Boolean, right: Boolean) = EarDetectionState(
                leftInEar = left,
                rightInEar = right,
                isWorn = left && right
            )

            fun fromSinglePod(worn: Boolean) = EarDetectionState(
                leftInEar = null,
                rightInEar = null,
                isWorn = worn
            )

            /**
             * Derives ear detection state from AAP aggregate values when per-side
             * (left/right) mapping is unavailable (e.g. resolvedPrimaryPod is null).
             *
             * Uses [isBeingWorn] (both pods in ear) and [isEitherPodInEar] (at least
             * one pod in ear) to reconstruct a pod count suitable for both normal mode
             * (bothInEar triggers play) and one-pod mode (podCount delta triggers play/pause).
             *
             * The left/right assignment is synthetic but deterministic — transitions are
             * always detected. UI-facing code should use [PodDevice.isLeftInEar] /
             * [PodDevice.isRightInEar] which stay null when side mapping is unknown.
             */
            fun fromAapAggregate(isBeingWorn: Boolean, isEitherPodInEar: Boolean) = when {
                isBeingWorn -> EarDetectionState(leftInEar = true, rightInEar = true, isWorn = true)
                isEitherPodInEar -> EarDetectionState(leftInEar = true, rightInEar = false, isWorn = false)
                else -> EarDetectionState(leftInEar = false, rightInEar = false, isWorn = false)
            }
        }
    }

    data class PlayPauseDecision(
        val shouldPlay: Boolean,
        val shouldPause: Boolean,
        val reason: String,
        val usedRecentCapPauseOverride: Boolean = false,
    )

    data class PendingPlayConfirmation(
        val profileId: String,
        val onePodMode: Boolean,
        val targetState: EarDetectionState,
        val reason: String,
    )

    data class PlayConfirmationResult(
        val decision: PlayPauseDecision,
        val pending: PendingPlayConfirmation?,
        val confirmedPendingPlay: Boolean,
        val stagedConfirmation: Boolean,
    )

    /**
     * Trust classification for the source of the current ear-detection reading.
     *
     * - [AAP]: EarDetection setting from an active AAP (L2CAP) session — error-corrected,
     *   identity-authenticated. Trusted; debounce skipped.
     * - [BLE_IRK_MATCH]: BLE advertisement whose RPA was verified against this profile's
     *   identity key. Identity-authenticated; debounce skipped.
     * - [BLE_PROFILE_FALLBACK]: BLE advertisement assigned to a profile via signal-quality
     *   fallback (no IRK match). Could be a stray advert from a nearby pair. Debounced.
     * - [BLE_ANONYMOUS]: BLE advertisement with no profile match. Filtered out by
     *   [primaryDevice] in production (devices without profileId are dropped before
     *   reaching the reaction layer); kept here defensively in case the upstream filter
     *   changes. Note: [PendingPauseDebounce] is profile-keyed, so a null profileId can
     *   never sustain pending state even if this branch is hit.
     * - [NO_LIVE_BLE]: No live BLE snapshot at all (cache-only or empty). Not debounced —
     *   the cached state is not "fresh evidence" so it must not advance the debounce counter.
     *   Any active pending is cleared.
     */
    enum class EarDetectionSource { AAP, BLE_IRK_MATCH, BLE_PROFILE_FALLBACK, BLE_ANONYMOUS, NO_LIVE_BLE }

    data class PendingPauseDebounce(
        val profileId: String,
        val initialPodCount: Int,
        val confirmationsRemaining: Int,
        // Tolerates one count-up rebound sample before the pending is reset. Mirrors the
        // count-down debounce on the pause side: a single corrupt advert that briefly
        // shows a pod returning shouldn't kill the pending, since the next sample may
        // confirm the pods are still out.
        val resetTolerance: Int = 1,
    )

    /** Discrete event produced by [applyPauseDebounce] for diagnostic logging. */
    enum class PauseDebounceEvent { NONE, STARTED, ADVANCED, RESET, COMMITTED }

    data class PauseDebounceResult(
        val decision: PlayPauseDecision,
        val pending: PendingPauseDebounce?,
        val event: PauseDebounceEvent = PauseDebounceEvent.NONE,
    )

    internal data class PlayPauseMonitorKey(
        val profileId: String?,
        val autoPlay: Boolean,
        val autoPause: Boolean,
        val onePodMode: Boolean,
        val hasEarDetection: Boolean,
        val hasDualPods: Boolean,
        val leftInEar: Boolean?,
        val rightInEar: Boolean?,
        val isBeingWorn: Boolean?,
        val isEitherPodInEar: Boolean?,
        val hasAapEarDetection: Boolean,
        val hasBleSnapshot: Boolean,
        val source: EarDetectionSource,
        // Set only for debounce-eligible sources (BLE_PROFILE_FALLBACK / BLE_ANONYMOUS) so
        // that monitor distinct filtering doesn't collapse repeated identical not-worn samples
        // and the debounce counter can advance.
        //
        // Caveat: BlePodMonitor.preferCaseContextPod can keep an existing case-context
        // snapshot in place over an incoming non-case-context snapshot, preserving the old
        // seenLastAt. This is fail-closed (delays a legitimate unauthenticated-BLE pause
        // rather than firing a false one), so accepted as a trade-off.
        val debounceFreshness: Instant?,
    )

    internal fun PodDevice.earDetectionSource(): EarDetectionSource {
        if (aap?.aapEarDetection != null) return EarDetectionSource.AAP
        if (ble == null) return EarDetectionSource.NO_LIVE_BLE
        val applePod = ble as? ApplePods
        return when {
            applePod?.meta?.isIRKMatch == true -> EarDetectionSource.BLE_IRK_MATCH
            ble.meta.profile != null -> EarDetectionSource.BLE_PROFILE_FALLBACK
            else -> EarDetectionSource.BLE_ANONYMOUS
        }
    }

    internal fun PodDevice.toPlayPauseMonitorKey(): PlayPauseMonitorKey {
        val source = earDetectionSource()
        // Freshness applies to all unauthenticated samples (both worn and not-worn) so
        // that monitor distinct filtering doesn't collapse repeated identical samples:
        //   - not-worn samples must pass through to advance the pause debounce counter
        //   - worn samples must pass through to satisfy applyBleOnlyPlayConfirmation,
        //     which requires a 2nd identical worn sample to confirm a staged play
        //     (see commit 6825abaa "Guard BLE-only autoplay")
        // The 2-sample autoplay confirmation IS the debounce on the play side, mirroring
        // the pause debounce. isPauseDebounceResetCandidate() remains as a defense-in-depth
        // backstop for the rare case where seenLastAt doesn't advance between samples
        // (e.g. BlePodMonitor.preferCaseContextPod preserves the prior snapshot).
        val needsFreshness = source == EarDetectionSource.BLE_PROFILE_FALLBACK ||
            source == EarDetectionSource.BLE_ANONYMOUS
        return PlayPauseMonitorKey(
            profileId = profileId,
            autoPlay = reactions.autoPlay,
            autoPause = reactions.autoPause,
            onePodMode = reactions.onePodMode,
            hasEarDetection = hasEarDetection,
            hasDualPods = hasDualPods,
            leftInEar = isLeftInEar,
            rightInEar = isRightInEar,
            isBeingWorn = isBeingWorn,
            isEitherPodInEar = isEitherPodInEar,
            hasAapEarDetection = aap?.aapEarDetection != null,
            hasBleSnapshot = ble != null,
            source = source,
            debounceFreshness = if (needsFreshness) ble?.seenLastAt else null,
        )
    }

    private fun PodDevice.isPauseDebounceResetCandidate(pending: PendingPauseDebounce?): Boolean {
        val activePending = pending?.takeIf { it.profileId == profileId } ?: return false
        val source = earDetectionSource()
        val needsDebounce = source == EarDetectionSource.BLE_PROFILE_FALLBACK ||
            source == EarDetectionSource.BLE_ANONYMOUS

        if (!needsDebounce || !hasEarDetection) return false

        return toEarDetectionState().podCount > activePending.initialPodCount
    }

    /**
     * Converts a dual-pod [PodDevice] to [EarDetectionState] for reaction evaluation.
     *
     * When AAP EarDetection is present, prefers AAP aggregate state — even if per-side
     * (left/right) values are non-null via BLE fallback. This avoids letting BLE per-side
     * bits drive the decision when AAP has authoritative aggregate state but
     * resolvedPrimaryPod is unknown (cmd 0x0008 not received or cleared by role swap).
     *
     * When AAP is absent, prefers per-side values (BLE) when available, falling back to
     * AAP aggregate as a last resort (which is identical to BLE aggregate in this case).
     */
    internal fun PodDevice.toEarDetectionState(): EarDetectionState {
        if (aap?.aapEarDetection != null) {
            return EarDetectionState.fromAapAggregate(
                isBeingWorn = isBeingWorn ?: false,
                isEitherPodInEar = isEitherPodInEar ?: false,
            )
        }
        val left = isLeftInEar
        val right = isRightInEar
        if (left != null && right != null) {
            return EarDetectionState.fromDualPod(left = left, right = right)
        }
        return EarDetectionState.fromAapAggregate(
            isBeingWorn = isBeingWorn ?: false,
            isEitherPodInEar = isEitherPodInEar ?: false,
        )
    }

    companion object {
        private val TAG = logTag("Reaction", "PlayPause")

        /**
         * Number of additional confirmations required before an unauthenticated-BLE pause
         * decision is dispatched. With 2, a pause needs 3 consecutive not-worn samples total.
         */
        internal const val PAUSE_DEBOUNCE_SAMPLES = 2
    }
}
