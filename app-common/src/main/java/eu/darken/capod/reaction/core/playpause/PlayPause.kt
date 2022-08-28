package eu.darken.capod.reaction.core.playpause

import dagger.Reusable
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.reaction.core.ReactionSettings
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@Reusable
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
        .flatMapLatest { if (it) bluetoothManager.connectedDevices() else emptyFlow() }
        .flatMapLatest {
            if (it.isEmpty()) {
                log(TAG) { "No known devices connected." }
                emptyFlow()
            } else {
                log(TAG) { "Known devices connected: $it" }
                podMonitor.mainDevice
            }
        }
        .distinctUntilChanged()
        .withPrevious()
        .onEach { (previous, current) ->
            if (previous is HasEarDetection && current is HasEarDetection) {
                log(TAG, VERBOSE) { "previous=${previous.isBeingWorn}, current=${current.isBeingWorn}" }
                log(TAG, VERBOSE) { "previous-id=${previous.identifier}, current-id=${current.identifier}" }
                if (previous.identifier != current.identifier) {
                    return@onEach
                }

                if (previous is HasEarDetectionDual && current is HasEarDetectionDual && reactionSettings.onePodMode.value) {
                    log(TAG) { "Ear status changed for dual pod device in single pod mode." }
                    val previousWorn = previous.isEitherPodInEar
                    val currentWorn = current.isEitherPodInEar
                    if (!previousWorn && currentWorn && !mediaControl.isPlaying) {
                        if (reactionSettings.autoPlay.value) {
                            mediaControl.sendPlay()
                        } else {
                            log(TAG) { "autoPlay is disabled" }
                        }
                    } else if (!currentWorn && previousWorn && mediaControl.isPlaying) {
                        if (reactionSettings.autoPause.value) {
                            mediaControl.sendPause()
                        } else {
                            log(TAG) { "autoPause is disabled" }
                        }
                    }
                } else if (previous.isBeingWorn != current.isBeingWorn) {
                    log(TAG) { "Ear status changed for monitored device." }
                    if (current.isBeingWorn && !mediaControl.isPlaying) {
                        if (reactionSettings.autoPlay.value) {
                            mediaControl.sendPlay()
                        } else {
                            log(TAG) { "autoPlay is disabled" }
                        }
                    } else if (!current.isBeingWorn && mediaControl.isPlaying) {
                        if (reactionSettings.autoPause.value) {
                            mediaControl.sendPause()
                        } else {
                            log(TAG) { "autoPause is disabled" }
                        }
                    }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "PlayPause")
    }
}