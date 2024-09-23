package eu.darken.capod.main.ui.settings.general.debug

import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.uix.PreferenceFragment3
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class DebugSettingsFragment : PreferenceFragment3() {

    override val vm: DebugSettingsFragmentVM by viewModels()

    @Inject lateinit var debugSettings: DebugSettings

    override val settings: DebugSettings
        get() = debugSettings

    override val preferenceFile: Int = R.xml.preferences_debug

}