package eu.darken.capod.common

import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
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

    private val transitionLock = Any()

    @Volatile private var lastKnownMusicActive: Boolean = false
    @Volatile private var externalStopAt: Long = NO_TIMESTAMP
    @Volatile private var capPauseDispatchedAt: Long = NO_TIMESTAMP

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            recordTransition(audioManager.isMusicActive)
        }
    }

    init {
        // Seed from current state so we can't miss a true→false transition when
        // MediaControl is constructed while music is already active.
        lastKnownMusicActive = audioManager.isMusicActive
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
    }

    val isPlaying: Boolean
        get() = audioManager.isMusicActive

    val wasRecentlyPausedByCap: Boolean
        get() = capPauseExpiryElapsedRealtime > timeSource.elapsedRealtime()

    /**
     * `true` when music has been stopped recently by something *other* than CAP — i.e. the
     * user paused via the phone, the playing app stopped on its own, or playback ended.
     *
     * Used by [PlayPause] to suppress auto-play on pod re-insertion when the user clearly
     * wanted music to stay stopped. Stays `false` for stops attributed to CAP — those are
     * detected by [recordTransition] from the pending [capPauseDispatchedAt] set by
     * [sendPause].
     */
    val wasMusicExternallyStoppedRecently: Boolean
        get() {
            val nowActive = audioManager.isMusicActive
            // Defense in depth: if the callback was missed (race, re-register, etc.), record
            // the transition on read.
            if (lastKnownMusicActive != nowActive) recordTransition(nowActive)
            if (nowActive) return false
            val stoppedAt = externalStopAt
            return stoppedAt != NO_TIMESTAMP &&
                timeSource.elapsedRealtime() - stoppedAt < EXTERNAL_STOP_WINDOW_MS
        }

    private fun recordTransition(nowActive: Boolean) = synchronized(transitionLock) {
        if (lastKnownMusicActive && !nowActive) {
            val now = timeSource.elapsedRealtime()
            val pendingAt = capPauseDispatchedAt
            val byCap = pendingAt != NO_TIMESTAMP &&
                (now - pendingAt) < CAP_PAUSE_ATTRIBUTION_TTL_MS
            if (byCap) {
                // Consume the pending CAP attribution; clear any stale prior external stop
                // since we're attributing the *current* state-of-music to CAP.
                capPauseDispatchedAt = NO_TIMESTAMP
                externalStopAt = NO_TIMESTAMP
            } else {
                externalStopAt = now
                // Stale pending dispatch (TTL exceeded — pause was probably ignored). Drop it
                // so a future stop isn't misattributed.
                capPauseDispatchedAt = NO_TIMESTAMP
            }
        } else if (!lastKnownMusicActive && nowActive) {
            // Music is active again — any pending CAP attribution is stale, and any prior
            // external stop is no longer "recent" (music has been resumed since).
            capPauseDispatchedAt = NO_TIMESTAMP
            externalStopAt = NO_TIMESTAMP
        }
        lastKnownMusicActive = nowActive
    }

    suspend fun sendPlay() {
        log(TAG, INFO) { "sendPlay()" }
        if (audioManager.isMusicActive && !wasRecentlyPausedByCap) {
            log(TAG, INFO) { "Music is already playing, not sending play" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        clearRecentCapPause()
    }

    /**
     * Dispatches a MEDIA_PAUSE key event if music is currently playing.
     *
     * Returns `true` when a key event was actually dispatched (and the 15-second
     * [wasRecentlyPausedByCap] window was set), `false` when the call was a no-op because
     * nothing was playing. Callers that need to distinguish "we actually paused" from "there
     * was nothing to pause" — e.g. the sleep reaction, which gates its notification and
     * cooldown on a real pause — should branch on the return value rather than checking
     * [isPlaying] themselves to avoid a check-then-act race with the audio system.
     */
    suspend fun sendPause(): Boolean {
        log(TAG, INFO) { "sendPause()" }
        if (!audioManager.isMusicActive) {
            log(TAG, INFO) { "Music is not playing, not sending pause" }
            return false
        }
        // Set BEFORE dispatch so the resulting active→inactive transition (whether observed
        // by the playback callback or detected by the getter's read-time fallback) attributes
        // the stop to CAP. Held under transitionLock for memory ordering against recordTransition.
        synchronized(transitionLock) {
            capPauseDispatchedAt = timeSource.elapsedRealtime()
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        markRecentCapPause()
        return true
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
        private const val EXTERNAL_STOP_WINDOW_MS = 60_000L
        // TTL for a pending CAP-pause attribution. Long enough to cover delayed/missed
        // playback-config callbacks (the read-time fallback may fire many seconds later);
        // short enough that an "ignored pause" doesn't wrongly claim a much later external
        // stop as CAP-attributed.
        private const val CAP_PAUSE_ATTRIBUTION_TTL_MS = 30_000L
        private const val NO_TIMESTAMP = -1L
    }
}
