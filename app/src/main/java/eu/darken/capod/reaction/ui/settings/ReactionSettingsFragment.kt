package eu.darken.capod.reaction.ui.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.uix.PreferenceFragment2
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.ui.settings.general.DeviceSelectionDialogFactory
import eu.darken.capod.reaction.core.ReactionSettings
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class ReactionSettingsFragment : PreferenceFragment2() {

    private val vm: ReactionSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var reactionSettings: ReactionSettings

    override val settings: ReactionSettings
        get() = reactionSettings

    override val preferenceFile: Int = R.xml.preferences_reactions

    private val autoConnectConditionPref by lazy { findPreference<ListPreference>(settings.autoConnectCondition.key)!! }

    override fun onPreferencesCreated() {
        autoConnectConditionPref.apply {
            entries = AutoConnectCondition.values().map { getString(it.labelRes) }.toTypedArray()
            entryValues = AutoConnectCondition.values().map { settings.autoConnectCondition.rawWriter(it) as String }
                .toTypedArray()
        }
        super.onPreferencesCreated()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == reactionSettings.autoConnect.key && generalSettings.mainDeviceAddress.value == null) {
            preference as CheckBoxPreference

            val devices = vm.bondedDevices
            DeviceSelectionDialogFactory(requireContext()).create(
                devices = devices,
                current = devices.firstOrNull { it.address == generalSettings.mainDeviceAddress.value }
            ) { selected ->
                generalSettings.mainDeviceAddress.value = selected?.address
                if (selected != null) preference.isChecked = true
            }.show()
            preference.isChecked = false
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings.autoConnect.flow.asLiveData().observe2 {
            generalSettings.monitorMode.value = MonitorMode.ALWAYS
            autoConnectConditionPref.isEnabled = it
        }

        super.onViewCreated(view, savedInstanceState)
    }

}