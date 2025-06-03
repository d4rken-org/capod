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

    fun open(address: String) {
        val intent = Intent(Intent.ACTION_VIEW, address.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            log(ERROR) { "Failed to launch: ${e.asLog()}" }
        }
    }

}