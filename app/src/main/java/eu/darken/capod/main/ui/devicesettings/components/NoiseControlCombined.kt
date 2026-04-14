package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
internal fun NoiseControlCombined(
    currentMode: AapSetting.AncMode.Value,
    pendingMode: AapSetting.AncMode.Value?,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    cycleMask: Int?,
    onCycleMaskChange: (Int) -> Unit,
    onAllowOffChange: (Boolean) -> Unit = {},
    onOffVisibilityChange: (enabled: Boolean, currentCycleMask: Int) -> Unit = { _, _ -> },
    enabled: Boolean,
) {
    val displayMode = pendingMode ?: currentMode
    val cycleBits = mapOf(
        AapSetting.AncMode.Value.OFF to 0x01,
        AapSetting.AncMode.Value.ON to 0x02,
        AapSetting.AncMode.Value.TRANSPARENCY to 0x04,
        AapSetting.AncMode.Value.ADAPTIVE to 0x08,
    )

    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
        for (mode in supportedModes) {
            val isSelected = mode == displayMode
            val bit = cycleBits[mode] ?: continue
            val inCycle = cycleMask?.let { (it and bit) != 0 }
            val cycleCount = cycleMask?.let { Integer.bitCount(it and 0x0F) } ?: 0
            val canRemoveFromCycle = inCycle != true || cycleCount > 2

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Visibility toggle — independent click target
                if (cycleMask != null) {
                    IconButton(
                        onClick = {
                            val isOff = mode == AapSetting.AncMode.Value.OFF
                            if (isOff) {
                                onOffVisibilityChange(inCycle != true, cycleMask)
                            } else if (inCycle == true && canRemoveFromCycle) {
                                onCycleMaskChange(cycleMask xor bit)
                            } else if (inCycle != true) {
                                onCycleMaskChange(cycleMask or bit)
                            }
                        },
                        enabled = enabled && (inCycle != true || canRemoveFromCycle),
                    ) {
                        Icon(
                            imageVector = if (inCycle == true) Icons.TwoTone.Visibility else Icons.TwoTone.VisibilityOff,
                            contentDescription = null,
                            tint = if (inCycle == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
                }

                // Mode selection — label + radio as one click target
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = enabled) { onModeSelected(mode) }
                        .padding(start = if (cycleMask == null) 16.dp else 0.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = mode.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else null,
                        modifier = Modifier.weight(1f),
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        enabled = enabled,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AapSetting.AncMode.Value.label(): String = when (this) {
    AapSetting.AncMode.Value.OFF -> stringResource(R.string.device_settings_listening_mode_cycle_off)
    AapSetting.AncMode.Value.ON -> stringResource(R.string.device_settings_listening_mode_cycle_anc)
    AapSetting.AncMode.Value.TRANSPARENCY -> stringResource(R.string.device_settings_listening_mode_cycle_transparency)
    AapSetting.AncMode.Value.ADAPTIVE -> stringResource(R.string.device_settings_listening_mode_cycle_adaptive)
}

private val ALL_MODES = listOf(
    AapSetting.AncMode.Value.OFF,
    AapSetting.AncMode.Value.ON,
    AapSetting.AncMode.Value.TRANSPARENCY,
    AapSetting.AncMode.Value.ADAPTIVE,
)

@Preview2
@Composable
private fun NoiseControlCombinedProPreview() = PreviewWrapper {
        NoiseControlCombined(
            currentMode = AapSetting.AncMode.Value.ADAPTIVE,
            pendingMode = null,
            supportedModes = ALL_MODES,
            onModeSelected = {},
            cycleMask = 0x0E,
            onCycleMaskChange = {},
            enabled = true,
        )
}
