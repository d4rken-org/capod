package eu.darken.capod.main.ui.settings.general

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AccountTree
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Contrast
import androidx.compose.material.icons.twotone.DarkMode
import androidx.compose.material.icons.twotone.DisabledVisible
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.SettingsBluetooth
import androidx.compose.material.icons.automirrored.twotone.ViewList
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsListPreferenceItem
import eu.darken.capod.common.settings.ThemeColorSelectorDialog
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeState
import eu.darken.capod.common.theming.ThemeStyle
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
            onThemeModeSelected = { mode -> vm.setThemeMode(mode) },
            onThemeStyleSelected = { style -> vm.setThemeStyle(style) },
            onThemeColorSelected = { color -> vm.setThemeColor(color) },
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
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onThemeStyleSelected: (ThemeStyle) -> Unit = {},
    onThemeColorSelected: (ThemeColor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMonitorModeDialog by remember { mutableStateOf(false) }
    var showScannerModeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    val isMaterialYouActive = state.themeState.style == ThemeStyle.MATERIAL_YOU &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_general_label)) },
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
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_appearance_label))
            }
            item {
                SettingsListPreferenceItem(
                    icon = Icons.TwoTone.DarkMode,
                    title = stringResource(R.string.ui_theme_mode_label),
                    entries = ThemeMode.entries,
                    selectedEntry = state.themeState.mode,
                    onEntrySelected = onThemeModeSelected,
                    entryLabel = { stringResource(it.labelRes) },
                )
            }
            item {
                SettingsListPreferenceItem(
                    icon = Icons.TwoTone.Contrast,
                    title = stringResource(R.string.ui_theme_style_label),
                    entries = ThemeStyle.entries,
                    selectedEntry = state.themeState.style,
                    onEntrySelected = onThemeStyleSelected,
                    entryLabel = { stringResource(it.labelRes) },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.ui_theme_color_label),
                    subtitle = if (isMaterialYouActive) {
                        stringResource(R.string.ui_theme_color_disabled_materialyou)
                    } else {
                        stringResource(state.themeState.color.labelRes)
                    },
                    icon = Icons.TwoTone.Palette,
                    onClick = { showColorDialog = true },
                    enabled = !isMaterialYouActive,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_monitor_mode_label),
                    subtitle = stringResource(state.monitorMode.labelRes),
                    icon = Icons.TwoTone.DisabledVisible,
                    onClick = { showMonitorModeDialog = true },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_scanner_mode_label),
                    subtitle = stringResource(state.scannerMode.labelRes),
                    icon = Icons.TwoTone.SettingsBluetooth,
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
                    icon = Icons.TwoTone.Notifications,
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
                    icon = Icons.AutoMirrored.TwoTone.Message,
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
                    icon = Icons.TwoTone.BugReport,
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
                    icon = Icons.TwoTone.FilterList,
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
                    icon = Icons.AutoMirrored.TwoTone.ViewList,
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
                    icon = Icons.TwoTone.AccountTree,
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

    if (showColorDialog) {
        ThemeColorSelectorDialog(
            selectedColor = state.themeState.color,
            onColorSelected = {
                onThemeColorSelected(it)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreview() = PreviewWrapper {
    GeneralSettingsScreen(
        state = GeneralSettingsViewModel.State(
            monitorMode = MonitorMode.AUTOMATIC,
            scannerMode = ScannerMode.BALANCED,
            showConnectedNotification = true,
            keepNotificationAfterDisconnect = false,
            isOffloadedFilteringDisabled = false,
            isOffloadedBatchingDisabled = false,
            useIndirectScanResultCallback = false,
            themeState = ThemeState(),
        ),
        onNavigateUp = {},
        onMonitorModeSelected = {},
        onScannerModeSelected = {},
        onShowConnectedNotificationChanged = {},
        onKeepNotificationAfterDisconnectChanged = {},
        onDebugSettings = {},
        onOffloadedFilteringDisabledChanged = {},
        onOffloadedBatchingDisabledChanged = {},
        onUseIndirectScanResultCallbackChanged = {},
    )
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
