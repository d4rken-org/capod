package eu.darken.capod.reactions.core

import dagger.Reusable
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.HasEarDetection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@Reusable
class PlayPause @Inject constructor(
    private val podMonitor: PodMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val generalSettings: GeneralSettings,
    private val mediaControl: MediaControl,
) {

    fun monitor() = bluetoothManager.connectedDevices()
        .flatMapLatest {
            if (it.isEmpty()) {
                log(TAG) { "No known devices connected." }
                emptyFlow()
            } else {
                log(TAG) { "Known devices connected: $it" }
                podMonitor.mainDevice
            }
        }
        .setupCommonEventHandlers(TAG) { "podReactions" }
        .distinctUntilChanged()
        .withPrevious()
        .onEach { (previous, current) ->
            if (previous is HasEarDetection && current is HasEarDetection) {
                log(TAG) { "previous=${previous.isBeingWorn}, current=${current.isBeingWorn}" }
                log(TAG) { "previous-id=${previous.identifier}, current-id=${current.identifier}" }
                if (previous.identifier == current.identifier && previous.isBeingWorn != current.isBeingWorn) {
                    if (current.isBeingWorn && generalSettings.autoPlay.value && !mediaControl.isPlaying) {
                        mediaControl.sendPlay()
                    } else if (!current.isBeingWorn && generalSettings.autoPause.value && mediaControl.isPlaying) {
                        mediaControl.sendPause()
                    }
                }
            }
        }

    companion object {
        private val TAG = logTag("Reactions", "PlayPause")
    }
}