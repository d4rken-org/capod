package eu.darken.capod.main.ui.settings.support

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader

@Composable
fun SupportScreenHost(vm: SupportViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SupportScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDiscord = { vm.openUrl("https://discord.gg/rrxxng35jq") },
            onIssueTracker = { vm.openUrl("https://github.com/d4rken-org/capod/issues") },
            onTroubleShooter = { vm.goToTroubleShooter() },
            onDebugLogToggle = {
                if (it.isRecording) vm.stopDebugLog()
                else vm.startDebugLog()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportViewModel.State,
    onNavigateUp: () -> Unit,
    onDiscord: () -> Unit,
    onIssueTracker: () -> Unit,
    onTroubleShooter: () -> Unit,
    onDebugLogToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_support_label)) },
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
                    title = stringResource(R.string.discord_label),
                    subtitle = stringResource(R.string.discord_description),
                    iconPainter = painterResource(R.drawable.ic_discord_onsurface),
                    onClick = onDiscord,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.issue_tracker_label),
                    subtitle = stringResource(R.string.issue_tracker_description),
                    iconPainter = painterResource(R.drawable.ic_github_onsurface),
                    onClick = onIssueTracker,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.troubleshooter_title),
                    subtitle = stringResource(R.string.troubleshooter_summary),
                    iconPainter = painterResource(R.drawable.ic_baseline_settings_24),
                    onClick = onTroubleShooter,
                )
            }
            item {
                SettingsBaseItem(
                    title = if (state.isRecording) {
                        stringResource(R.string.debug_debuglog_stop_action)
                    } else {
                        stringResource(R.string.debug_debuglog_record_action)
                    },
                    subtitle = if (state.isRecording) {
                        state.currentLogPath?.path
                    } else {
                        stringResource(R.string.debug_debuglog_record_action)
                    },
                    iconPainter = if (state.isRecording) {
                        painterResource(R.drawable.ic_cancel)
                    } else {
                        painterResource(R.drawable.ic_baseline_bug_report_24)
                    },
                    onClick = onDebugLogToggle,
                )
            }
        }
    }
}
