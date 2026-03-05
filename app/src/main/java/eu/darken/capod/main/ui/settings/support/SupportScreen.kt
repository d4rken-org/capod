package eu.darken.capod.main.ui.settings.support

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FiberManualRecord
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.common.debug.recording.core.DebugSession
import eu.darken.capod.common.debug.recording.ui.RecorderActivity
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
        vm.refreshSessions()
        onPauseOrDispose {}
    }

    val context = LocalContext.current

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)

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

                is SupportViewModel.Event.OpenRecorderActivity -> {
                    val intent = RecorderActivity.getLaunchIntent(context, event.sessionId, event.legacyPath).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
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
            onOpenSession = { vm.openSession(it) },
            onDeleteSession = { id ->
                MaterialAlertDialogBuilder(context).apply {
                    setTitle(R.string.support_debuglog_session_delete_title)
                    setMessage(R.string.support_debuglog_session_delete_message)
                    setPositiveButton(R.string.profiles_delete_action) { _, _ ->
                        vm.deleteSession(id)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
            },
            onStopRecording = { vm.onDebugLogToggle() },
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
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStopRecording: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSessionsSheet by remember { mutableStateOf(false) }

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
            if (state.sessions.isNotEmpty()) {
                item {
                    val nonRecordingSessions = state.sessions.count { it !is DebugSession.Recording }
                    val logSizeFormatted = Formatter.formatShortFileSize(context, state.logFolderSize)
                    SettingsBaseItem(
                        title = stringResource(R.string.support_debuglog_sessions_label),
                        subtitle = if (nonRecordingSessions > 0) {
                            pluralStringResource(
                                R.plurals.support_debuglog_folder_summary,
                                nonRecordingSessions,
                                nonRecordingSessions,
                                logSizeFormatted,
                            )
                        } else {
                            stringResource(R.string.support_debuglog_sessions_desc)
                        },
                        iconPainter = painterResource(R.drawable.ic_delete_sweep_24),
                        onClick = { showSessionsSheet = true },
                    )
                }
            }
        }
    }

    if (showSessionsSheet) {
        DebugSessionsBottomSheet(
            sessions = state.sessions,
            onDismiss = { showSessionsSheet = false },
            onOpenSession = onOpenSession,
            onDeleteSession = onDeleteSession,
            onStopRecording = onStopRecording,
            onClearAll = {
                showSessionsSheet = false
                onClearLogs()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSessionsBottomSheet(
    sessions: List<DebugSession>,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStopRecording: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.support_debuglog_sessions_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (sessions.any { it !is DebugSession.Recording }) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_sweep_24),
                            contentDescription = stringResource(R.string.support_debuglog_clear_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.support_debuglog_sessions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        context = context,
                        onOpen = { onOpenSession(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                        onStop = onStopRecording,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: DebugSession,
    context: android.content.Context,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (session is DebugSession.Ready) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading icon
        when (session) {
            is DebugSession.Recording -> Icon(
                imageVector = Icons.TwoTone.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            is DebugSession.Compressing -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            is DebugSession.Ready -> Icon(
                imageVector = Icons.TwoTone.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            is DebugSession.Failed -> Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (session) {
                    is DebugSession.Recording -> stringResource(R.string.support_debuglog_session_recording)
                    is DebugSession.Compressing -> stringResource(R.string.support_debuglog_session_compressing)
                    is DebugSession.Ready -> Formatter.formatShortFileSize(context, session.diskSize)
                    is DebugSession.Failed -> when (session.reason) {
                        DebugSession.Failed.Reason.EMPTY_LOG -> stringResource(R.string.support_debuglog_failed_empty_log)
                        DebugSession.Failed.Reason.MISSING_LOG -> stringResource(R.string.support_debuglog_failed_missing_log)
                        DebugSession.Failed.Reason.CORRUPT_ZIP -> stringResource(R.string.support_debuglog_failed_corrupt_zip)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Trailing action
        when (session) {
            is DebugSession.Recording -> {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.TwoTone.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is DebugSession.Compressing -> {
                // No action during compression
            }

            is DebugSession.Ready -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is DebugSession.Failed -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        onOpenSession = {},
        onDeleteSession = {},
        onStopRecording = {},
        onClearLogs = {},
    )
}
