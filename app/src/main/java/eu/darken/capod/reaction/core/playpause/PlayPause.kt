package eu.darken.capod.reaction.core.playpause

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.reaction.core.ReactionSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayPause @Inject constructor(
    private val podMonitor: PodMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val reactionSettings: ReactionSettings,
    private val mediaControl: MediaControl,
) {

    fun monitor() = combine(
        reactionSettings.autoPlay.flow,
        reactionSettings.autoPause.flow,
        reactionSettings.onePodMode.flow,
    ) { play, pause, _ -> play || pause }
        .flatMapLatest { if (it) bluetoothManager.connectedDevices else emptyFlow() }
        .flatMapLatest {
            if (it.isEmpty()) {
                log(TAG) { "No known devices connected." }
                emptyFlow()
            } else {
                log(TAG) { "Known devices connected: $it" }
                podMonitor.primaryDevice()
            }
        }
        .distinctUntilChanged()
        .withPrevious()
        .filter { (previous, current) ->
            if (previous == null || current == null) return@filter false
            log(TAG, VERBOSE) { "previous-id=${previous.identifier}, current-id=${current.identifier}" }
            val match = previous.identifier == current.identifier
            if (!match) log(TAG, WARN) { "Main device switched, skipping reaction." }
            match
        }
        .onEach { (previous, current) ->
            log(TAG, VERBOSE) { "Checking\nprevious=$previous\ncurrent=$current" }

            // Convert to EarDetectionState based on device capabilities
            val prevState: EarDetectionState
            val currState: EarDetectionState

            when {
                previous is HasEarDetectionDual && current is HasEarDetectionDual -> {
                    // Dual pod devices (AirPods, AirPods Pro, etc.)
                    log(TAG, VERBOSE) {
                        "Dual-pod device: left=${current.isLeftPodInEar}, right=${current.isRightPodInEar}"
                    }
                    prevState = EarDetectionState.fromDualPod(previous)
                    currState = EarDetectionState.fromDualPod(current)
                }

                previous is HasEarDetection && current is HasEarDetection -> {
                    // Single pod devices (AirPods Max, etc.)
                    log(TAG, VERBOSE) { "Single-pod device: worn=${current.isBeingWorn}" }
                    prevState = EarDetectionState.fromSinglePod(previous)
                    currState = EarDetectionState.fromSinglePod(current)
                }

                else -> {
                    log(TAG, VERBOSE) { "Device doesn't support ear detection: $current" }
                    return@onEach
                }
            }

            // Evaluate what action to take
            val decision = evaluatePlayPauseAction(
                previous = prevState,
                current = currState,
                onePodMode = reactionSettings.onePodMode.value,
                isCurrentlyPlaying = mediaControl.isPlaying
            )

            log(TAG, VERBOSE) { "Decision: ${decision.reason}" }

            // Execute the decision
            when {
                decision.shouldPlay && reactionSettings.autoPlay.value -> {
                    log(TAG) { "autoPlay is triggered, sendPlay() - ${decision.reason}" }
                    mediaControl.sendPlay()
                }

                decision.shouldPlay && !reactionSettings.autoPlay.value -> {
                    log(TAG, VERBOSE) { "autoPlay is disabled" }
                }

                decision.shouldPause && reactionSettings.autoPause.value -> {
                    log(TAG) { "autoPause is triggered, sendPause() - ${decision.reason}" }
                    mediaControl.sendPause()
                }

                decision.shouldPause && !reactionSettings.autoPause.value -> {
                    log(TAG, VERBOSE) { "autoPause is disabled" }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    internal fun evaluatePlayPauseAction(
        previous: EarDetectionState,
        current: EarDetectionState,
        onePodMode: Boolean,
        isCurrentlyPlaying: Boolean
    ): PlayPauseDecision = if (onePodMode) {
        evaluateOnePodMode(previous, current, isCurrentlyPlaying)
    } else {
        evaluateNormalMode(previous, current, isCurrentlyPlaying)
    }

    private fun evaluateOnePodMode(
        previous: EarDetectionState,
        current: EarDetectionState,
        isCurrentlyPlaying: Boolean
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
            netChange > 0 && !isCurrentlyPlaying -> PlayPauseDecision(
                shouldPlay = true,
                shouldPause = false,
                reason = "One-pod mode: pod(s) inserted (net change: +$netChange)"
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
        isCurrentlyPlaying: Boolean
    ): PlayPauseDecision {
        val wasWorn = previous.bothInEar
        val isWorn = current.bothInEar

        return when {
            // Transition: not worn → worn, and not playing → play
            !wasWorn && isWorn && !isCurrentlyPlaying -> PlayPauseDecision(
                shouldPlay = true,
                shouldPause = false,
                reason = "Normal mode: both pods in ear"
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

    data class EarDetectionState(
        val leftInEar: Boolean?,    // null for single pod devices
        val rightInEar: Boolean?,   // null for single pod devices
        val isWorn: Boolean         // Always populated
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
            fun fromDualPod(device: HasEarDetectionDual) = fromDualPod(
                left = device.isLeftPodInEar,
                right = device.isRightPodInEar,
            )

            fun fromDualPod(left: Boolean, right: Boolean) = EarDetectionState(
                leftInEar = left,
                rightInEar = right,
                isWorn = left && right
            )

            fun fromSinglePod(device: HasEarDetection) = fromSinglePod(worn = device.isBeingWorn)
            
            fun fromSinglePod(worn: Boolean) = EarDetectionState(
                leftInEar = null,
                rightInEar = null,
                isWorn = worn
            )
        }
    }

    data class PlayPauseDecision(
        val shouldPlay: Boolean,
        val shouldPause: Boolean,
        val reason: String
    )

    companion object {
        private val TAG = logTag("Reaction", "PlayPause")
    }
}