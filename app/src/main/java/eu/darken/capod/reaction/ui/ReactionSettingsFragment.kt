package eu.darken.capod.reaction.ui

import android.bluetooth.BluetoothDevice
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
import eu.darken.capod.common.uix.PreferenceFragment3
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.ui.settings.general.DeviceSelectionDialogFactory
import eu.darken.capod.reaction.core.ReactionSettings
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class ReactionSettingsFragment : PreferenceFragment3() {

    override val vm: ReactionSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var reactionSettings: ReactionSettings
    @Inject lateinit var upgradeRepo: UpgradeRepo

    override val settings: ReactionSettings
        get() = reactionSettings

    override val preferenceFile: Int = R.xml.preferences_reactions

    private var isPro: Boolean = false
    private var bondedDevices: List<BluetoothDevice> = emptyList()
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
        if (preference.key == reactionSettings.autoPlay.key && !isPro) {
            preference as CheckBoxPreference
            upgradeRepo.launchBillingFlow(requireActivity())
            preference.isChecked = false
            return true
        } else if (preference.key == reactionSettings.autoPause.key && !isPro) {
            preference as CheckBoxPreference
            upgradeRepo.launchBillingFlow(requireActivity())
            preference.isChecked = false
            return true
        } else if (preference.key == reactionSettings.autoConnect.key) {
            preference as CheckBoxPreference

            if (!isPro) {
                upgradeRepo.launchBillingFlow(requireActivity())
                preference.isChecked = false
                return true
            } else if (generalSettings.mainDeviceAddress.value == null) {
                DeviceSelectionDialogFactory(requireContext()).create(
                    devices = bondedDevices,
                    current = bondedDevices.firstOrNull { it.address == generalSettings.mainDeviceAddress.value }
                ) { selected ->
                    generalSettings.mainDeviceAddress.value = selected?.address
                    if (selected != null) preference.isChecked = true
                }.show()
                preference.isChecked = false
                return true
            }
        } else if (preference.key == reactionSettings.showPopUpOnCaseOpen.key && !isPro) {
            preference as CheckBoxPreference
            upgradeRepo.launchBillingFlow(requireActivity())
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

        vm.isPro.observe2 { isPro = it }

        vm.bondedDevices.observe2 { bondedDevices = it }

        super.onViewCreated(view, savedInstanceState)
    }

}