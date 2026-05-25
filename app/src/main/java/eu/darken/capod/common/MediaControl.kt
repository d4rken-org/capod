package eu.darken.capod.common

import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.view.KeyEvent
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
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
    /**
     * Set when [sendPause] dispatches a pause we expect to take effect, cleared when [sendPlay]
     * dispatches a resume or when music transitions inactive→active from any source. Read by
     * [PlayPause] to gate auto-resume on pod-in: only resume if we're the ones who paused.
     *
     * "Sticky" — no time-based expiry. The original 15-second window was too short for typical
     * pod-out conversations and had a known race where music starting again via another source
     * (e.g. user manually resumed) could trigger a stray play-key dispatch on the next pod-in.
     */
    @Volatile private var capPaused: Boolean = false
    @Volatile private var lastKnownMusicActive: Boolean = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val nowActive = audioManager.isMusicActive
            if (!lastKnownMusicActive && nowActive) {
                // Music started by some source (could be us via sendPlay or someone else).
                // Either way, our pause memory is stale — drop it so a future pod-in doesn't
                // re-fire a play key over already-active music.
                capPaused = false
            }
            lastKnownMusicActive = nowActive
        }
    }

    init {
        // Seed the active flag from current state so we won't miss the next inactive→active
        // transition if music is already playing when MediaControl is constructed.
        lastKnownMusicActive = audioManager.isMusicActive
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
    }

    val isPlaying: Boolean
        get() = audioManager.isMusicActive

    val wasRecentlyPausedByCap: Boolean
        get() = capPaused

    suspend fun sendPlay() {
        log(TAG, INFO) { "sendPlay()" }
        if (audioManager.isMusicActive && !capPaused) {
            log(TAG, INFO) { "Music is already playing, not sending play" }
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        capPaused = false
    }

    /**
     * Dispatches a MEDIA_PAUSE key event if music is currently playing.
     *
     * @param rememberForResume When `true`, arms [wasRecentlyPausedByCap] so a subsequent
     * pod-in transition can auto-resume — set this only from the auto-pause / ear-detection
     * flow. When `false` (default), the dispatched pause clears any pending auto-resume —
     * this is the path for stem-press play/pause, sleep detection, and anywhere else CAPod
     * is relaying an explicit user choice to stop playback. Matches Apple's iOS/macOS
     * behavior where only ear-removal auto-pauses are eligible for auto-resume.
     *
     * Returns `true` when a key event was actually dispatched, `false` when the call was a
     * no-op because nothing was playing. A no-op leaves the existing flag state untouched
     * (so a sleep-reaction firing while music is already paused doesn't accidentally cancel
     * a pending auto-resume from a recent ear-removal pause). Callers that need to
     * distinguish "we actually paused" from "there was nothing to pause" — e.g. the sleep
     * reaction, which gates its notification and cooldown on a real pause — should branch
     * on the return value rather than checking [isPlaying] themselves to avoid a
     * check-then-act race with the audio system.
     */
    suspend fun sendPause(rememberForResume: Boolean = false): Boolean {
        log(TAG, INFO) { "sendPause(rememberForResume=$rememberForResume)" }
        if (!audioManager.isMusicActive) {
            log(TAG, INFO) { "Music is not playing, not sending pause" }
            return false
        }
        // Set BEFORE the suspending sendKey() call. If we set after, an inactive→active
        // playback callback that fires during the dispatch (e.g. a fast user resume on the
        // phone, or another app grabbing audio focus and immediately starting) could clear
        // capPaused mid-dispatch and we'd then overwrite it back to true on a stale pause.
        // This single explicit assignment also covers the contract that an explicit user
        // pause cancels a pending auto-resume.
        capPaused = rememberForResume
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return true
    }

    /**
     * Dispatches MEDIA_STOP and clears any pending auto-resume — Stop is an explicit user
     * "stay stopped" action, so a later pod-in must not auto-resume from a prior auto-pause.
     */
    suspend fun sendStop() {
        log(TAG, INFO) { "sendStop()" }
        capPaused = false
        sendKey(KeyEvent.KEYCODE_MEDIA_STOP)
    }

    suspend fun sendPlayPause() {
        log(TAG) { "sendPlayPause()" }
        if (capPaused) {
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

    /** Current STREAM_MUSIC volume index. Used to detect user-initiated volume changes after a duck. */
    fun currentMusicVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /**
     * Lowers STREAM_MUSIC volume by [reductionPercent] (relative to the current level) and returns
     * the prior + the volume actually applied, so the caller can later restore it and detect whether
     * the user changed the volume in the meantime.
     *
     * Returns `null` (no-op) when nothing is playing, the device has fixed volume, or the computed
     * target wouldn't actually lower the volume. No [AudioManager.FLAG_SHOW_UI] — this fires on a
     * frequent push event and the volume panel flashing would be noisy. The applied target is read
     * back from the system because Bluetooth absolute-volume routes can quantize the requested value.
     */
    fun duckMusicVolume(reductionPercent: Int): VolumeDuck? {
        if (!audioManager.isMusicActive) {
            log(TAG, INFO) { "duckMusicVolume: nothing playing, skipping" }
            return null
        }
        if (audioManager.isVolumeFixed) {
            log(TAG, INFO) { "duckMusicVolume: device has fixed volume, skipping" }
            return null
        }
        val percent = reductionPercent.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
        val prior = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (prior * (100 - percent) / 100).coerceIn(min, max)
        if (target >= prior) {
            log(TAG, INFO) { "duckMusicVolume: target $target >= current $prior, skipping" }
            return null
        }
        return try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            val applied = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            log(TAG, INFO) { "duckMusicVolume($percent%): $prior -> $applied (requested $target)" }
            VolumeDuck(priorVolume = prior, appliedVolume = applied)
        } catch (e: SecurityException) {
            // setStreamVolume throws under Do-Not-Disturb without notification policy access.
            log(TAG, WARN) { "duckMusicVolume: setStreamVolume denied: ${e.message}" }
            null
        }
    }

    /** Restores STREAM_MUSIC to [priorVolume]. No-op on fixed-volume devices; denials are logged. */
    fun restoreMusicVolume(priorVolume: Int) {
        if (audioManager.isVolumeFixed) return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, priorVolume, 0)
            log(TAG, INFO) { "restoreMusicVolume($priorVolume)" }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "restoreMusicVolume: setStreamVolume denied: ${e.message}" }
        }
    }

    /** Snapshot of a volume duck so the caller can restore the prior level and detect user changes. */
    data class VolumeDuck(
        val priorVolume: Int,
        val appliedVolume: Int,
    )

    companion object {
        private val TAG = logTag("MediaControl")
    }
}
