package eu.darken.capod.reaction.core.autoconnect

import androidx.annotation.StringRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.common.R

@JsonClass(generateAdapter = false)
enum class AutoConnectCondition(
    val identifier: String,
    @StringRes val labelRes: Int
) {
    @Json(name = "autoconnect.condition.seen") WHEN_SEEN(
        "monitor.mode.manual",
        R.string.settings_reaction_autoconnect_whenseen_label
    ),
    @Json(name = "autoconnect.condition.case") CASE_OPEN(
        "autoconnect.condition.case",
        R.string.settings_reaction_autoconnect_caseopen_label
    ),
    @Json(name = "autoconnect.condition.inear") IN_EAR(
        "autoconnect.condition.inear",
        R.string.settings_reaction_autoconnect_inear_label
    ),
}