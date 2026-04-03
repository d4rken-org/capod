package eu.darken.capod.reaction.core.stem

import kotlinx.serialization.Serializable

@Serializable
enum class StemAction {
    NONE,
    PLAY_PAUSE,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    VOLUME_UP,
    VOLUME_DOWN,
}
