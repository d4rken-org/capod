package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun VolumeSwipeLengthSetting(
    selected: AapSetting.VolumeSwipeLength.Value,
    onSelected: (AapSetting.VolumeSwipeLength.Value) -> Unit,
    enabled: Boolean,
) {
    SegmentedSettingRow(
        icon = Icons.TwoTone.Swipe,
        title = stringResource(R.string.device_settings_volume_swipe_length_label),
        subtitle = stringResource(R.string.device_settings_volume_swipe_length_description),
        options = listOf(
            stringResource(R.string.device_settings_volume_swipe_length_default) to AapSetting.VolumeSwipeLength.Value.DEFAULT,
            stringResource(R.string.device_settings_volume_swipe_length_longer) to AapSetting.VolumeSwipeLength.Value.LONGER,
            stringResource(R.string.device_settings_volume_swipe_length_longest) to AapSetting.VolumeSwipeLength.Value.LONGEST,
        ),
        selected = selected,
        onSelected = onSelected,
        enabled = enabled,
    )
}

@Preview2
@Composable
private fun VolumeSwipeLengthSettingPreview() = PreviewWrapper {
    VolumeSwipeLengthSetting(
        selected = AapSetting.VolumeSwipeLength.Value.DEFAULT,
        onSelected = {},
        enabled = true,
    )
}
