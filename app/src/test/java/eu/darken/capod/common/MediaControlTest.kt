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
        // Critical: the cap-pause flag must NOT be set for a no-op pause, otherwise an
        // unrelated sendPlay would treat it as "we just paused, resume from it".
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `wasRecentlyPausedByCap is sticky and does not expire on its own`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause()
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Wait an arbitrarily long time. With the previous timer-based design this would have
        // expired after 15 seconds; the sticky flag must remain true until cleared by an event.
        timeSource.advanceBy(java.time.Duration.ofMinutes(30))

        assertTrue(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `wasRecentlyPausedByCap clears when music transitions inactive to active from any source`() = runTest {
        every { audioManager.isMusicActive } returns true

        // First the seed transition active→active so lastKnownMusicActive is true.
        fireCallback()

        mediaControl.sendPause()
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music goes inactive (CAP's pause took effect).
        every { audioManager.isMusicActive } returns false
        fireCallback()
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music starts again (e.g. user manually resumed via phone). Sticky flag must clear so
        // a later pod-in doesn't fire a stray play key on top of already-playing music.
        every { audioManager.isMusicActive } returns true
        fireCallback()
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `sendPause sets capPaused before dispatching so a racing inactive-active callback cannot leave a stale true`() = runTest {
        // Repro for a race where the playback config callback fires during sendKey()'s
        // suspension. If capPaused were set after dispatch, an interleaved inactive→active
        // callback would clear it, then sendPause's post-dispatch line would put it back to
        // true while music is genuinely playing again — wrongly arming a future pod-in resume.
        every { audioManager.isMusicActive } returns true
        fireCallback() // seed lastKnownMusicActive=true

        // sendKey is implemented with an internal delay(100). Drive a callback during that
        // window by sending a single coalesced inactive→active sequence right after kicking
        // off sendPause; with the fix in place the sequence's effect on capPaused is the
        // intended one (cleared on inactive→active, but only AFTER capPaused was set).
        every { audioManager.dispatchMediaKeyEvent(any()) } answers {
            // First DOWN dispatch: pretend music briefly went inactive then active mid-pause.
            every { audioManager.isMusicActive } returns false
            fireCallback()
            every { audioManager.isMusicActive } returns true
            fireCallback()
        }

        mediaControl.sendPause()

        // After the suspended dispatch returns, capPaused should be in a coherent state with
        // the live callback observations. Music is currently active (per the racing callback)
        // so the inactive→active reset clears the sticky flag — that's the correct outcome:
        // we don't want to claim our pause "stuck" when audio is playing.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }
}
