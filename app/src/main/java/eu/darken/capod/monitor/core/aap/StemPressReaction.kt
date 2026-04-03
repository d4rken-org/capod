package eu.darken.capod.monitor.core.aap

import android.view.KeyEvent
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StemPressReaction @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val stemActionSettings: StemActionSettings,
    private val mediaControl: MediaControl,
) {
    fun monitor(): Flow<Unit> = aapManager.stemPressEvents
        .onEach { (_, event) ->
            val action = resolveAction(event)
            log(TAG) { "Stem ${event.bud} ${event.pressType} -> $action" }
            executeAction(action)
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "stemReaction" }

    private suspend fun resolveAction(event: StemPressEvent): StemAction {
        val setting = when (event.bud) {
            StemPressEvent.Bud.LEFT -> when (event.pressType) {
                StemPressEvent.PressType.SINGLE -> stemActionSettings.leftSingle
                StemPressEvent.PressType.DOUBLE -> stemActionSettings.leftDouble
                StemPressEvent.PressType.TRIPLE -> stemActionSettings.leftTriple
                StemPressEvent.PressType.LONG -> stemActionSettings.leftLong
            }
            StemPressEvent.Bud.RIGHT -> when (event.pressType) {
                StemPressEvent.PressType.SINGLE -> stemActionSettings.rightSingle
                StemPressEvent.PressType.DOUBLE -> stemActionSettings.rightDouble
                StemPressEvent.PressType.TRIPLE -> stemActionSettings.rightTriple
                StemPressEvent.PressType.LONG -> stemActionSettings.rightLong
            }
        }
        return setting.flow.first()
    }

    private suspend fun executeAction(action: StemAction) = when (action) {
        StemAction.NONE -> Unit
        StemAction.PLAY_PAUSE -> mediaControl.sendPlayPause()
        StemAction.NEXT_TRACK -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        StemAction.PREVIOUS_TRACK -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        StemAction.VOLUME_UP -> mediaControl.sendKey(KeyEvent.KEYCODE_VOLUME_UP)
        StemAction.VOLUME_DOWN -> mediaControl.sendKey(KeyEvent.KEYCODE_VOLUME_DOWN)
    }

    companion object {
        private val TAG = logTag("Monitor", "StemPressReaction")
    }
}
