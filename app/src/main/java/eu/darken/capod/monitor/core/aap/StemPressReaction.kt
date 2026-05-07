package eu.darken.capod.monitor.core.aap

import android.view.KeyEvent
import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
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
            executeAction(address, action)
        }
        .map { }
        .setupCommonEventHandlers(TAG) { "stemReaction" }

    private suspend fun resolveAction(address: String, event: StemPressEvent): StemAction {
        val profile = profileFor(address) ?: return StemAction.None
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

    private suspend fun executeAction(address: String, action: StemAction) {
        when (action) {
            is StemAction.None, is StemAction.NoAction -> Unit
            is StemAction.PlayPause -> mediaControl.sendPlayPause()
            is StemAction.NextTrack -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            is StemAction.PreviousTrack -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            is StemAction.VolumeUp -> mediaControl.adjustVolumeUp()
            is StemAction.VolumeDown -> mediaControl.adjustVolumeDown()
            is StemAction.Stop -> mediaControl.sendStop()
            is StemAction.FastForward -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            is StemAction.Rewind -> mediaControl.sendKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            is StemAction.MuteToggle -> mediaControl.toggleMuteMusic()
            is StemAction.CycleAnc -> cycleAnc(address)
            is StemAction.ToggleAncTransparency -> toggleAncTransparency(address)
        }
    }

    private suspend fun profileFor(address: String): AppleDeviceProfile? = profilesRepo.profiles.first()
        .filterIsInstance<AppleDeviceProfile>()
        .firstOrNull { it.address == address }

    private suspend fun cycleAnc(address: String) {
        val profile = profileFor(address) ?: return
        if (!profile.model.features.hasListeningModeCycle) return
        val aapState = aapManager.allStates.first()[address] ?: return
        val state = resolveEffectiveAncState(aapState, profile) ?: return
        val next = nextGestureAncMode(state) ?: return
        sendAncCommand(address, next)
    }

    private suspend fun toggleAncTransparency(address: String) {
        val profile = profileFor(address) ?: return
        val aapState = aapManager.allStates.first()[address] ?: return
        val state = resolveEffectiveAncState(aapState, profile) ?: return
        val supported = state.supported
        val target = if (state.current == AapSetting.AncMode.Value.TRANSPARENCY) {
            supported.firstOrNull { it == AapSetting.AncMode.Value.ON }
                ?: supported.firstOrNull { it == AapSetting.AncMode.Value.ADAPTIVE }
                ?: supported.firstOrNull { it == AapSetting.AncMode.Value.OFF }
                ?: return
        } else {
            if (AapSetting.AncMode.Value.TRANSPARENCY in supported) AapSetting.AncMode.Value.TRANSPARENCY else return
        }
        sendAncCommand(address, target)
    }

    private suspend fun sendAncCommand(address: String, mode: AapSetting.AncMode.Value) {
        try {
            aapManager.sendCommand(address, AapCommand.SetAncMode(mode))
        } catch (e: Exception) {
            log(TAG, WARN) { "SetAncMode($mode) failed for $address: $e" }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "StemPressReaction")
    }
}
