package eu.darken.capod.main.ui.stemactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.reaction.core.stem.StemAction

@Composable
fun StemActionConfigScreenHost(
    vm: StemActionConfigViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    StemActionConfigScreen(
        state = currentState,
        onNavigateUp = { vm.navUp() },
        onReset = { vm.resetAll() },
        onLeftSingle = { vm.setLeftSingle(it) },
        onLeftDouble = { vm.setLeftDouble(it) },
        onLeftTriple = { vm.setLeftTriple(it) },
        onLeftLong = { vm.setLeftLong(it) },
        onRightSingle = { vm.setRightSingle(it) },
        onRightDouble = { vm.setRightDouble(it) },
        onRightTriple = { vm.setRightTriple(it) },
        onRightLong = { vm.setRightLong(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StemActionConfigScreen(
    state: StemActionConfigViewModel.State,
    onNavigateUp: () -> Unit,
    onReset: () -> Unit = {},
    onLeftSingle: (StemAction) -> Unit = {},
    onLeftDouble: (StemAction) -> Unit = {},
    onLeftTriple: (StemAction) -> Unit = {},
    onLeftLong: (StemAction) -> Unit = {},
    onRightSingle: (StemAction) -> Unit = {},
    onRightDouble: (StemAction) -> Unit = {},
    onRightTriple: (StemAction) -> Unit = {},
    onRightLong: (StemAction) -> Unit = {},
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { Text(stringResource(R.string.stem_actions_reset_confirm_message)) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stem_actions_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.RestartAlt,
                            contentDescription = stringResource(R.string.stem_actions_reset_label),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item("description") {
                Text(
                    text = stringResource(R.string.stem_actions_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Press type rows with left/right dropdowns
            item("single_header") {
                SettingsCategoryHeader(text = stringResource(R.string.stem_actions_single_press))
            }
            item("single_row") {
                StemActionRow(
                    leftAction = state.leftSingle,
                    rightAction = state.rightSingle,
                    onLeftChange = onLeftSingle,
                    onRightChange = onRightSingle,
                )
            }

            item("double_header") {
                SettingsCategoryHeader(text = stringResource(R.string.stem_actions_double_press))
            }
            item("double_row") {
                StemActionRow(
                    leftAction = state.leftDouble,
                    rightAction = state.rightDouble,
                    onLeftChange = onLeftDouble,
                    onRightChange = onRightDouble,
                )
            }

            item("triple_header") {
                SettingsCategoryHeader(text = stringResource(R.string.stem_actions_triple_press))
            }
            item("triple_row") {
                StemActionRow(
                    leftAction = state.leftTriple,
                    rightAction = state.rightTriple,
                    onLeftChange = onLeftTriple,
                    onRightChange = onRightTriple,
                )
            }

            item("long_header") {
                SettingsCategoryHeader(text = stringResource(R.string.stem_actions_long_press))
            }
            item("long_row") {
                StemActionRow(
                    leftAction = state.leftLong,
                    rightAction = state.rightLong,
                    onLeftChange = onLeftLong,
                    onRightChange = onRightLong,
                )
            }

            if (state.leftLong != StemAction.NONE || state.rightLong != StemAction.NONE) {
                item("long_anc_cycle_info") {
                    SettingsInfoBox(
                        text = stringResource(R.string.stem_actions_long_press_anc_cycle_info),
                        type = InfoBoxType.INFO,
                    )
                }
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
                text = stringResource(R.string.stem_actions_left),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stem_actions_right),
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
    StemAction.NONE -> stringResource(R.string.stem_action_none)
    StemAction.NO_ACTION -> stringResource(R.string.stem_action_no_action)
    StemAction.PLAY_PAUSE -> stringResource(R.string.stem_action_play_pause)
    StemAction.NEXT_TRACK -> stringResource(R.string.stem_action_next_track)
    StemAction.PREVIOUS_TRACK -> stringResource(R.string.stem_action_previous_track)
    StemAction.VOLUME_UP -> stringResource(R.string.stem_action_volume_up)
    StemAction.VOLUME_DOWN -> stringResource(R.string.stem_action_volume_down)
}
