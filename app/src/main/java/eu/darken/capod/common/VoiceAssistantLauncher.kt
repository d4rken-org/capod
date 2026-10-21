package eu.darken.capod.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceAssistantLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun launch() {
        if (!Settings.canDrawOverlays(context)) {
            log(TAG, WARN) { "Overlay permission missing, the platform may block launching the voice assistant" }
        }

        val intent = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            log(TAG) { "Voice assistant launched" }
        } catch (e: ActivityNotFoundException) {
            log(TAG, WARN) { "No voice assistant available: ${e.asLog()}" }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.press_action_voice_assistant_unavailable, Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Launching voice assistant was denied: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("VoiceAssistant", "Launcher")
    }
}
