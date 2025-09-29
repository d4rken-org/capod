package eu.darken.capod.main.ui.settings.general

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.preferences.PercentSliderPreference
import eu.darken.capod.common.toHex
import eu.darken.capod.common.uix.PreferenceFragment3
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class GeneralSettingsFragment : PreferenceFragment3() {

    override val vm: GeneralSettingsFragmentVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var webpageTool: WebpageTool

    override val settings: GeneralSettings
        get() = generalSettings

    override val preferenceFile: Int = R.xml.preferences_general

    private val monitorModePref by lazy { findPreference<ListPreference>(generalSettings.monitorMode.key)!! }
    private val scanModePref by lazy { findPreference<ListPreference>(generalSettings.scannerMode.key)!! }

    override fun onPreferencesCreated() {
        monitorModePref.apply {
            entries = MonitorMode.entries.map { getString(it.labelRes) }.toTypedArray()
            entryValues = MonitorMode.entries.map { settings.monitorMode.rawWriter(it) as String }.toTypedArray()
        }
        scanModePref.apply {
            entries = ScannerMode.entries.map { getString(it.labelRes) }.toTypedArray()
            entryValues = ScannerMode.entries.map { settings.scannerMode.rawWriter(it) as String }.toTypedArray()
        }
        super.onPreferencesCreated()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (PercentSliderPreference.onDisplayPreferenceDialog(this, preference)) return

        super.onDisplayPreferenceDialog(preference)
    }
}