package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun SoundCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    enabled: Boolean,
    onPersonalizedVolumeChange: (Boolean) -> Unit = {},
    onToneVolumeChange: (Int) -> Unit = {},
    onMicrophoneModeChange: (AapSetting.MicrophoneMode.Mode) -> Unit = {},
    onUpgrade: () -> Unit = {},
    onOpenIssueTracker: () -> Unit = {},
) {
    val personalizedVol = device.personalizedVolume
    val toneVol = device.toneVolume

    SettingsSection(title = stringResource(R.string.device_settings_category_sound_label)) {
        if (features.hasPersonalizedVolume && personalizedVol != null) {
            SettingsSwitchItem(
                icon = Icons.AutoMirrored.TwoTone.VolumeUp,
                title = stringResource(R.string.device_settings_personalized_volume_label),
                subtitle = stringResource(R.string.device_settings_personalized_volume_description),
                checked = personalizedVol.enabled,
                onCheckedChange = onPersonalizedVolumeChange,
                enabled = enabled,
            )
            if (personalizedVol.enabled) {
                SettingsInfoBox(
                    title = stringResource(R.string.device_settings_experimental_title),
                    text = stringResource(R.string.device_settings_experimental_description),
                    type = InfoBoxType.WARNING,
                    action = {
                        TextButton(onClick = onOpenIssueTracker) {
                            Text(stringResource(R.string.device_settings_experimental_action))
                        }
                    },
                )
            }
        }
        if (features.hasToneVolume && toneVol != null) {
            if (isPro) {
                ToneVolumeSlider(
                    level = toneVol.level,
                    onLevelChange = onToneVolumeChange,
                    enabled = enabled,
                )
            } else {
                SettingsBaseItem(
                    icon = Icons.AutoMirrored.TwoTone.VolumeUp,
                    title = stringResource(R.string.device_settings_tone_volume_label),
                    subtitle = stringResource(R.string.device_settings_tone_volume_description),
                    onClick = onUpgrade,
                    requiresUpgrade = true,
                )
            }
        }
        if (features.hasMicrophoneMode) {
            if (isPro) {
                val micMode = device.microphoneMode
                    ?: AapSetting.MicrophoneMode(AapSetting.MicrophoneMode.Mode.AUTO)
                SegmentedSettingRow(
                    icon = Icons.TwoTone.Mic,
                    title = stringResource(R.string.device_settings_microphone_mode_label),
                    subtitle = stringResource(R.string.device_settings_microphone_mode_description),
                    options = listOf(
                        stringResource(R.string.device_settings_microphone_mode_auto) to AapSetting.MicrophoneMode.Mode.AUTO,
                        stringResource(R.string.device_settings_microphone_mode_left) to AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT,
                        stringResource(R.string.device_settings_microphone_mode_right) to AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT,
                    ),
                    selected = micMode.mode,
                    onSelected = onMicrophoneModeChange,
                    enabled = enabled,
                )
            } else {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Mic,
                    title = stringResource(R.string.device_settings_microphone_mode_label),
                    subtitle = stringResource(R.string.device_settings_microphone_mode_description),
                    onClick = onUpgrade,
                    requiresUpgrade = true,
                )
            }
        }
    }
}

@Composable
private fun ToneVolumeSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean,
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    SettingsSliderItem(
        icon = Icons.AutoMirrored.TwoTone.VolumeUp,
        title = stringResource(R.string.device_settings_tone_volume_label),
        subtitle = stringResource(R.string.device_settings_tone_volume_description),
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 15f..100f,
        steps = 84,
        enabled = enabled,
        valueLabel = { "${it.toInt()}%" },
    )
}

@Preview2
@Composable
private fun SoundCardPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    SoundCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}

@Preview2
@Composable
private fun SoundCardNonProPreview() = PreviewWrapper {
    val state = previewFullState(isPro = false)
    val device = state.device!!
    SoundCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}
