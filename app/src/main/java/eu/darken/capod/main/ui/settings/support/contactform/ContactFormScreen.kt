package eu.darken.capod.main.ui.settings.support.contactform

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R
import eu.darken.capod.common.WebpageTool
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.common.debug.recording.ui.RecorderConsentDialog
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.main.ui.settings.support.contactform.ContactFormViewModel.Category

@Composable
fun ContactFormScreenHost(vm: ContactFormViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LifecycleResumeEffect(Unit) {
        vm.refreshLogSessions()
        onPauseOrDispose {}
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showShortRecordingWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ContactFormViewModel.Event.OpenEmail -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.support_contact_no_email_app)
                        )
                    }
                }

                is ContactFormViewModel.Event.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                ContactFormViewModel.Event.ShowConsentDialog -> {
                    RecorderConsentDialog(context, WebpageTool(context)).showDialog {
                        vm.doStartRecording()
                    }
                }

                ContactFormViewModel.Event.ShowShortRecordingWarning -> {
                    showShortRecordingWarning = true
                }
            }
        }
    }

    if (showShortRecordingWarning) {
        LaunchedEffect(Unit) {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(R.string.debug_debuglog_short_recording_title)
                setMessage(R.string.debug_debuglog_short_recording_message)
                setPositiveButton(R.string.debug_debuglog_short_recording_continue) { _, _ ->
                    showShortRecordingWarning = false
                }
                setNegativeButton(R.string.debug_debuglog_short_recording_stop) { _, _ ->
                    showShortRecordingWarning = false
                    vm.forceStopRecording()
                }
                setOnCancelListener { showShortRecordingWarning = false }
            }.show()
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    state?.let {
        ContactFormScreen(
            state = it,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onCategoryChange = { vm.updateCategory(it) },
            onDescriptionChange = { vm.updateDescription(it) },
            onExpectedChange = { vm.updateExpectedBehavior(it) },
            onSelectSession = { vm.selectLogSession(it) },
            onDeleteSession = { id ->
                MaterialAlertDialogBuilder(context).apply {
                    setTitle(R.string.support_contact_debuglog_delete_title)
                    setMessage(R.string.support_contact_debuglog_delete_message)
                    setPositiveButton(R.string.profiles_delete_action) { _, _ ->
                        vm.deleteLogSession(id)
                    }
                    setNegativeButton(R.string.general_cancel_action) { _, _ -> }
                }.show()
            },
            onStartRecording = { vm.startRecording() },
            onStopRecording = { vm.stopRecording() },
            onSend = { vm.send() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactFormScreen(
    state: ContactFormViewModel.State,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onCategoryChange: (Category) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onExpectedChange: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.support_contact_label)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Category Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        icon = { Icon(painterResource(R.drawable.ic_description_24), null, Modifier.size(20.dp)) },
                        title = stringResource(R.string.support_contact_category_label),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.category == Category.QUESTION,
                            onClick = { onCategoryChange(Category.QUESTION) },
                            label = { Text(stringResource(R.string.support_contact_category_question_label)) },
                        )
                        FilterChip(
                            selected = state.category == Category.FEATURE,
                            onClick = { onCategoryChange(Category.FEATURE) },
                            label = { Text(stringResource(R.string.support_contact_category_feature_label)) },
                        )
                        FilterChip(
                            selected = state.category == Category.BUG,
                            onClick = { onCategoryChange(Category.BUG) },
                            label = { Text(stringResource(R.string.support_contact_category_bug_label)) },
                        )
                    }
                }
            }

            // Debug Log Card (only for Bug)
            if (state.isBug) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = {
                                Icon(
                                    Icons.TwoTone.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            title = stringResource(R.string.support_contact_debuglog_label),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.support_contact_debuglog_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.sessions.isEmpty() && !state.isRecording) {
                            Text(
                                text = stringResource(R.string.support_contact_debuglog_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            state.sessions.forEach { session ->
                                val isSelected = state.selectedSessionId == session.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectSession(session.id) },
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp),
                                    ) {
                                        Text(
                                            text = session.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val sizeText = Formatter.formatShortFileSize(context, session.diskSize)
                                        val agoText = DateUtils.getRelativeTimeSpanString(
                                            session.createdAt,
                                            System.currentTimeMillis(),
                                            DateUtils.SECOND_IN_MILLIS,
                                            DateUtils.FORMAT_ABBREV_RELATIVE,
                                        )
                                        Text(
                                            text = "$sizeText · $agoText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSession(session.id) }) {
                                        Icon(
                                            Icons.TwoTone.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (state.isRecording) {
                                androidx.compose.material3.FilledTonalButton(onClick = onStopRecording) {
                                    Icon(Icons.TwoTone.Cancel, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_debuglog_stop_action))
                                }
                            } else {
                                androidx.compose.material3.FilledTonalButton(onClick = onStartRecording) {
                                    Icon(Icons.TwoTone.BugReport, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_debuglog_record_action))
                                }
                            }
                        }
                    }
                }
            }

            // Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChange,
                        label = { Text(stringResource(R.string.support_contact_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    WordCountText(
                        current = state.descriptionWords,
                        minimum = 20,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (state.category) {
                            Category.BUG -> stringResource(R.string.support_contact_description_bug_hint)
                            Category.FEATURE -> stringResource(R.string.support_contact_description_feature_hint)
                            Category.QUESTION -> stringResource(R.string.support_contact_description_question_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expected Behavior Card (only for Bug)
            if (state.isBug) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.expectedBehavior,
                            onValueChange = onExpectedChange,
                            label = { Text(stringResource(R.string.support_contact_expected_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        WordCountText(
                            current = state.expectedWords,
                            minimum = 10,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.support_contact_expected_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Personal Note Card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_contact_support_24),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.support_contact_welcome),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Send Button
            androidx.compose.material3.Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSend,
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.TwoTone.Email, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.support_contact_send_action))
            }

            // Footer
            Text(
                text = stringResource(R.string.support_contact_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: @Composable () -> Unit,
    title: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun WordCountText(
    current: Int,
    minimum: Int,
) {
    val color = when {
        current == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        current < minimum -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = pluralStringResource(R.plurals.support_contact_word_count, current, current, minimum),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}
