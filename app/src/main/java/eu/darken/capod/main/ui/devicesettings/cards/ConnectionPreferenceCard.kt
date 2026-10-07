package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun ConnectionPreferenceCard(
    selected: AapSetting.ConnectionPreference.Mode?,
    routingEnabled: Boolean,
    onPreferenceChange: (AapSetting.ConnectionPreference.Mode) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.device_settings_connection_preferences_title)) {
        SegmentedSettingRow<AapSetting.ConnectionPreference.Mode?>(
            icon = Icons.TwoTone.Bluetooth,
            title = stringResource(R.string.device_settings_connection_preference_label),
            subtitle = stringResource(R.string.device_settings_connection_preference_description),
            options = listOf(
                stringResource(R.string.device_settings_connection_automatic) to AapSetting.ConnectionPreference.Mode.AUTOMATIC,
                stringResource(R.string.device_settings_connection_last) to AapSetting.ConnectionPreference.Mode.LAST_CONNECTED,
            ),
            selected = selected,
            onSelected = { it?.let(onPreferenceChange) },
            enabled = routingEnabled,
        )
    }
}
