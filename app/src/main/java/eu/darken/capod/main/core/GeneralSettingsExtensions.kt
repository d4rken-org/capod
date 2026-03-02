package eu.darken.capod.main.core

import eu.darken.capod.common.theming.ThemeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import eu.darken.capod.common.datastore.valueBlocking

val GeneralSettings.themeState: Flow<ThemeState>
    get() = combine(themeMode.flow, themeStyle.flow, themeColor.flow) { mode, style, color ->
        ThemeState(mode, style, color)
    }

val GeneralSettings.currentThemeState: ThemeState
    get() = ThemeState(themeMode.valueBlocking, themeStyle.valueBlocking, themeColor.valueBlocking)
