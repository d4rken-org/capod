package eu.darken.capod.reaction.core

import dagger.Reusable
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.reaction.core.autoconnect.AutoConnect
import eu.darken.capod.reaction.core.playpause.PlayPause
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@Reusable
class ReactionHub @Inject constructor(
    private val playPause: PlayPause,
    private val autoConnect: AutoConnect,
) {

    fun monitor(): Flow<Unit> = combine(
        playPause.monitor(),
        autoConnect.monitor()
    ) { _, _ ->
        Unit
    }
        .setupCommonEventHandlers(TAG) { "monitor" }

    companion object {
        private val TAG = logTag("Reaction", "Hub")
    }
}