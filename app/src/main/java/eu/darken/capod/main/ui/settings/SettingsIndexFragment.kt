package eu.darken.capod.main.ui.settings

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.MainDirections
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.common.uix.PreferenceFragment2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import javax.inject.Inject

@AndroidEntryPoint
class SettingsIndexFragment : PreferenceFragment2() {

    @Inject lateinit var generalSettings: GeneralSettings
    override val settings: Settings
        get() = generalSettings
    override val preferenceFile: Int = R.xml.preferences_index

    @Inject lateinit var webpageTool: WebpageTool
    @Inject lateinit var upgradeRepo: UpgradeRepo

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu(R.menu.menu_settings_index) { item ->
            when (item.itemId) {
                R.id.menu_item_sponsor -> {
                    upgradeRepo.getSponsorUrl()?.let { webpageTool.open(it) }
                }
            }
        }
        toolbar.menu?.findItem(R.id.menu_item_sponsor)?.isVisible = !upgradeRepo.getSponsorUrl().isNullOrEmpty()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferencesCreated() {
        findPreference<Preference>("core.changelog")!!.summary = BuildConfigWrap.VERSION_DESCRIPTION
        findPreference<Preference>("core.privacy")!!.setOnPreferenceClickListener {
            webpageTool.open(PrivacyPolicy.URL)
            true
        }
        findPreference<Preference>("core.profile.manager")!!.setOnPreferenceClickListener {
            findNavController().navigate(MainDirections.actionGlobalDeviceManagerFragment())
            true
        }

        super.onPreferencesCreated()
    }
}