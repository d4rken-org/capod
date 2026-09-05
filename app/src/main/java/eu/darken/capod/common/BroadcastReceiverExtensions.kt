import android.content.BroadcastReceiver
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

fun BroadcastReceiver.PendingResult.finish2(): Boolean = try {
    finish()
    true
} catch (e: IllegalStateException) {
    log(TAG) { "BroadcastReceiver.PendingResult.finish() failed: $e" }
    false
}

private val TAG = logTag("Common", "BroadcastReceiver")
