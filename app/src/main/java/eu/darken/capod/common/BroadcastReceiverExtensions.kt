import android.content.BroadcastReceiver
import eu.darken.capod.common.debug.logging.log

fun BroadcastReceiver.PendingResult.finish2(): Boolean = try {
    finish()
    true
} catch (e: IllegalStateException) {
    log { "BroadcastReceiver.PendingResult.finish() failed: $e" }
    false
}