package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.SettingsPreferenceItem
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.components.VolumeSwipeLengthSetting
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun ControlsCard(
    device: PodDevice,
    features: PodModel.Features,
    enabled: Boolean,
    onPressControlsClick: () -> Unit = {},
    onVolumeSwipeChange: (Boolean) -> Unit = {},
    onVolumeSwipeLengthChange: (AapSetting.VolumeSwipeLength.Value) -> Unit = {},
) {
    val volSwipe = device.volumeSwipe
    val volSwipeLen = device.volumeSwipeLength

    val showPressControlsNav = (features.hasStemConfig && device.stemConfig != null) ||
            (features.hasPressSpeed && device.pressSpeed != null) ||
            (features.hasPressHoldDuration && device.pressHoldDuration != null) ||
            (features.hasEndCallMuteMic && device.endCallMuteMic != null)

    SettingsSection(title = stringResource(R.string.device_settings_category_controls_label)) {
        if (showPressControlsNav) {
            SettingsPreferenceItem(
                icon = Icons.TwoTone.TouchApp,
                title = stringResource(R.string.press_controls_title),
                subtitle = stringResource(R.string.press_controls_nav_description),
                onClick = onPressControlsClick,
                enabled = enabled,
            )
        }
        if (features.hasVolumeSwipe && volSwipe != null) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.Swipe,
                title = stringResource(R.string.device_settings_volume_swipe_label),
                subtitle = stringResource(R.string.device_settings_volume_swipe_description),
                checked = volSwipe.enabled,
                onCheckedChange = onVolumeSwipeChange,
                enabled = enabled,
            )
        }
        if (features.hasVolumeSwipeLength && volSwipeLen != null) {
            VolumeSwipeLengthSetting(
                selected = volSwipeLen.value,
                onSelected = onVolumeSwipeLengthChange,
                enabled = enabled,
            )
        }
    }
}

@Preview2
@Composable
private fun ControlsCardPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    ControlsCard(
        device = device,
        features = device.model.features,
        enabled = device.isAapReady,
    )
}

@Preview2
@Composable
private fun ControlsCardNonProPreview() = PreviewWrapper {
    val state = previewFullState(isPro = false)
    val device = state.device!!
    ControlsCard(
        device = device,
        features = device.model.features,
        enabled = device.isAapReady,
    )
}
