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

            val previousWorn: Boolean?
            val currentWorn: Boolean?

            when {
                reactionSettings.onePodMode.value && previous is HasEarDetectionDual && current is HasEarDetectionDual -> {
                    previousWorn = previous.isEitherPodInEar
                    currentWorn = current.isEitherPodInEar
                    log(TAG, VERBOSE) { "previous: left=${previous.isLeftPodInEar}, right=${previous.isRightPodInEar}" }
                    log(TAG, VERBOSE) { "current: left${current.isLeftPodInEar}, right=${current.isRightPodInEar}" }
                }
                previous is HasEarDetection && current is HasEarDetection -> {
                    previousWorn = previous.isBeingWorn
                    currentWorn = current.isBeingWorn
                    log(TAG, VERBOSE) { "prev.isBeingWorn=${previousWorn}, cur.isBeingWorn=${currentWorn}" }
                }
                else -> {
                    log(TAG, VERBOSE) { "Current devices don't support ear detection." }
                    previousWorn = null
                    currentWorn = null
                }
            }

            if (previousWorn == false && currentWorn == true && !mediaControl.isPlaying) {
                if (reactionSettings.autoPlay.value) {
                    log(TAG) { "autoPlay is triggered, sendPlay()" }
                    mediaControl.sendPlay()
                } else {
                    log(TAG, VERBOSE) { "autoPlay is disabled" }
                }
            } else if (previousWorn == true && currentWorn == false && mediaControl.isPlaying) {
                if (reactionSettings.autoPause.value) {
                    log(TAG) { "autoPause is triggered, sendPause()" }
                    mediaControl.sendPause()
                } else {
                    log(TAG) { "autoPause is disabled" }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "PlayPause")
    }
}