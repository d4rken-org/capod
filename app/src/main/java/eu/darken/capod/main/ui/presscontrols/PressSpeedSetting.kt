package eu.darken.capod.main.ui.presscontrols

import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun PressSpeedSetting(
    selected: AapSetting.PressSpeed.Value,
    onSelected: (AapSetting.PressSpeed.Value) -> Unit,
    enabled: Boolean,
) {
    SegmentedSettingRow(
        icon = Icons.TwoTone.Speed,
        title = stringResource(R.string.device_settings_press_speed_label),
        subtitle = stringResource(R.string.device_settings_press_speed_description),
        options = listOf(
            stringResource(R.string.device_settings_press_speed_default) to AapSetting.PressSpeed.Value.DEFAULT,
            stringResource(R.string.device_settings_press_speed_slower) to AapSetting.PressSpeed.Value.SLOWER,
            stringResource(R.string.device_settings_press_speed_slowest) to AapSetting.PressSpeed.Value.SLOWEST,
        ),
        selected = selected,
        onSelected = onSelected,
        enabled = enabled,
    )
}

@Preview2
@Composable
private fun PressSpeedSettingPreview() = PreviewWrapper {
    PressSpeedSetting(
        selected = AapSetting.PressSpeed.Value.DEFAULT,
        onSelected = {},
        enabled = true,
    )
}
