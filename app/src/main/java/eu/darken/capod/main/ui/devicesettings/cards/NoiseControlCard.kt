package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DoNotDisturbOn
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsPreferenceItem
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.components.icon
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.main.ui.devicesettings.components.SettingsCompoundHeader
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.main.ui.overview.cards.components.AncModeSelector
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.resolvedAncCycleMask
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun NoiseControlCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    enabled: Boolean,
    hasCustomLongPressStemAction: Boolean = false,
    showListeningModeCycleDialog: Boolean = false,
    onShowListeningModeCycleDialogChange: (Boolean) -> Unit = {},
    onAncModeChange: (AapSetting.AncMode.Value) -> Unit = {},
    onNcWithOneAirPodChange: (Boolean) -> Unit = {},
    onAdaptiveAudioNoiseChange: (Int) -> Unit = {},
    onAllowOffOptionChange: (Boolean) -> Unit = {},
    onListeningModeCycleChange: (Int) -> Unit = {},
    onStemActionsClick: () -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    val context = LocalContext.current
    val ancMode = device.ancMode ?: return
    val adaptiveNoise = device.adaptiveAudioNoise
    val cycleMask = device.resolvedAncCycleMask
    val cycleSummary = if (cycleMask != null) listeningModeCycleSummary(context, ancMode.supported, cycleMask) else null
    val cycleSubtitle = if (cycleSummary != null) {
        buildString {
            append(cycleSummary)
            append('\n')
            append(
                context.getString(
                    if (hasCustomLongPressStemAction) {
                        R.string.device_settings_listening_mode_cycle_summary_helper_override
                    } else {
                        R.string.device_settings_listening_mode_cycle_summary_helper
                    }
                ),
            )
        }
    } else {
        null
    }

    SettingsSection(title = stringResource(R.string.device_settings_noise_control_label)) {
        NoiseControlCurrentModeControl(
            currentMode = ancMode.current,
            pendingMode = device.pendingAncMode,
            supportedModes = device.visibleAncModes,
            onModeSelected = onAncModeChange,
            enabled = enabled,
        )
        val ncWithOneAirPod = device.ncWithOneAirPod
        val showNcWithOneAirPod = features.hasNcOneAirpod && ncWithOneAirPod != null
        val hasNoiseExtras = (features.hasAdaptiveAudioNoise && adaptiveNoise != null) ||
            features.hasListeningModeCycle ||
            showNcWithOneAirPod
        if (hasNoiseExtras) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (features.hasListeningModeCycle && cycleMask != null && cycleSubtitle != null) {
            SettingsPreferenceItem(
                icon = Icons.TwoTone.Loop,
                title = stringResource(R.string.device_settings_listening_mode_cycle_label),
                subtitle = cycleSubtitle,
                value = stringResource(
                    if (isPro) {
                        R.string.general_edit_action
                    } else {
                        R.string.general_upgrade_action
                    },
                ),
                onClick = {
                    if (isPro) {
                        onShowListeningModeCycleDialogChange(true)
                    } else {
                        onUpgrade()
                    }
                },
                enabled = if (isPro) enabled else true,
                requiresUpgrade = !isPro,
            )
            if (hasCustomLongPressStemAction) {
                SettingsInfoBox(
                    text = stringResource(R.string.stem_actions_long_press_anc_cycle_info),
                    type = InfoBoxType.INFO,
                    action = {
                        TextButton(onClick = onStemActionsClick) {
                            Text(stringResource(R.string.device_settings_noise_control_open_stem_actions_action))
                        }
                    },
                )
            }
        }
        if (features.hasAllowOffOption) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.DoNotDisturbOn,
                title = stringResource(R.string.device_settings_allow_off_label),
                subtitle = stringResource(R.string.device_settings_allow_off_description),
                checked = device.allowOffOption?.enabled != false,
                onCheckedChange = { onAllowOffOptionChange(it) },
                enabled = if (isPro) enabled else true,
                requiresUpgrade = !isPro,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (showNcWithOneAirPod) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.LooksOne,
                title = stringResource(R.string.device_settings_nc_one_airpod_label),
                subtitle = stringResource(R.string.device_settings_nc_one_airpod_description),
                checked = ncWithOneAirPod.enabled,
                onCheckedChange = onNcWithOneAirPodChange,
                enabled = enabled,
            )
        }
        if (features.hasAdaptiveAudioNoise && adaptiveNoise != null) {
            AdaptiveNoiseSlider(
                level = adaptiveNoise.level,
                onLevelChange = onAdaptiveAudioNoiseChange,
                enabled = enabled,
                isAdaptiveMode = ancMode.current == AapSetting.AncMode.Value.ADAPTIVE
                        || device.pendingAncMode == AapSetting.AncMode.Value.ADAPTIVE,
            )
        }
    }

    if (showListeningModeCycleDialog && features.hasListeningModeCycle && device.isAapConnected) {
        val currentCycleMask = (device.listeningModeCycle ?: AapSetting.ListeningModeCycle(modeMask = 0x0E)).modeMask
        // When Allow Off is confirmed disabled, strip OFF from the dialog entirely so
        // the user can't (re-)introduce it invisibly via a stale cycle-mask bit.
        val allowOffEnabled = device.allowOffOption?.enabled != false
        val dialogSupportedModes = if (allowOffEnabled) {
            ancMode.supported
        } else {
            ancMode.supported - AapSetting.AncMode.Value.OFF
        }
        ListeningModeCycleDialog(
            supportedModes = dialogSupportedModes,
            currentCycleMask = currentCycleMask,
            onSave = { newMask -> onListeningModeCycleChange(newMask) },
            onDismiss = { onShowListeningModeCycleDialogChange(false) },
        )
    }
}

