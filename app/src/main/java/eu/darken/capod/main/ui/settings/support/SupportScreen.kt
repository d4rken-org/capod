package eu.darken.capod.main.ui.settings.support

import android.text.format.Formatter
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.debug.recording.ui.RecorderConsentDialog
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader
@Composable
fun SupportScreenHost(vm: SupportViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    LifecycleResumeEffect(Unit) {
        vm.refreshLogSize()
        onPauseOrDispose {}
    }

    val context = LocalContext.current

    val state by waitForState(vm.state)

    var showShortRecordingWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                SupportViewModel.Event.ShowConsentDialog -> {
                    RecorderConsentDialog(context, WebpageTool(context)).showDialog {
                        vm.startDebugLog()
                    }
                }

                SupportViewModel.Event.ShowShortRecordingWarning -> {
                    showShortRecordingWarning = true
                }
            }
        }
    }

    if (showShortRecordingWarning) {
        ShortRecordingWarningDialog(
            context = context,
            onDismiss = { showShortRecordingWarning = false },
            onStopAnyway = { vm.forceStopDebugLog() },
        )
    }

    state?.let {
        SupportScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onContactDeveloper = { vm.goToContactSupport() },
            onDiscord = { vm.openUrl("https://discord.gg/rrxxng35jq") },
            onIssueTracker = { vm.openUrl("https://github.com/d4rken-org/capod/issues") },
            onWiki = { vm.openUrl("https://github.com/d4rken-org/capod/wiki") },
            onTroubleShooter = { vm.goToTroubleShooter() },
            onDebugLogToggle = { vm.onDebugLogToggle() },
            onClearLogs = { vm.clearDebugLogs() },
        )
    }
}

@Composable
private fun ShortRecordingWarningDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onStopAnyway: () -> Unit,
) {
    LaunchedEffect(Unit) {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.debug_debuglog_short_recording_title)
            setMessage(R.string.debug_debuglog_short_recording_message)
            setPositiveButton(R.string.debug_debuglog_short_recording_continue) { _, _ ->
                onDismiss()
            }
            setNegativeButton(R.string.debug_debuglog_short_recording_stop) { _, _ ->
                onDismiss()
                onStopAnyway()
            }
            setOnCancelListener { onDismiss() }
        }.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportViewModel.State,
    onNavigateUp: () -> Unit,
    onContactDeveloper: () -> Unit,
    onDiscord: () -> Unit,
    onIssueTracker: () -> Unit,
    onWiki: () -> Unit,
    onTroubleShooter: () -> Unit,
    onDebugLogToggle: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_support_label)) },
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
                    title = stringResource(R.string.troubleshooter_title),
                    subtitle = stringResource(R.string.troubleshooter_summary),
                    icon = Icons.TwoTone.Settings,
                    onClick = onTroubleShooter,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_gethelp_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_wiki_label),
                    subtitle = stringResource(R.string.settings_wiki_description),
                    icon = Icons.AutoMirrored.TwoTone.MenuBook,
                    onClick = onWiki,
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
                SettingsBaseItem(
                    title = stringResource(R.string.discord_label),
                    subtitle = stringResource(R.string.discord_description),
                    iconPainter = painterResource(R.drawable.ic_discord_onsurface),
                    onClick = onDiscord,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.support_contact_label),
                    subtitle = stringResource(R.string.support_contact_desc),
                    iconPainter = painterResource(R.drawable.ic_contact_support_24),
                    onClick = onContactDeveloper,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_debug_label))
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
                        stringResource(R.string.support_debuglog_desc)
                    },
                    icon = if (state.isRecording) {
                        Icons.TwoTone.Cancel
                    } else {
                        Icons.TwoTone.BugReport
                    },
                    onClick = onDebugLogToggle,
                )
            }
            if (state.logFolderSize > 0 && !state.isRecording) {
                item {
                    val logSizeFormatted = Formatter.formatShortFileSize(context, state.logFolderSize)
                    SettingsBaseItem(
                        title = stringResource(R.string.support_debuglog_clear_action),
                        subtitle = pluralStringResource(
                            R.plurals.support_debuglog_folder_summary,
                            state.logSessionCount,
                            state.logSessionCount,
                            logSizeFormatted,
                        ),
                        iconPainter = painterResource(R.drawable.ic_delete_sweep_24),
                        onClick = onClearLogs,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun SupportScreenPreview() = PreviewWrapper {
    SupportScreen(
        state = SupportViewModel.State(),
        onNavigateUp = {},
        onContactDeveloper = {},
        onDiscord = {},
        onIssueTracker = {},
        onWiki = {},
        onTroubleShooter = {},
        onDebugLogToggle = {},
        onClearLogs = {},
    )
}
