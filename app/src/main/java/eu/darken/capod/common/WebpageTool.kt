package eu.darken.capod.common

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import javax.inject.Inject

@Reusable
class WebpageTool @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Returns whether an activity was actually started, so callers that gate behaviour on the page
    // having opened (e.g. the FOSS sponsor unlock heuristic) don't fire when no browser handled it.
    fun open(address: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, address.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            log(ERROR) { "Failed to launch: ${e.asLog()}" }
            false
        }
    }

}