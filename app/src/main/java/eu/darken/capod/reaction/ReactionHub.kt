package eu.darken.capod.reaction

import dagger.Reusable
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.reaction.autoconnect.AutoConnect
import eu.darken.capod.reaction.playpause.PlayPause
import eu.darken.capod.reaction.popup.PopUpReaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@Reusable
class ReactionHub @Inject constructor(
    private val playPause: PlayPause,
    private val autoConnect: AutoConnect,
    private val popUpReaction: PopUpReaction,
) {

    fun monitor(): Flow<Unit> = combine(
        playPause.monitor(),
        autoConnect.monitor(),
        popUpReaction.monitor()
    ) { _, _, _ ->
        Unit
    }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "Hub")
    }
}