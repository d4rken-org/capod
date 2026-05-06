package eu.darken.capod.common

import android.media.AudioManager
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import java.time.Duration

class MediaControlTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var mediaControl: MediaControl
    private lateinit var timeSource: TestTimeSource
    private lateinit var playbackCallbackSlot: CapturingSlot<AudioManager.AudioPlaybackCallback>

    @BeforeEach
    fun setup() {
        timeSource = TestTimeSource(
            elapsedRealtimeMs = 1_000L,
            uptimeMillisValue = 1_000L,
        )
        audioManager = mockk(relaxed = true)
        every { audioManager.dispatchMediaKeyEvent(any()) } just Runs
        every { audioManager.isMusicActive } returns false
        playbackCallbackSlot = slot()
        every { audioManager.registerAudioPlaybackCallback(capture(playbackCallbackSlot), any()) } just Runs
        mediaControl = MediaControl(audioManager, timeSource)
    }

    private fun fireCallback() {
        playbackCallbackSlot.captured.onPlaybackConfigChanged(emptyList())
    }

    @Test
    fun `sendPlay ignores stale active state after cap pause`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause()
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        mediaControl.sendPlay()
        assertFalse(mediaControl.wasRecentlyPausedByCap)

        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendPlayPause resumes after cap pause even when audio manager still reports active`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause()
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        mediaControl.sendPlayPause()
        assertFalse(mediaControl.wasRecentlyPausedByCap)

        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendPause returns true and dispatches when music is active`() = runTest {
        every { audioManager.isMusicActive } returns true

        val dispatched = mediaControl.sendPause()

        assertTrue(dispatched)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendPause returns false and is a no-op when no music is active`() = runTest {
        every { audioManager.isMusicActive } returns false

        val dispatched = mediaControl.sendPause()

        assertFalse(dispatched)
        // Critical: the 15-second cap-pause window must NOT open for a no-op pause, otherwise
        // an unrelated sendPlay would treat it as "we just paused, resume from it".
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `wasMusicExternallyStoppedRecently is false on cold start`() {
        // No music ever observed.
        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `wasMusicExternallyStoppedRecently is false while music is currently active`() {
        every { audioManager.isMusicActive } returns true

        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `external stop is recorded when music goes inactive without a preceding sendPause`() {
        every { audioManager.isMusicActive } returns true
        fireCallback() // seeds lastKnownMusicActive=true

        every { audioManager.isMusicActive } returns false
        fireCallback() // active->inactive transition with no recent CAP pause

        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `cap stop is NOT classified as external`() = runTest {
        every { audioManager.isMusicActive } returns true
        fireCallback() // seeds lastKnownMusicActive=true

        mediaControl.sendPause() // sets lastCapPauseDispatchAt synchronously

        every { audioManager.isMusicActive } returns false
        fireCallback() // active->inactive immediately after our pause

        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `external-stop window expires after 60 seconds`() {
        every { audioManager.isMusicActive } returns true
        fireCallback()
        every { audioManager.isMusicActive } returns false
        fireCallback()

        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)

        timeSource.advanceBy(Duration.ofSeconds(61))

        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `external-stop window boundary - 59s in, 60s out`() {
        every { audioManager.isMusicActive } returns true
        fireCallback()
        every { audioManager.isMusicActive } returns false
        fireCallback()

        timeSource.advanceBy(Duration.ofMillis(59_999))
        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)

        timeSource.advanceBy(Duration.ofMillis(1)) // now exactly 60_000ms after stop
        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `init seeds lastKnownMusicActive from current state`() {
        // Construct a fresh MediaControl with isMusicActive=true at construction. The first
        // active->inactive callback must fire the transition logic — without the seed it
        // would incorrectly believe the previous state was inactive and miss the stop.
        val freshAudioManager: AudioManager = mockk(relaxed = true)
        every { freshAudioManager.dispatchMediaKeyEvent(any()) } just Runs
        every { freshAudioManager.isMusicActive } returns true
        val freshSlot = slot<AudioManager.AudioPlaybackCallback>()
        every { freshAudioManager.registerAudioPlaybackCallback(capture(freshSlot), any()) } just Runs

        val freshControl = MediaControl(freshAudioManager, timeSource)

        // Music goes off (e.g. user pause) — this is the first transition we observe.
        every { freshAudioManager.isMusicActive } returns false
        freshSlot.captured.onPlaybackConfigChanged(emptyList())

        assertTrue(freshControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `getter self-heals when callback was missed`() {
        // Seed: callback fired with active=true. Then the active->inactive transition happens
        // but the callback never fires (race / missed event). The getter should detect the
        // mismatch on read and record the stop itself.
        every { audioManager.isMusicActive } returns true
        fireCallback()

        every { audioManager.isMusicActive } returns false
        // No fireCallback() — simulate missed event.

        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `delayed cap callback within TTL is still attributed to cap`() = runTest {
        every { audioManager.isMusicActive } returns true
        fireCallback()

        mediaControl.sendPause()
        every { audioManager.isMusicActive } returns false
        timeSource.advanceBy(Duration.ofSeconds(10)) // realistic-but-late callback

        fireCallback()

        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `cap stop self-heals via getter when callback was missed past the old short window`() = runTest {
        // Codex review scenario: CAP pauses, the playback callback never fires, the user
        // reinserts a pod some seconds later. The getter must still attribute the stop to
        // CAP — not regress to recreating the 16-60s "dead zone" that the original fix had.
        every { audioManager.isMusicActive } returns true
        fireCallback()

        mediaControl.sendPause()
        every { audioManager.isMusicActive } returns false
        timeSource.advanceBy(Duration.ofSeconds(16))

        // No fireCallback() — the callback was missed.
        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `pending cap dispatch is dropped after TTL so an unrelated later stop is external`() = runTest {
        // Pause was dispatched but ignored (music kept playing). After TTL, the next genuine
        // active→inactive transition must NOT be misattributed to CAP.
        every { audioManager.isMusicActive } returns true
        fireCallback()

        mediaControl.sendPause()
        // Music ignored the key — still active. Time passes.
        timeSource.advanceBy(Duration.ofSeconds(31)) // > CAP_PAUSE_ATTRIBUTION_TTL_MS

        every { audioManager.isMusicActive } returns false
        fireCallback() // unrelated stop

        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `prior external stop is cleared when music resumes so it does not suppress a later cap pause cycle`() = runTest {
        // T=0: external stop.
        every { audioManager.isMusicActive } returns true
        fireCallback()
        every { audioManager.isMusicActive } returns false
        fireCallback()
        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)

        // Music resumes (e.g. user starts a new song manually) — must clear externalStopAt.
        timeSource.advanceBy(Duration.ofSeconds(5))
        every { audioManager.isMusicActive } returns true
        fireCallback()

        // CAP pauses fresh. The earlier external stop must not bleed through.
        timeSource.advanceBy(Duration.ofSeconds(5))
        mediaControl.sendPause()
        every { audioManager.isMusicActive } returns false
        fireCallback()

        assertFalse(mediaControl.wasMusicExternallyStoppedRecently)
    }

    @Test
    fun `pending cap dispatch is dropped when music transitions to active before being consumed`() = runTest {
        // sendPause was dispatched but the pause was effectively ignored — observable as a
        // sustained inactive→active transition without ever going inactive. The next external
        // stop must still be classified as external.
        every { audioManager.isMusicActive } returns false
        fireCallback() // seed lastKnownMusicActive=false

        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause()
        // sendPause sets pending; but isMusicActive is now true (not actually paused).
        fireCallback() // observe inactive→active; clears pending

        // Some time later, a genuine external stop happens.
        timeSource.advanceBy(Duration.ofSeconds(2))
        every { audioManager.isMusicActive } returns false
        fireCallback()

        assertTrue(mediaControl.wasMusicExternallyStoppedRecently)
    }
}
