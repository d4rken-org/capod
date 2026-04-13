package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun AncModeSelector(
    modifier: Modifier = Modifier,
    currentMode: AapSetting.AncMode.Value,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    pendingMode: AapSetting.AncMode.Value? = null,
    enabled: Boolean = true,
) {
    val displayMode = pendingMode ?: currentMode
    Box(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            supportedModes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == displayMode,
                    onClick = { onModeSelected(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, supportedModes.size),
                    colors = if (pendingMode != null && mode == displayMode) {
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        SegmentedButtonDefaults.colors()
                    },
                    label = {
                        Text(
                            text = when (mode) {
                                AapSetting.AncMode.Value.OFF -> stringResource(R.string.anc_mode_off)
                                AapSetting.AncMode.Value.ON -> stringResource(R.string.anc_mode_on)
                                AapSetting.AncMode.Value.TRANSPARENCY -> stringResource(R.string.anc_mode_transparency)
                                AapSetting.AncMode.Value.ADAPTIVE -> stringResource(R.string.anc_mode_adaptive)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AncModeSelectorPreview() = PreviewWrapper {
    AncModeSelector(
        currentMode = AapSetting.AncMode.Value.ON,
        supportedModes = listOf(
            AapSetting.AncMode.Value.OFF,
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
            AapSetting.AncMode.Value.ADAPTIVE,
        ),
        onModeSelected = {},
    )
}

@Preview2
@Composable
private fun AncModeSelectorPendingPreview() = PreviewWrapper {
    AncModeSelector(
        currentMode = AapSetting.AncMode.Value.ON,
        supportedModes = listOf(
            AapSetting.AncMode.Value.OFF,
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
        ),
        onModeSelected = {},
        pendingMode = AapSetting.AncMode.Value.TRANSPARENCY,
    )
}
