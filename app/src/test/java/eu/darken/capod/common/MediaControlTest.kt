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
    fun `sendPlay ignores stale active state after auto-pause`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        mediaControl.sendPlay()
        assertFalse(mediaControl.wasRecentlyPausedByCap)

        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendPlayPause resumes after auto-pause even when audio manager still reports active`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        mediaControl.sendPlayPause()
        assertFalse(mediaControl.wasRecentlyPausedByCap)

        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `default sendPause dispatches but does NOT arm auto-resume`() = runTest {
        every { audioManager.isMusicActive } returns true

        val dispatched = mediaControl.sendPause()

        assertTrue(dispatched)
        // Critical: a user-initiated pause (stem, sleep, etc.) must not arm the auto-resume
        // flag. Only `rememberForResume = true` (auto-pause from ear removal) does that.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendPause with rememberForResume dispatches and arms auto-resume`() = runTest {
        every { audioManager.isMusicActive } returns true

        val dispatched = mediaControl.sendPause(rememberForResume = true)

        assertTrue(dispatched)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `default sendPause clears any prior auto-resume flag when actually dispatching`() = runTest {
        // Prime: ear-removal auto-pause armed the flag.
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music is somehow playing again (manual resume). Then a stem-press or sleep-pause
        // fires while music is active — explicit user pause must SUPERSEDE the prior
        // auto-pause memory.
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause()

        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `sendPause returns false and is a no-op when no music is active`() = runTest {
        every { audioManager.isMusicActive } returns false

        val dispatched = mediaControl.sendPause()

        assertFalse(dispatched)
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `no-op sendPause does NOT clear an existing auto-resume flag`() = runTest {
        // Prime: auto-pause armed the flag and music is now inactive.
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music has gone inactive (the auto-pause took effect). A sleep reaction now fires
        // while music is already paused — sendPause is a no-op (returns false) and must NOT
        // wipe the pending auto-resume intent from the prior ear-removal pause.
        every { audioManager.isMusicActive } returns false
        val dispatched = mediaControl.sendPause()

        assertFalse(dispatched)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `sendPause with rememberForResume returns false and does not arm the flag when no music is active`() = runTest {
        every { audioManager.isMusicActive } returns false

        val dispatched = mediaControl.sendPause(rememberForResume = true)

        assertFalse(dispatched)
        // Critical: arming the flag for a no-op pause would later make sendPlay treat it as
        // "we just paused" and force a resume.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 0) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `sendStop dispatches MEDIA_STOP and clears auto-resume`() = runTest {
        // Prime auto-resume.
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        mediaControl.sendStop()

        assertFalse(mediaControl.wasRecentlyPausedByCap)
        verify(exactly = 2) { audioManager.dispatchMediaKeyEvent(any()) }
    }

    @Test
    fun `wasRecentlyPausedByCap is sticky and does not expire on its own`() = runTest {
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Wait an arbitrarily long time. With the previous timer-based design this would have
        // expired after 15 seconds; the sticky flag must remain true until cleared by an event.
        timeSource.advanceBy(java.time.Duration.ofMinutes(30))

        assertTrue(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `wasRecentlyPausedByCap clears when music transitions inactive to active from any source`() = runTest {
        every { audioManager.isMusicActive } returns true
        fireCallback() // seed lastKnownMusicActive=true

        mediaControl.sendPause(rememberForResume = true)
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
    fun `auto-pause sets capPaused before dispatching so a racing inactive-active callback cannot leave a stale true`() = runTest {
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

        mediaControl.sendPause(rememberForResume = true)

        // After the suspended dispatch returns, capPaused should be in a coherent state with
        // the live callback observations. Music is currently active (per the racing callback)
        // so the inactive→active reset clears the sticky flag — that's the correct outcome:
        // we don't want to claim our pause "stuck" when audio is playing.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }
}
