package eu.darken.capod.reaction.core.charged

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which battery slots the charged notification watches. */
@Serializable
enum class ChargedSlotScope(
    @StringRes val labelRes: Int,
) {
    @SerialName("charged.scope.pods") PODS(R.string.settings_charged_scope_pods_label),
    @SerialName("charged.scope.case") CASE(R.string.settings_charged_scope_case_label),
    @SerialName("charged.scope.podsandcase") PODS_AND_CASE(R.string.settings_charged_scope_both_label),
}