@Composable
private fun NoiseControlCurrentModeControl(
    currentMode: AapSetting.AncMode.Value,
    pendingMode: AapSetting.AncMode.Value?,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    enabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SettingsCompoundHeader(
                icon = Icons.TwoTone.Headphones,
                title = stringResource(R.string.device_settings_noise_control_current_mode_label),
                subtitle = null,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AncModeSelector(
                currentMode = currentMode,
                supportedModes = supportedModes,
                onModeSelected = onModeSelected,
                pendingMode = pendingMode,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AdaptiveNoiseSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean,
    isAdaptiveMode: Boolean,
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    val subtitleRes = if (isAdaptiveMode) {
        R.string.device_settings_adaptive_noise_description
    } else {
        R.string.device_settings_adaptive_noise_requires_adaptive
    }
    SettingsSliderItem(
        icon = Icons.TwoTone.GraphicEq,
        title = stringResource(R.string.device_settings_adaptive_noise_label),
        subtitle = stringResource(subtitleRes),
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 0f..100f,
        steps = 99,
        enabled = enabled && isAdaptiveMode,
        valueLabel = { "${it.toInt()}%" },
    )
}

@Composable
private fun ListeningModeCycleDialog(
    supportedModes: List<AapSetting.AncMode.Value>,
    currentCycleMask: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val supportedMask = supportedModes.fold(0) { mask, mode -> mask or cycleBit(mode) }
    var draftMask by remember(currentCycleMask, supportedMask) {
        mutableIntStateOf(currentCycleMask and supportedMask)
    }
    val selectedCount = Integer.bitCount(draftMask and supportedMask)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.device_settings_listening_mode_cycle_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.device_settings_listening_mode_cycle_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    supportedModes.forEach { mode ->
                        val bit = cycleBit(mode)
                        val isSelected = (draftMask and bit) != 0
                        val canToggle = !isSelected || selectedCount > 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    enabled = canToggle,
                                    role = Role.Checkbox,
                                    onClick = {
                                        draftMask = if (isSelected) {
                                            draftMask and bit.inv()
                                        } else {
                                            draftMask or bit
                                        }
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = canToggle,
                            )
                            Icon(
                                imageVector = mode.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canToggle) 1f else 0.5f),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .align(Alignment.CenterVertically),
                            )
                            Text(
                                text = mode.listeningModeCycleDialogLabel(context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canToggle) 1f else 0.5f),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.device_settings_listening_mode_cycle_minimum),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draftMask)
                    onDismiss()
                },
                enabled = selectedCount >= 2,
            ) {
                Text(text = stringResource(R.string.general_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_cancel_action))
            }
        },
    )
}

private fun cycleBit(mode: AapSetting.AncMode.Value): Int = when (mode) {
    AapSetting.AncMode.Value.OFF -> 0x01
    AapSetting.AncMode.Value.ON -> 0x02
    AapSetting.AncMode.Value.TRANSPARENCY -> 0x04
    AapSetting.AncMode.Value.ADAPTIVE -> 0x08
}

private fun listeningModeCycleSummary(
    context: android.content.Context,
    supportedModes: List<AapSetting.AncMode.Value>,
    cycleMask: Int,
): String = supportedModes
    .filter { mode -> (cycleMask and cycleBit(mode)) != 0 }
    .joinToString(separator = " • ") { it.shortLabel(context) }

private fun AapSetting.AncMode.Value.listeningModeCycleDialogLabel(
    context: android.content.Context,
): String = when (this) {
    AapSetting.AncMode.Value.OFF -> context.getString(R.string.device_settings_listening_mode_cycle_off)
    AapSetting.AncMode.Value.ON -> context.getString(R.string.device_settings_listening_mode_cycle_anc)
    AapSetting.AncMode.Value.TRANSPARENCY -> context.getString(R.string.device_settings_listening_mode_cycle_transparency)
    AapSetting.AncMode.Value.ADAPTIVE -> context.getString(R.string.device_settings_listening_mode_cycle_adaptive)
}

@Preview2
@Composable
private fun NoiseControlCardPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    NoiseControlCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}

@Preview2
@Composable
private fun NoiseControlCardNonProPreview() = PreviewWrapper {
    val state = previewFullState(isPro = false)
    val device = state.device!!
    NoiseControlCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
        enabled = device.isAapReady,
    )
}
