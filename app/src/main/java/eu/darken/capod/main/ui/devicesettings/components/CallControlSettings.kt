package eu.darken.capod.main.ui.devicesettings.components

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
import androidx.compose.material.icons.twotone.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun CallControlSettings(
    current: AapSetting.EndCallMuteMic,
    onChange: (AapSetting.EndCallMuteMic.MuteMicMode, AapSetting.EndCallMuteMic.EndCallMode) -> Unit,
    enabled: Boolean,
) {
    val optionASelected = current.endCall == AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SettingsCompoundHeader(
                icon = Icons.TwoTone.TouchApp,
                title = stringResource(R.string.device_settings_end_call_mute_mic_label),
                subtitle = stringResource(R.string.device_settings_end_call_mute_mic_description),
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.selectableGroup()) {
                CallControlOption(
                    title = stringResource(R.string.device_settings_end_call_mute_mic_option_a_title),
                    subtitle = stringResource(R.string.device_settings_end_call_mute_mic_option_a_subtitle),
                    selected = optionASelected,
                    enabled = enabled,
                    onClick = {
                        onChange(
                            AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS,
                            AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS,
                        )
                    },
                )

                CallControlOption(
                    title = stringResource(R.string.device_settings_end_call_mute_mic_option_b_title),
                    subtitle = stringResource(R.string.device_settings_end_call_mute_mic_option_b_subtitle),
                    selected = !optionASelected,
                    enabled = enabled,
                    onClick = {
                        onChange(
                            AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS,
                            AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CallControlOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { if (!selected) onClick() },
            )
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
    }
}

@Preview2
@Composable
private fun CallControlSettingsPreview() = PreviewWrapper {
    CallControlSettings(
        current = AapSetting.EndCallMuteMic(
            muteMic = AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS,
            endCall = AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS,
        ),
        onChange = { _, _ -> },
        enabled = true,
    )
}
