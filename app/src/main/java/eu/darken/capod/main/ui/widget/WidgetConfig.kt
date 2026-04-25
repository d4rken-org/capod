package eu.darken.capod.main.ui.widget

import eu.darken.capod.profiles.core.ProfileId
import kotlinx.serialization.Serializable

@Serializable
data class WidgetConfig(
    val profileId: ProfileId? = null,
    val theme: WidgetTheme = WidgetTheme(),
)
