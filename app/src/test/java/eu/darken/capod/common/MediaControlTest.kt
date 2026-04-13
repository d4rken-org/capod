package eu.darken.capod.common

import android.media.AudioManager
import android.os.SystemClock
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MediaControlTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var mediaControl: MediaControl

    @BeforeEach
    fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { SystemClock.uptimeMillis() } returns 1_000L
        audioManager = mockk(relaxed = true)
        every { audioManager.dispatchMediaKeyEvent(any()) } just Runs
        mediaControl = MediaControl(audioManager)
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(SystemClock::class)
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
}
