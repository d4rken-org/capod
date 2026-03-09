package eu.darken.capod.reaction.core.autoconnect

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AutoConnectCondition(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @SerialName("autoconnect.condition.seen") WHEN_SEEN(
        "monitor.mode.manual",
        R.string.settings_reaction_autoconnect_whenseen_label
    ),
    @SerialName("autoconnect.condition.case") CASE_OPEN(
        "autoconnect.condition.case",
        R.string.settings_reaction_autoconnect_caseopen_label
    ),
    @SerialName("autoconnect.condition.inear") IN_EAR(
        "autoconnect.condition.inear",
        R.string.settings_reaction_autoconnect_inear_label
    ),
}