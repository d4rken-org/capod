package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import eu.darken.capod.main.ui.devicesettings.components.EqMiniBars
import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.math.absoluteValue

@Composable
internal fun SoundCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    enabled: Boolean,
    onPersonalizedVolumeChange: (Boolean) -> Unit = {},
    onToneVolumeChange: (Int) -> Unit = {},
    onMicrophoneModeChange: (AapSetting.MicrophoneMode.Mode) -> Unit = {},
    onEqualizerClick: () -> Unit = {},
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
        if (features.hasMicrophoneMode && device.microphoneMode != null) {
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
        if (features.hasCustomEq) {
            val customEq = device.customEq
            SettingsBaseItem(
                icon = Icons.TwoTone.Tune,
                title = stringResource(R.string.device_settings_equalizer_label),
                subtitle = customEqSubtitle(customEq),
                onClick = if (isPro) onEqualizerClick else onUpgrade,
                requiresUpgrade = !isPro,
                trailingContent = {
                    EqMiniBars(
                        low = customEq?.low ?: CUSTOM_EQ_NEUTRAL,
                        mid = customEq?.mid ?: CUSTOM_EQ_NEUTRAL,
                        high = customEq?.high ?: CUSTOM_EQ_NEUTRAL,
                        isUnconfigured = customEq == null,
                        isEnabled = customEq?.enabled == true,
                    )
                },
            )
        }
    }
}

internal const val CUSTOM_EQ_NEUTRAL = 50

internal enum class CustomEqBand { LOW, MID, HIGH }

/**
 * Bands that deviate from neutral, in low-mid-high order, paired with their signed offset. Empty
 * when every band sits at neutral.
 */
internal fun customEqBandOffsets(eq: AapSetting.CustomEq): List<Pair<CustomEqBand, Int>> = listOf(
    CustomEqBand.LOW to (eq.low - CUSTOM_EQ_NEUTRAL),
    CustomEqBand.MID to (eq.mid - CUSTOM_EQ_NEUTRAL),
    CustomEqBand.HIGH to (eq.high - CUSTOM_EQ_NEUTRAL),
).filter { (_, offset) -> offset != 0 }

@Composable
private fun customEqSubtitle(eq: AapSetting.CustomEq?): String {
    val context = LocalContext.current
    if (eq == null) return stringResource(R.string.device_settings_equalizer_state_unconfigured)
    if (!eq.enabled) return stringResource(R.string.device_settings_equalizer_state_off)

    val offsets = customEqBandOffsets(eq)
    val summary = if (offsets.isEmpty()) {
        context.getString(R.string.device_settings_equalizer_summary_flat)
    } else {
        offsets.joinToString(context.getString(R.string.device_settings_equalizer_summary_separator)) { (band, offset) ->
            val bandLabel = context.getString(
                when (band) {
                    CustomEqBand.LOW -> R.string.device_settings_equalizer_band_low
                    CustomEqBand.MID -> R.string.device_settings_equalizer_band_mid
                    CustomEqBand.HIGH -> R.string.device_settings_equalizer_band_high
                }
            )
            val format = when {
                offset > 0 -> R.string.device_settings_equalizer_band_offset_positive
                else -> R.string.device_settings_equalizer_band_offset_negative
            }
            context.getString(format, bandLabel, offset.absoluteValue)
        }
    }
    return stringResource(
        R.string.device_settings_equalizer_state_custom_summary,
        stringResource(R.string.device_settings_equalizer_state_custom),
        summary,
    )
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
    val state = previewFullState(
        isPro = true,
        customEq = AapSetting.CustomEq(enabled = true, low = 62, mid = 50, high = 42),
    )
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
private fun SoundCardUnconfiguredEqPreview() = PreviewWrapper {
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
    val state = previewFullState(
        isPro = false,
        customEq = AapSetting.CustomEq(enabled = true, low = 62, mid = 50, high = 42),
    )
    val device = state.device!!
    SoundCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}
