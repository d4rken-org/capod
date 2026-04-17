package eu.darken.capod.reaction.core.stem

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class StemActionsConfig(
    @SerialName("leftSingle") val leftSingle: StemAction = StemAction.None,
    @SerialName("leftDouble") val leftDouble: StemAction = StemAction.None,
    @SerialName("leftTriple") val leftTriple: StemAction = StemAction.None,
    @SerialName("leftLong") val leftLong: StemAction = StemAction.None,
    @SerialName("rightSingle") val rightSingle: StemAction = StemAction.None,
    @SerialName("rightDouble") val rightDouble: StemAction = StemAction.None,
    @SerialName("rightTriple") val rightTriple: StemAction = StemAction.None,
    @SerialName("rightLong") val rightLong: StemAction = StemAction.None,
) : android.os.Parcelable
