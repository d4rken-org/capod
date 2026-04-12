package eu.darken.capod.reaction.core.playpause

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.primaryDevice
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayPause @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val mediaControl: MediaControl,
) {

    fun monitor() = deviceMonitor.primaryDevice()
        .map { device -> device?.reactions?.let { it.autoPlay || it.autoPause } == true }
        .distinctUntilChanged()
        .flatMapLatest { shouldMonitor ->
            if (shouldMonitor) bluetoothManager.connectedDevices else emptyFlow()
        }
        .flatMapLatest { connected ->
            if (connected.isEmpty()) {
                log(TAG) { "No known devices connected." }
                emptyFlow()
            } else {
                log(TAG) { "Known devices connected: $connected" }
                deviceMonitor.primaryDevice()
            }
        }
        .distinctUntilChanged()
        .withPrevious()
        .filter { (previous, current) ->
            if (previous == null || current == null) return@filter false
            // Use profileId (stable across BLE address rotations) rather than BLE identifier.
            // Only profiled devices reach this point (outer gate requires profile.autoPlay/autoPause).
            val match = previous.profileId != null && previous.profileId == current.profileId
            if (!match) log(TAG, WARN) { "Main device switched, skipping reaction." }
            match
        }
        .onEach { (previous, current) ->
            log(TAG, VERBOSE) { "Checking\nprevious=$previous\ncurrent=$current" }

            val reactions = current?.reactions
            if (reactions == null) {
                log(TAG, VERBOSE) { "No reactions on current device, skipping reaction" }
                return@onEach
            }

            // Convert to EarDetectionState based on device capabilities
            val prevState: EarDetectionState
            val currState: EarDetectionState

            when {
                previous!!.hasEarDetection && previous.hasDualPods &&
                        current.hasEarDetection && current.hasDualPods -> {
                    // Dual pod devices (AirPods, AirPods Pro, etc.)
                    log(TAG, VERBOSE) {
                        "Dual-pod device: left=${current.isLeftInEar}, right=${current.isRightInEar}"
                    }
                    prevState = EarDetectionState.fromDualPod(
                        left = previous.isLeftInEar ?: false,
                        right = previous.isRightInEar ?: false,
                    )
                    currState = EarDetectionState.fromDualPod(
                        left = current.isLeftInEar ?: false,
                        right = current.isRightInEar ?: false,
                    )
                }

                previous.hasEarDetection && current.hasEarDetection -> {
                    // Single pod devices (AirPods Max, etc.)
                    log(TAG, VERBOSE) { "Single-pod device: worn=${current.isBeingWorn}" }
                    prevState = EarDetectionState.fromSinglePod(worn = previous.isBeingWorn ?: false)
                    currState = EarDetectionState.fromSinglePod(worn = current.isBeingWorn ?: false)
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
                onePodMode = reactions.onePodMode,
                isCurrentlyPlaying = mediaControl.isPlaying
            )

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
                    log(TAG) { "autoPause is triggered, sendPause() - ${decision.reason}" }
                    mediaControl.sendPause()
                }

                decision.shouldPause && !reactions.autoPause -> {
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
