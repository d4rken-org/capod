package eu.darken.capod.common

import android.media.AudioManager
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
    private val timeSource: TimeSource,
) {
    private var capPauseExpiryElapsedRealtime: Long = 0L

    val isPlaying: Boolean
        get() = audioManager.isMusicActive

    val wasRecentlyPausedByCap: Boolean
        get() = capPauseExpiryElapsedRealtime > timeSource.elapsedRealtime()

    suspend fun sendPlay() {
        log(TAG, INFO) { "sendPlay()" }
        if (audioManager.isMusicActive && !wasRecentlyPausedByCap) {
            log(TAG, INFO) { "Music is already playing, not sending play" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        clearRecentCapPause()
    }

    suspend fun sendPause() {
        log(TAG, INFO) { "sendPause()" }
        if (!audioManager.isMusicActive) {
            log(TAG, INFO) { "Music is not playing, not sending pause" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        markRecentCapPause()
    }

    suspend fun sendPlayPause() {
        log(TAG) { "sendPlayPause()" }
        if (wasRecentlyPausedByCap) {
            sendPlay()
            return
        }
        if (audioManager.isMusicActive) {
            sendPause()
        } else {
            sendPlay()
        }
    }

    internal suspend fun sendKey(keyCode: Int) {
        log(TAG) { "Sending up+down KeyEvent: $keyCode" }
        val eventTime = timeSource.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        delay(100)
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime + 200, eventTime + 200, KeyEvent.ACTION_UP, keyCode, 0))
    }

    fun adjustVolumeUp() {
        log(TAG, INFO) { "adjustVolumeUp()" }
        audioManager.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_RAISE,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    fun adjustVolumeDown() {
        log(TAG, INFO) { "adjustVolumeDown()" }
        audioManager.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_LOWER,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    fun toggleMuteMusic() {
        log(TAG, INFO) { "toggleMuteMusic()" }
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_TOGGLE_MUTE,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun markRecentCapPause() {
        capPauseExpiryElapsedRealtime = timeSource.elapsedRealtime() + RECENT_CAP_PAUSE_WINDOW_MS
    }

    private fun clearRecentCapPause() {
        capPauseExpiryElapsedRealtime = 0L
    }

    companion object {
        private val TAG = logTag("MediaControl")
        private const val RECENT_CAP_PAUSE_WINDOW_MS = 15_000L
    }
}
