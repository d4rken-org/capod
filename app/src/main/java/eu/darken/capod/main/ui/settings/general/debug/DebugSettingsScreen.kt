package eu.darken.capod.main.ui.settings.general.debug

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.DataArray
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem

@Composable
fun DebugSettingsScreenHost(vm: DebugSettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        DebugSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDebugModeChanged = { enabled -> vm.setDebugModeEnabled(enabled) },
            onShowFakeDataChanged = { enabled -> vm.setShowFakeData(enabled) },
            onShowUnfilteredChanged = { enabled -> vm.setShowUnfiltered(enabled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    state: DebugSettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onShowFakeDataChanged: (Boolean) -> Unit,
    onShowUnfilteredChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_debug_label)) },
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
                SettingsBaseItem(
                    title = stringResource(R.string.settings_debug_mode_label),
                    subtitle = stringResource(R.string.settings_debug_mode_description),
                    icon = Icons.TwoTone.BugReport,
                    onClick = { onDebugModeChanged(!state.isDebugModeEnabled) },
                    trailingContent = {
                        Switch(
                            checked = state.isDebugModeEnabled,
                            onCheckedChange = onDebugModeChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_fake_data_label),
                    subtitle = stringResource(R.string.settings_fake_data_description),
                    icon = Icons.TwoTone.DataArray,
                    onClick = { onShowFakeDataChanged(!state.showFakeData) },
                    trailingContent = {
                        Switch(
                            checked = state.showFakeData,
                            onCheckedChange = onShowFakeDataChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_blescanner_unfiltered_label),
                    subtitle = stringResource(R.string.settings_blescanner_unfiltered_description),
                    icon = Icons.TwoTone.DevicesOther,
                    onClick = { onShowUnfilteredChanged(!state.showUnfiltered) },
                    trailingContent = {
                        Switch(
                            checked = state.showUnfiltered,
                            onCheckedChange = onShowUnfilteredChanged,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
        }
    }
}
