package eu.darken.capod.reaction.core.popup

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.reaction.core.ReactionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpReaction @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val podMonitor: PodMonitor,
    private val generalSettings: GeneralSettings,
    private val reactionSettings: ReactionSettings
) {

    fun monitor(): Flow<Unit> = reactionSettings.showPopUpOnCaseOpen.flow
        .flatMapLatest { if (it) podMonitor.mainDevice else emptyFlow() }
        .withPrevious()
        .map { (previous, current) ->

            if (previous is DualApplePods? && current is DualApplePods) {
                log(TAG) { "previous=${previous?.rawCaseLidState}, current=${current.rawCaseLidState}" }
                log(TAG, VERBOSE) { "previous-id=${previous?.identifier}, current-id=${current.identifier}" }

                val isSameDeviceWithCaseNowOpen =
                    previous?.identifier == current.identifier && previous.caseLidState != current.caseLidState
                val isNewDeviceWithJustOpenedCase =
                    previous?.identifier != current.identifier && previous?.caseLidState != current.caseLidState

                if (isSameDeviceWithCaseNowOpen || isNewDeviceWithJustOpenedCase) {
                    log(TAG) { "Case lid status changed for monitored device." }

                    if (current.caseLidState == DualApplePods.LidState.OPEN) {
                        log(TAG, INFO) { "Show popup" }
                    }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "PopUp")
    }
}