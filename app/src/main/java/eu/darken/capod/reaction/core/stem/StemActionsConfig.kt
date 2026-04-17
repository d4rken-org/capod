package eu.darken.capod.reaction.core.stem

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class StemActionsConfig(
    @SerialName("leftSingle") val leftSingle: StemAction = StemAction.NONE,
    @SerialName("leftDouble") val leftDouble: StemAction = StemAction.NONE,
    @SerialName("leftTriple") val leftTriple: StemAction = StemAction.NONE,
    @SerialName("leftLong") val leftLong: StemAction = StemAction.NONE,
    @SerialName("rightSingle") val rightSingle: StemAction = StemAction.NONE,
    @SerialName("rightDouble") val rightDouble: StemAction = StemAction.NONE,
    @SerialName("rightTriple") val rightTriple: StemAction = StemAction.NONE,
    @SerialName("rightLong") val rightLong: StemAction = StemAction.NONE,
) : android.os.Parcelable
