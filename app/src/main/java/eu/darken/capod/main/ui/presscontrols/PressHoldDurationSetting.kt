package eu.darken.capod.main.ui.presscontrols

import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun PressHoldDurationSetting(
    selected: AapSetting.PressHoldDuration.Value,
    onSelected: (AapSetting.PressHoldDuration.Value) -> Unit,
    enabled: Boolean,
) {
    SegmentedSettingRow(
        icon = Icons.TwoTone.Timer,
        title = stringResource(R.string.device_settings_press_hold_label),
        subtitle = stringResource(R.string.device_settings_press_hold_description),
        options = listOf(
            stringResource(R.string.device_settings_press_hold_default) to AapSetting.PressHoldDuration.Value.DEFAULT,
            stringResource(R.string.device_settings_press_hold_shorter) to AapSetting.PressHoldDuration.Value.SHORTER,
            stringResource(R.string.device_settings_press_hold_shortest) to AapSetting.PressHoldDuration.Value.SHORTEST,
        ),
        selected = selected,
        onSelected = onSelected,
        enabled = enabled,
    )
}

@Preview2
@Composable
private fun PressHoldDurationSettingPreview() = PreviewWrapper {
    PressHoldDurationSetting(
        selected = AapSetting.PressHoldDuration.Value.DEFAULT,
        onSelected = {},
        enabled = true,
    )
}
