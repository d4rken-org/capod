package eu.darken.capod.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

@Composable
fun ErrorEventHandler(source: ErrorEventSource2) {
    val errorEvents = source.errorEvents
    var currentError by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(errorEvents) { errorEvents.collect { error -> currentError = error } }

    currentError?.let { error ->
        ComposeErrorDialog(
            throwable = error,
            onDismiss = { currentError = null },
        )
    }
}

@Composable
private fun ComposeErrorDialog(
    throwable: Throwable,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Prefer the curated HasLocalizedError strings (e.g. billing errors) over raw exception
    // messages like Play Billing's internal debugMessage.
    val localizedError = remember(throwable, context) { throwable.localized(context) }

    val activity = context as? Activity
    // An error that offers a way out gets its own action plus a dismiss; everything else keeps the
    // acknowledge-only shape. The activity is required to launch the fix.
    val hasFix = localizedError.fixAction != null && activity != null

    // Keyed on the throwable, not the LocalizedError: the latter is rebuilt (with fresh action
    // lambdas, so never equal) on every recomposition, which would wipe the message immediately.
    var actionError by remember(throwable) { mutableStateOf<String?>(null) }

    // errorMessage is per-dispatch, NOT read from localizedError: fixActionErrorMessage describes
    // only the fix action's failure, so every dispatch site passes its own copy (or none) and no
    // other action can ever surface it.
    fun dispatchAndDismiss(
        action: (Activity) -> Unit,
        errorMessage: String? = null,
    ) {
        // Error actions are arbitrary code (intent launches): a throw here would crash the UI
        // thread from inside a click handler, and skipping onDismiss() would leave the dialog
        // latched on the current error with no way out.
        try {
            action(activity!!)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error action failed: ${e.asLog()}" }
            // A dispatch that ships its own failure copy keeps the dialog open and shows it inline
            // (no length cap, unlike a Toast). Never latched: the dismiss button stays available.
            errorMessage?.let {
                actionError = it
                return
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = localizedError.label) },
        text = {
            Column {
                Text(text = localizedError.description)
                actionError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (hasFix) {
                TextButton(
                    onClick = {
                        dispatchAndDismiss(
                            action = localizedError.fixAction!!,
                            errorMessage = localizedError.fixActionErrorMessage,
                        )
                    },
                ) {
                    Text(text = localizedError.fixActionLabel ?: stringResource(android.R.string.ok))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = if (hasFix) {
            {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.general_dismiss_action))
                }
            }
        } else {
            null
        },
    )
}

private val TAG = logTag("Error", "Dialog", "Compose")
