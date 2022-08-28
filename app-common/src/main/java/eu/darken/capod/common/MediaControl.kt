package eu.darken.capod.common

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControl @Inject constructor(
    private val audioManager: AudioManager,
) {

    val isPlaying: Boolean
        get() = audioManager.isMusicActive

    suspend fun sendPlay() {
        log(TAG, INFO) { "sendPlay()" }
        if (audioManager.isMusicActive) {
            log(TAG, INFO) { "Music is already playing, not sending play" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    suspend fun sendPause() {
        log(TAG, INFO) { "sendPause()" }
        if (!audioManager.isMusicActive) {
            log(TAG, INFO) { "Music is not playing, not sending pause" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    suspend fun sendPlayPause() {
        log(TAG) { "sendPlayPause()" }
        if (audioManager.isMusicActive) {
            sendPause()
        } else {
            sendPlay()
        }
    }

    private suspend fun sendKey(keyCode: Int) {
        log(TAG) { "Sending up+down KeyEvent: $keyCode" }
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        delay(100)
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime + 200, eventTime + 200, KeyEvent.ACTION_UP, keyCode, 0))
    }

    companion object {
        private val TAG = logTag("MediaControl")
    }
}
