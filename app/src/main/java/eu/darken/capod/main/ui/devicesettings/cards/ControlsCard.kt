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
import eu.darken.capod.main.ui.devicesettings.components.CallControlSettings
import eu.darken.capod.main.ui.devicesettings.components.PressHoldDurationSetting
import eu.darken.capod.main.ui.devicesettings.components.PressSpeedSetting
import eu.darken.capod.main.ui.devicesettings.components.VolumeSwipeLengthSetting
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun ControlsCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    enabled: Boolean,
    onStemActionsClick: () -> Unit = {},
    onEndCallMuteMicChange: (AapSetting.EndCallMuteMic.MuteMicMode, AapSetting.EndCallMuteMic.EndCallMode) -> Unit = { _, _ -> },
    onPressSpeedChange: (AapSetting.PressSpeed.Value) -> Unit = {},
    onPressHoldDurationChange: (AapSetting.PressHoldDuration.Value) -> Unit = {},
    onVolumeSwipeChange: (Boolean) -> Unit = {},
    onVolumeSwipeLengthChange: (AapSetting.VolumeSwipeLength.Value) -> Unit = {},
) {
    val pressSpd = device.pressSpeed
    val pressHold = device.pressHoldDuration
    val volSwipe = device.volumeSwipe
    val volSwipeLen = device.volumeSwipeLength
    val endCallMuteMic = device.endCallMuteMic

    SettingsSection(title = stringResource(R.string.device_settings_category_controls_label)) {
        if (features.hasStemConfig) {
            SettingsPreferenceItem(
                icon = Icons.TwoTone.TouchApp,
                title = stringResource(R.string.stem_actions_title),
                subtitle = stringResource(R.string.stem_actions_nav_description),
                onClick = onStemActionsClick,
                enabled = enabled,
                requiresUpgrade = !isPro,
            )
        }
        if (features.hasEndCallMuteMic && endCallMuteMic != null) {
            CallControlSettings(
                current = endCallMuteMic,
                onChange = onEndCallMuteMicChange,
                enabled = enabled,
            )
        }
        if (features.hasPressSpeed && pressSpd != null) {
            PressSpeedSetting(
                selected = pressSpd.value,
                onSelected = onPressSpeedChange,
                enabled = enabled,
            )
        }
        if (features.hasPressHoldDuration && pressHold != null) {
            PressHoldDurationSetting(
                selected = pressHold.value,
                onSelected = onPressHoldDurationChange,
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
        isPro = state.isPro,
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
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}
