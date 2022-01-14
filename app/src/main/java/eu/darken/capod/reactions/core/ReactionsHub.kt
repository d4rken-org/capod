package eu.darken.capod.reactions.core

import dagger.Reusable
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@Reusable
class ReactionsHub @Inject constructor(
    private val playPause: PlayPause,
    private val autoConnect: AutoConnect,
) {

    fun monitor(): Flow<Unit> = combine(
        playPause.monitor(),
        autoConnect.monitor()
    ) { _, _ ->
        Unit
    }

    companion object {
        private val TAG = logTag("Reactions", "Hub")
    }
}