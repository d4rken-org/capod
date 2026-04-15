package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.components.icon
import eu.darken.capod.main.ui.components.shortLabelRes
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = if (pendingMode != null && mode == displayMode) {
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        SegmentedButtonDefaults.colors()
                    },
                    icon = {},
                    label = {
                        val isSelected = mode == displayMode
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (index == 0) 6.dp else 0.dp,
                                    end = if (index == supportedModes.lastIndex) 6.dp else 0.dp,
                                    top = 4.dp,
                                    bottom = 4.dp,
                                ),
                        ) {
                            Icon(
                                imageVector = mode.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(mode.shortLabelRes()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
