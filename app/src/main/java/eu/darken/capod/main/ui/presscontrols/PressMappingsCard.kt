package eu.darken.capod.main.ui.presscontrols

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Looks3
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.LooksTwo
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.UpgradeBadge
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig

@Composable
fun PressMappingsCard(
    stemActions: StemActionsConfig,
    isPro: Boolean,
    onLeftSingle: (StemAction) -> Unit = {},
    onLeftDouble: (StemAction) -> Unit = {},
    onLeftTriple: (StemAction) -> Unit = {},
    onLeftLong: (StemAction) -> Unit = {},
    onRightSingle: (StemAction) -> Unit = {},
    onRightDouble: (StemAction) -> Unit = {},
    onRightTriple: (StemAction) -> Unit = {},
    onRightLong: (StemAction) -> Unit = {},
) {
    SettingsSection {
        MappingsCardHeader(isPro = isPro)
        PressTypeHeader(
            icon = Icons.TwoTone.LooksOne,
            text = stringResource(R.string.press_controls_single_press),
        )
        StemActionRow(
            leftAction = stemActions.leftSingle,
            rightAction = stemActions.rightSingle,
            onLeftChange = onLeftSingle,
            onRightChange = onRightSingle,
        )
        PressTypeHeader(
            icon = Icons.TwoTone.LooksTwo,
            text = stringResource(R.string.press_controls_double_press),
        )
        StemActionRow(
            leftAction = stemActions.leftDouble,
            rightAction = stemActions.rightDouble,
            onLeftChange = onLeftDouble,
            onRightChange = onRightDouble,
        )
        PressTypeHeader(
            icon = Icons.TwoTone.Looks3,
            text = stringResource(R.string.press_controls_triple_press),
        )
        StemActionRow(
            leftAction = stemActions.leftTriple,
            rightAction = stemActions.rightTriple,
            onLeftChange = onLeftTriple,
            onRightChange = onRightTriple,
        )
        PressTypeHeader(
            icon = Icons.TwoTone.Timer,
            text = stringResource(R.string.press_controls_long_press),
        )
        StemActionRow(
            leftAction = stemActions.leftLong,
            rightAction = stemActions.rightLong,
            onLeftChange = onLeftLong,
            onRightChange = onRightLong,
        )
        if (stemActions.leftLong != StemAction.NONE || stemActions.rightLong != StemAction.NONE) {
            SettingsInfoBox(
                text = stringResource(R.string.press_controls_long_press_anc_cycle_info),
                type = InfoBoxType.INFO,
            )
        }
    }
}

@Composable
private fun MappingsCardHeader(isPro: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.press_controls_mappings_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (!isPro) {
            UpgradeBadge()
        }
    }
}

@Composable
private fun PressTypeHeader(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StemActionRow(
    leftAction: StemAction,
    rightAction: StemAction,
    onLeftChange: (StemAction) -> Unit,
    onRightChange: (StemAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.press_controls_left),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StemActionDropdown(
                selected = leftAction,
                onSelected = onLeftChange,
                otherSideAction = rightAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(R.string.press_controls_right),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StemActionDropdown(
                selected = rightAction,
                onSelected = onRightChange,
                otherSideAction = leftAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StemActionDropdown(
    selected: StemAction,
    onSelected: (StemAction) -> Unit,
    otherSideAction: StemAction,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val options = if (otherSideAction == StemAction.NONE) {
        StemAction.entries.filter { it != StemAction.NO_ACTION }
    } else {
        StemAction.entries.toList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (action in options) {
                DropdownMenuItem(
                    text = { Text(action.label()) },
                    onClick = {
                        onSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StemAction.label(): String = when (this) {
    StemAction.NONE -> stringResource(R.string.press_action_none)
    StemAction.NO_ACTION -> stringResource(R.string.press_action_no_action)
    StemAction.PLAY_PAUSE -> stringResource(R.string.press_action_play_pause)
    StemAction.NEXT_TRACK -> stringResource(R.string.press_action_next_track)
    StemAction.PREVIOUS_TRACK -> stringResource(R.string.press_action_previous_track)
    StemAction.VOLUME_UP -> stringResource(R.string.press_action_volume_up)
    StemAction.VOLUME_DOWN -> stringResource(R.string.press_action_volume_down)
}

@Preview2
@Composable
private fun PressMappingsCardProPreview() = PreviewWrapper {
    PressMappingsCard(
        stemActions = StemActionsConfig(
            leftSingle = StemAction.PLAY_PAUSE,
            rightSingle = StemAction.NO_ACTION,
            leftLong = StemAction.NEXT_TRACK,
        ),
        isPro = true,
    )
}

@Preview2
@Composable
private fun PressMappingsCardNonProPreview() = PreviewWrapper {
    PressMappingsCard(
        stemActions = StemActionsConfig(),
        isPro = false,
    )
}
