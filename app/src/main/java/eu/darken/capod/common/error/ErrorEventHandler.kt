package eu.darken.capod.common.error

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(android.R.string.dialog_alert_title)) },
        text = {
            Text(
                text = throwable.localizedMessage
                    ?: throwable.message
                    ?: throwable::class.simpleName
                    ?: "Unknown error"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
