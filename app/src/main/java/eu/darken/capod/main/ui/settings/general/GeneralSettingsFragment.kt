package eu.darken.capod.main.ui.settings.general

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.preferences.PercentSliderPreference
import eu.darken.capod.common.uix.PreferenceFragment2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.ScannerMode
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class GeneralSettingsFragment : PreferenceFragment2() {

    private val vm: GeneralSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var upgradeRepo: UpgradeRepo

    override val settings: GeneralSettings
        get() = generalSettings

    override val preferenceFile: Int = R.xml.preferences_general

    private val monitorModePref by lazy { findPreference<ListPreference>(generalSettings.monitorMode.key)!! }
    private val scanModePref by lazy { findPreference<ListPreference>(generalSettings.scannerMode.key)!! }
    private val mainDeviceAddressPref by lazy { findPreference<Preference>(generalSettings.mainDeviceAddress.key)!! }

    override fun onPreferencesCreated() {
        monitorModePref.apply {
            entries = MonitorMode.values().map { getString(it.labelRes) }.toTypedArray()
            entryValues = MonitorMode.values().map { settings.monitorMode.rawWriter(it) as String }.toTypedArray()
        }
        scanModePref.apply {
            entries = ScannerMode.values().map { getString(it.labelRes) }.toTypedArray()
            entryValues = ScannerMode.values().map { settings.scannerMode.rawWriter(it) as String }.toTypedArray()
        }
        super.onPreferencesCreated()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.bondedDevices.observe2 { devices ->
            mainDeviceAddressPref.setOnPreferenceClickListener {
                val dialog = DeviceSelectionDialogFactory(requireContext()).create(
                    devices,
                    devices.firstOrNull { it.address == generalSettings.mainDeviceAddress.value }
                ) { selected ->
                    generalSettings.mainDeviceAddress.value = selected?.address
                }
                dialog.show()
                true
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val isPro = runBlocking { upgradeRepo.isPro() }
        if (!isPro && preference.key == generalSettings.showAll.key) {
            upgradeRepo.launchBillingFlow(requireActivity())
            preference as CheckBoxPreference
            preference.isChecked = false
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (PercentSliderPreference.onDisplayPreferenceDialog(this, preference)) return

        super.onDisplayPreferenceDialog(preference)
    }
}