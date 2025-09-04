package eu.darken.capod.common.error

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R

fun Throwable.asErrorDialogBuilder(
    context: Context
) = MaterialAlertDialogBuilder(context).apply {
    val error = this@asErrorDialogBuilder
    val localizedError = error.localized(context)

    setTitle(localizedError.label)
    setMessage(localizedError.description)

    setPositiveButton(android.R.string.ok) { _, _ ->

    }
}