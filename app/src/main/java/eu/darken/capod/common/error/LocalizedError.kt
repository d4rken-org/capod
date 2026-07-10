package eu.darken.capod.common.error

import android.content.Context
import eu.darken.capod.R

interface HasLocalizedError {
    fun getLocalizedError(context: Context): LocalizedError
}

data class LocalizedError(
    val throwable: Throwable,
    val label: String,
    val description: String
) {
    fun asText() = "$label:\n$description"
}

fun Throwable.localized(c: Context): LocalizedError = when {
    this is HasLocalizedError -> this.getLocalizedError(c)
    localizedMessage != null -> LocalizedError(
        throwable = this,
        label = "${c.getString(R.string.general_error_label)}: ${errorTypeName()}",
        description = localizedMessage ?: getStackTracePeek()
    )
    else -> LocalizedError(
        throwable = this,
        label = "${c.getString(R.string.general_error_label)}: ${errorTypeName()}",
        description = getStackTracePeek()
    )
}

// Anonymous throwable classes have no simpleName — never crash while rendering an error.
private fun Throwable.errorTypeName(): String = this::class.simpleName ?: "Error"

private fun Throwable.getStackTracePeek() = this.stackTraceToString()
    .lines()
    .filterIndexed { index, _ -> index > 1 }
    .take(3)
    .joinToString("\n")