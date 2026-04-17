package eu.darken.capod.reaction.core.stem

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface StemAction : Parcelable {
    @Parcelize @Serializable @SerialName("NONE") data object None : StemAction
    @Parcelize @Serializable @SerialName("NO_ACTION") data object NoAction : StemAction
    @Parcelize @Serializable @SerialName("PLAY_PAUSE") data object PlayPause : StemAction
    @Parcelize @Serializable @SerialName("NEXT_TRACK") data object NextTrack : StemAction
    @Parcelize @Serializable @SerialName("PREVIOUS_TRACK") data object PreviousTrack : StemAction
    @Parcelize @Serializable @SerialName("VOLUME_UP") data object VolumeUp : StemAction
    @Parcelize @Serializable @SerialName("VOLUME_DOWN") data object VolumeDown : StemAction
    @Parcelize @Serializable @SerialName("STOP") data object Stop : StemAction
    @Parcelize @Serializable @SerialName("FAST_FORWARD") data object FastForward : StemAction
    @Parcelize @Serializable @SerialName("REWIND") data object Rewind : StemAction
    @Parcelize @Serializable @SerialName("MUTE_TOGGLE") data object MuteToggle : StemAction
    @Parcelize @Serializable @SerialName("CYCLE_ANC") data object CycleAnc : StemAction
    @Parcelize @Serializable @SerialName("TOGGLE_ANC_TRANSPARENCY") data object ToggleAncTransparency : StemAction

    companion object {
        val all: List<StemAction> = listOf(
            None,
            PlayPause, NextTrack, PreviousTrack,
            VolumeUp, VolumeDown, MuteToggle,
            Stop, FastForward, Rewind,
            CycleAnc, ToggleAncTransparency,
            NoAction,
        )
    }
}
