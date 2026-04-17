package eu.darken.capod.monitor.core.aap

import android.view.KeyEvent
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StemPressReaction @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val mediaControl: MediaControl,
) {
    fun monitor(): Flow<Unit> = aapManager.stemPressEvents
        .onEach { (address, event) ->
            val action = resolveAction(address, event)
            log(TAG) { "Stem ${event.bud} ${event.pressType} @ $address -> $action" }
            executeAction(action)
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "stemReaction" }

    private suspend fun resolveAction(address: String, event: StemPressEvent): StemAction {
        val profile = profilesRepo.profiles.first()
            .filterIsInstance<AppleDeviceProfile>()
            .firstOrNull { it.address == address }
            ?: return StemAction.NONE
        return profile.stemActions.actionFor(event.bud, event.pressType)
    }

    private fun StemActionsConfig.actionFor(bud: StemPressEvent.Bud, pressType: StemPressEvent.PressType): StemAction =
        when (bud) {
            StemPressEvent.Bud.LEFT -> when (pressType) {
                StemPressEvent.PressType.SINGLE -> leftSingle
                StemPressEvent.PressType.DOUBLE -> leftDouble
                StemPressEvent.PressType.TRIPLE -> leftTriple
                StemPressEvent.PressType.LONG -> leftLong
            }

            StemPressEvent.Bud.RIGHT -> when (pressType) {
                StemPressEvent.PressType.SINGLE -> rightSingle
                StemPressEvent.PressType.DOUBLE -> rightDouble
                StemPressEvent.PressType.TRIPLE -> rightTriple
                StemPressEvent.PressType.LONG -> rightLong
            }
        }

    private suspend fun executeAction(action: StemAction) = when (action) {
        StemAction.NONE, StemAction.NO_ACTION -> Unit
        StemAction.PLAY_PAUSE -> mediaControl.sendPlayPause()
        StemAction.NEXT_TRACK -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        StemAction.PREVIOUS_TRACK -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        StemAction.VOLUME_UP -> mediaControl.adjustVolumeUp()
        StemAction.VOLUME_DOWN -> mediaControl.adjustVolumeDown()
    }

    companion object {
        private val TAG = logTag("Monitor", "StemPressReaction")
    }
}
