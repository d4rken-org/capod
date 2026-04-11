package eu.darken.capod.reaction.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition

@Composable
fun ReactionSettingsScreenHost(vm: ReactionSettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)

    state?.let {
        ReactionSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onOnePodModeChanged = { enabled -> vm.setOnePodMode(enabled) },
            onAutoPlayChanged = { enabled -> vm.setAutoPlay(enabled) },
            onAutoPauseChanged = { enabled -> vm.setAutoPause(enabled) },
            onAutoConnectChanged = { enabled -> vm.setAutoConnect(enabled) },
            onAutoConnectConditionSelected = { condition -> vm.setAutoConnectCondition(condition) },
            onShowPopUpOnCaseOpenChanged = { enabled -> vm.setShowPopUpOnCaseOpen(enabled) },
            onShowPopUpOnConnectionChanged = { enabled -> vm.setShowPopUpOnConnection(enabled) },
        )
    }
}

@Composable
fun ReactionSettingsScreen(
    state: ReactionSettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onOnePodModeChanged: (Boolean) -> Unit,
    onAutoPlayChanged: (Boolean) -> Unit,
    onAutoPauseChanged: (Boolean) -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
    onAutoConnectConditionSelected: (AutoConnectCondition) -> Unit,
    onShowPopUpOnCaseOpenChanged: (Boolean) -> Unit,
    onShowPopUpOnConnectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAutoConnectConditionDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_reaction_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_autopplay_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_onepod_mode_label),
                    subtitle = stringResource(R.string.settings_onepod_mode_description),
                    icon = Icons.TwoTone.LooksOne,
                    onClick = { onOnePodModeChanged(!state.onePodMode) },
                    trailingContent = {
                        Switch(
                            checked = state.onePodMode,
                            onCheckedChange = onOnePodModeChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PlayCircle,
                    title = stringResource(R.string.settings_autopplay_label),
                    subtitle = stringResource(R.string.settings_autoplay_description),
                    checked = state.autoPlay,
                    onCheckedChange = onAutoPlayChanged,
                    requiresUpgrade = !state.isPro,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PauseCircle,
                    title = stringResource(R.string.settings_autopause_label),
                    subtitle = stringResource(R.string.settings_autopause_description),
                    checked = state.autoPause,
                    onCheckedChange = onAutoPauseChanged,
                    requiresUpgrade = !state.isPro,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_eardetection_info_label),
                    subtitle = stringResource(R.string.settings_eardetection_info_description),
                    icon = Icons.TwoTone.QuestionMark,
                    onClick = {},
                    enabled = false,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_autoconnect_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.BluetoothConnected,
                    title = stringResource(R.string.settings_autoconnect_label),
                    subtitle = stringResource(R.string.settings_autoconnect_description),
                    checked = state.autoConnect,
                    onCheckedChange = onAutoConnectChanged,
                    requiresUpgrade = !state.isPro,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_autoconnect_condition_label),
                    subtitle = stringResource(state.autoConnectCondition.labelRes),
                    icon = Icons.TwoTone.Workspaces,
                    onClick = { if (state.autoConnect) showAutoConnectConditionDialog = true },
                    enabled = state.autoConnect,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.TwoTone.Message,
                    title = stringResource(R.string.settings_popup_caseopen_label),
                    subtitle = stringResource(R.string.settings_popup_caseopen_description),
                    checked = state.showPopUpOnCaseOpen,
                    onCheckedChange = onShowPopUpOnCaseOpenChanged,
                    requiresUpgrade = !state.isPro,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.AutoMirrored.TwoTone.Message,
                    title = stringResource(R.string.settings_popup_connected_label),
                    subtitle = stringResource(R.string.settings_popup_connected_description),
                    checked = state.showPopUpOnConnection,
                    onCheckedChange = onShowPopUpOnConnectionChanged,
                    requiresUpgrade = !state.isPro,
                )
            }
        }
    }

    if (showAutoConnectConditionDialog) {
        AlertDialog(
            onDismissRequest = { showAutoConnectConditionDialog = false },
            title = { Text(text = stringResource(R.string.settings_autoconnect_condition_label)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AutoConnectCondition.entries.forEach { condition ->
                        val isSelected = condition == state.autoConnectCondition
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onAutoConnectConditionSelected(condition)
                                        showAutoConnectConditionDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(condition.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoConnectConditionDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun ReactionSettingsScreenPreview() = PreviewWrapper {
    ReactionSettingsScreen(
        state = ReactionSettingsViewModel.State(
            isPro = true,
            onePodMode = false,
            autoPlay = true,
            autoPause = true,
            autoConnect = false,
            autoConnectCondition = AutoConnectCondition.WHEN_SEEN,
            showPopUpOnCaseOpen = true,
            showPopUpOnConnection = false,
        ),
        onNavigateUp = {},
        onOnePodModeChanged = {},
        onAutoPlayChanged = {},
        onAutoPauseChanged = {},
        onAutoConnectChanged = {},
        onAutoConnectConditionSelected = {},
        onShowPopUpOnCaseOpenChanged = {},
        onShowPopUpOnConnectionChanged = {},
    )
}

@Preview2
@Composable
private fun ReactionSettingsScreenNonProPreview() = PreviewWrapper {
    ReactionSettingsScreen(
        state = ReactionSettingsViewModel.State(
            isPro = false,
            onePodMode = false,
            autoPlay = false,
            autoPause = false,
            autoConnect = false,
            autoConnectCondition = AutoConnectCondition.WHEN_SEEN,
            showPopUpOnCaseOpen = false,
            showPopUpOnConnection = false,
        ),
        onNavigateUp = {},
        onOnePodModeChanged = {},
        onAutoPlayChanged = {},
        onAutoPauseChanged = {},
        onAutoConnectChanged = {},
        onAutoConnectConditionSelected = {},
        onShowPopUpOnCaseOpenChanged = {},
        onShowPopUpOnConnectionChanged = {},
    )
}
