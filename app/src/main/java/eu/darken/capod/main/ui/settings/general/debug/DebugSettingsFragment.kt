package eu.darken.capod.main.ui.settings.general.debug

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.observe2
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val logPref = findPreference<Preference>("debug.log.record")!!
        vm.state.observe2(this) {
            logPref.summary = it.currentLogPath?.path
        }
        logPref.setOnPreferenceClickListener {
            vm.toggleRecorder()
            true
        }

        super.onViewCreated(view, savedInstanceState)
    }

}