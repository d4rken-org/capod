package eu.darken.capod.main.ui.settings.general

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.main.core.MonitorMode

@Composable
fun GeneralSettingsScreenHost(vm: GeneralSettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        GeneralSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onMonitorModeSelected = { mode -> vm.setMonitorMode(mode) },
            onScannerModeSelected = { mode -> vm.setScannerMode(mode) },
            onShowConnectedNotificationChanged = { enabled -> vm.setShowConnectedNotification(enabled) },
            onKeepNotificationAfterDisconnectChanged = { enabled -> vm.setKeepNotificationAfterDisconnect(enabled) },
            onDebugSettings = { vm.goToDebugSettings() },
            onOffloadedFilteringDisabledChanged = { disabled -> vm.setOffloadedFilteringDisabled(disabled) },
            onOffloadedBatchingDisabledChanged = { disabled -> vm.setOffloadedBatchingDisabled(disabled) },
            onUseIndirectScanResultCallbackChanged = { enabled -> vm.setUseIndirectScanResultCallback(enabled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onMonitorModeSelected: (MonitorMode) -> Unit,
    onScannerModeSelected: (ScannerMode) -> Unit,
    onShowConnectedNotificationChanged: (Boolean) -> Unit,
    onKeepNotificationAfterDisconnectChanged: (Boolean) -> Unit,
    onDebugSettings: () -> Unit,
    onOffloadedFilteringDisabledChanged: (Boolean) -> Unit,
    onOffloadedBatchingDisabledChanged: (Boolean) -> Unit,
    onUseIndirectScanResultCallbackChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMonitorModeDialog by remember { mutableStateOf(false) }
    var showScannerModeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_general_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_monitor_mode_label),
                    subtitle = stringResource(state.monitorMode.labelRes),
                    iconPainter = painterResource(R.drawable.ic_baseline_disabled_visible_24),
                    onClick = { showMonitorModeDialog = true },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_scanner_mode_label),
                    subtitle = stringResource(state.scannerMode.labelRes),
                    iconPainter = painterResource(R.drawable.ic_baseline_settings_bluetooth_24),
                    onClick = { showScannerModeDialog = true },
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_monitor_connected_notification_label),
                    subtitle = stringResource(R.string.settings_monitor_connected_notification_description),
                    iconPainter = painterResource(R.drawable.ic_checkbox_blank_badge_24),
                    onClick = { onShowConnectedNotificationChanged(!state.showConnectedNotification) },
                    trailingContent = {
                        Switch(
                            checked = state.showConnectedNotification,
                            onCheckedChange = onShowConnectedNotificationChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_keep_notification_after_disconnect_label),
                    subtitle = stringResource(R.string.settings_keep_notification_after_disconnect_description),
                    iconPainter = painterResource(R.drawable.ic_message_24),
                    onClick = { onKeepNotificationAfterDisconnectChanged(!state.keepNotificationAfterDisconnect) },
                    enabled = state.showConnectedNotification,
                    trailingContent = {
                        Switch(
                            checked = state.keepNotificationAfterDisconnect,
                            onCheckedChange = onKeepNotificationAfterDisconnectChanged,
                            enabled = state.showConnectedNotification,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_debug_label),
                    subtitle = stringResource(R.string.settings_debug_description),
                    iconPainter = painterResource(R.drawable.ic_baseline_bug_report_24),
                    onClick = onDebugSettings,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_compatibility_options_title))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_compat_offloaded_filtering_disabled_title),
                    subtitle = stringResource(R.string.settings_compat_offloaded_filtering_disabled_summary),
                    iconPainter = painterResource(R.drawable.ic_filter_cog_outline_24),
                    onClick = { onOffloadedFilteringDisabledChanged(!state.isOffloadedFilteringDisabled) },
                    trailingContent = {
                        Switch(
                            checked = state.isOffloadedFilteringDisabled,
                            onCheckedChange = onOffloadedFilteringDisabledChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_compat_offloaded_batching_disabled_title),
                    subtitle = stringResource(R.string.settings_compat_offloaded_batching_disabled_summary),
                    iconPainter = painterResource(R.drawable.ic_format_list_group_24),
                    onClick = { onOffloadedBatchingDisabledChanged(!state.isOffloadedBatchingDisabled) },
                    trailingContent = {
                        Switch(
                            checked = state.isOffloadedBatchingDisabled,
                            onCheckedChange = onOffloadedBatchingDisabledChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_compat_indirectcallback_title),
                    subtitle = stringResource(R.string.settings_compat_indirectcallback_summary),
                    iconPainter = painterResource(R.drawable.ic_strategy_24),
                    onClick = { onUseIndirectScanResultCallbackChanged(!state.useIndirectScanResultCallback) },
                    trailingContent = {
                        Switch(
                            checked = state.useIndirectScanResultCallback,
                            onCheckedChange = onUseIndirectScanResultCallbackChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
        }
    }

    if (showMonitorModeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.settings_monitor_mode_label),
            entries = MonitorMode.entries,
            selectedEntry = state.monitorMode,
            onEntrySelected = {
                onMonitorModeSelected(it)
                showMonitorModeDialog = false
            },
            entryLabel = { stringResource(it.labelRes) },
            onDismiss = { showMonitorModeDialog = false },
        )
    }

    if (showScannerModeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.settings_scanner_mode_label),
            entries = ScannerMode.entries,
            selectedEntry = state.scannerMode,
            onEntrySelected = {
                onScannerModeSelected(it)
                showScannerModeDialog = false
            },
            entryLabel = { stringResource(it.labelRes) },
            onDismiss = { showScannerModeDialog = false },
        )
    }
}

@Composable
private fun <T> ListPreferenceDialog(
    title: String,
    entries: List<T>,
    selectedEntry: T,
    onEntrySelected: (T) -> Unit,
    entryLabel: @Composable (T) -> String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(Modifier.selectableGroup()) {
                entries.forEach { entry ->
                    val isSelected = entry == selectedEntry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onEntrySelected(entry) },
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
                            text = entryLabel(entry),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}
