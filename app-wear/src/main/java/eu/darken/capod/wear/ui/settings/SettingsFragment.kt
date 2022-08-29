package eu.darken.capod.wear.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.SettingsFragmentBinding


@AndroidEntryPoint
class SettingsFragment : Fragment3(R.layout.settings_fragment) {

    override val vm: SettingsFragmentVM by viewModels()
    override val ui: SettingsFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.appVersion.text = BuildConfigWrap.VERSION_DESCRIPTION_TINY
        super.onViewCreated(view, savedInstanceState)
    }
}
