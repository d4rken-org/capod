package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

/**
 * Apple's "Optimized Charge Limit" toggle (AAP setting 0x3B), shown for models that advertise
 * [PodModel.Features.hasDynamicEndOfCharge]. The wire format follows the Apple-bool convention
 * used by every other boolean setting.
 */
@Composable
internal fun BatteryCard(
    device: PodDevice,
    features: PodModel.Features,
    enabled: Boolean,
    onDynamicEndOfChargeChange: (Boolean) -> Unit = {},
) {
    if (!features.hasDynamicEndOfCharge) return
    val cap = device.dynamicEndOfCharge ?: return

    SettingsSection(title = stringResource(R.string.device_settings_category_battery_label)) {
        SettingsSwitchItem(
            icon = Icons.TwoTone.BatteryChargingFull,
            title = stringResource(R.string.device_settings_charge_cap_label),
            subtitle = stringResource(R.string.device_settings_charge_cap_description),
            checked = cap.enabled,
            onCheckedChange = onDynamicEndOfChargeChange,
            enabled = enabled,
        )
    }
}

@Preview2
@Composable
private fun BatteryCardEnabledPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    BatteryCard(
        device = device,
        features = PodModel.Features(hasDynamicEndOfCharge = true),
        enabled = true,
    )
}

@Preview2
@Composable
private fun BatteryCardDisabledPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    BatteryCard(
        device = device,
        features = PodModel.Features(hasDynamicEndOfCharge = true),
        enabled = false,
    )
}
