package eu.darken.capod.main.ui.settings.general

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationEntry
import javax.inject.Inject

class GeneralSettingsNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<Nav.Settings.General> { GeneralSettingsScreenHost() }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds
        @IntoSet
        abstract fun bind(entry: GeneralSettingsNavigation): NavigationEntry
    }
}
