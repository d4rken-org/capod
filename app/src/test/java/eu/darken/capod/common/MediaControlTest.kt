package eu.darken.capod.common

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.view.KeyEvent
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var handler: Handler
    private lateinit var playbackCallbackSlot: CapturingSlot<AudioManager.AudioPlaybackCallback>
    private lateinit var initRunnableSlot: CapturingSlot<Runnable>
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var focusRequestFactory: MediaControl.DuckFocusRequestFactory
    private lateinit var focusListenerSlot: CapturingSlot<AudioManager.OnAudioFocusChangeListener>

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
        // The handler is captured, not inlined: registration and seeding now happen on a posted
        // runnable, and an unconditionally-inline post would hide the window that exists between
        // construction and that runnable draining.
        handler = mockk()
        initRunnableSlot = slot()
        every { handler.post(capture(initRunnableSlot)) } returns true
        // AudioFocusRequest.Builder is an unmocked android.jar stub, so the request is handed to
        // MediaControl through the injected factory instead of being built inside it.
        focusRequest = mockk()
        focusListenerSlot = slot()
        focusRequestFactory = mockk()
        every { focusRequestFactory.create(capture(focusListenerSlot)) } returns focusRequest
        mediaControl = MediaControl(audioManager, timeSource, handler, focusRequestFactory)
        // Drain the init runnable so `playbackCallbackSlot` is populated for `fireCallback()`.
        initRunnableSlot.captured.run()
        // Everything posted after init (the pause arm) runs inline and synchronously, which keeps
        // the arm-before-dispatch ordering that the tests below observe.
        every { handler.post(any()) } answers { firstArg<Runnable>().run(); true }
    }

    private fun playbackConfig(stream: Int): AudioPlaybackConfiguration {
        val attributes = mockk<AudioAttributes>()
        every { attributes.volumeControlStream } returns stream
        return mockk<AudioPlaybackConfiguration>().also {
            every { it.audioAttributes } returns attributes
        }
    }

    /**
     * The callback derives the edge from the delivered snapshot, so the snapshot has to carry the
     * state — stubbing `isMusicActive` no longer influences it.
     */
    private fun fireCallback(musicActive: Boolean) {
        val configs: List<AudioPlaybackConfiguration> =
            if (musicActive) listOf(playbackConfig(AudioManager.STREAM_MUSIC)) else emptyList()
        playbackCallbackSlot.captured.onPlaybackConfigChanged(configs)
    }

    private fun fireCallbackWithStream(stream: Int) {
        playbackCallbackSlot.captured.onPlaybackConfigChanged(listOf(playbackConfig(stream)))
    }

    private fun fireCallbackWithUnmappableConfig() {
        val attributes = mockk<AudioAttributes>()
        every { attributes.volumeControlStream } throws IllegalArgumentException("Unknown usage")
        val config = mockk<AudioPlaybackConfiguration>()
        every { config.audioAttributes } returns attributes
        playbackCallbackSlot.captured.onPlaybackConfigChanged(listOf(config))
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
        fireCallback(true) // seed lastKnownMusicActive=true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music goes inactive (CAP's pause took effect).
        every { audioManager.isMusicActive } returns false
        fireCallback(false)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // Music starts again (e.g. user manually resumed via phone). Sticky flag must clear so
        // a later pod-in doesn't fire a stray play key on top of already-playing music.
        every { audioManager.isMusicActive } returns true
        fireCallback(true)
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `auto-pause sets capPaused before dispatching so a racing inactive-active callback cannot leave a stale true`() = runTest {
        // Repro for a race where the playback config callback fires during sendKey()'s
        // suspension. If capPaused were set after dispatch, an interleaved inactive→active
        // callback would clear it, then sendPause's post-dispatch line would put it back to
        // true while music is genuinely playing again — wrongly arming a future pod-in resume.
        every { audioManager.isMusicActive } returns true
        fireCallback(true) // seed lastKnownMusicActive=true

        // sendKey is implemented with an internal delay(100). Drive a callback during that
        // window by sending a single coalesced inactive→active sequence right after kicking
        // off sendPause; with the fix in place the sequence's effect on capPaused is the
        // intended one (cleared on inactive→active, but only AFTER capPaused was set).
        every { audioManager.dispatchMediaKeyEvent(any()) } answers {
            // First DOWN dispatch: pretend music briefly went inactive then active mid-pause.
            every { audioManager.isMusicActive } returns false
            fireCallback(false)
            every { audioManager.isMusicActive } returns true
            fireCallback(true)
        }

        mediaControl.sendPause(rememberForResume = true)

        // After the suspended dispatch returns, capPaused should be in a coherent state with
        // the live callback observations. Music is currently active (per the racing callback)
        // so the inactive→active reset clears the sticky flag — that's the correct outcome:
        // we don't want to claim our pause "stuck" when audio is playing.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `callback edge detection uses the event snapshot so coalesced queued deliveries still clear the flag`() =
        runTest {
            // Deliveries queue up on the callback handler. A live isMusicActive read makes every
            // queued delivery observe the newest state, so an inactive→active edge that happened
            // while they were queued is never seen and capPaused stays stale.
            every { audioManager.isMusicActive } returns true
            fireCallback(true)

            mediaControl.sendPause(rememberForResume = true)
            assertTrue(mediaControl.wasRecentlyPausedByCap)

            // Live state stays "active" the whole time — only the snapshots carry the sequence.
            fireCallback(false)
            fireCallback(true)

            assertFalse(mediaControl.wasRecentlyPausedByCap)
        }

    @Test
    fun `replacement delivery that drops the intermediate inactive snapshot leaves the flag armed`() = runTest {
        // Pins the documented residual: API 36+ may replace a pending config message with the
        // newest one, so two transitions arrive as a single callback. A state that is never
        // delivered is unrecoverable at the receiver — the accepted cost is a stale capPaused
        // producing one redundant (idempotent) MEDIA_PLAY on a later pod-in.
        every { audioManager.isMusicActive } returns true
        fireCallback(true)

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        fireCallback(true)

        assertTrue(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `non-music playback configs do not count as a music-start edge`() = runTest {
        // Init drained with isMusicActive=false, so lastKnownMusicActive starts false.
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // The pause records music as active; bring the observation back to inactive so the
        // following deliveries are candidates for an inactive→active edge.
        fireCallback(false)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        fireCallbackWithStream(AudioManager.STREAM_NOTIFICATION)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // A config whose attributes have no legacy stream mapping counts as not-music too.
        fireCallbackWithUnmappableConfig()
        assertTrue(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `queued music-start snapshot draining after an owned pause does not clear the fresh arm`() = runTest {
        // Init drained with isMusicActive=false, so lastKnownMusicActive starts false.
        every { audioManager.isMusicActive } returns true

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // A music-start snapshot that was already queued when the pause armed the flag drains now.
        // The pause's own live active-check already observed that music, so this must not read as a
        // fresh inactive→active edge.
        fireCallback(true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // A genuine resume afterwards still clears it.
        fireCallback(false)
        fireCallback(true)
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `stale queued snapshot pair draining before the arm does not clear it`() = runTest {
        // Regression pin for the queued-pair race: a pre-pause inactive→active pair (e.g. a track
        // change gap) that was already delivered but not yet drained when the pause armed. On the
        // pre-fix code the pair drained after the caller-side arm and the active snapshot read as a
        // fresh music-start edge that cleared it. Arming on the callback handler queues the arm
        // behind the pair instead.
        every { audioManager.isMusicActive } returns true
        fireCallback(true) // seed lastKnownMusicActive=true

        // The pair is queued ahead of the arm, so it drains before the posted runnable runs.
        every { handler.post(any()) } answers {
            fireCallback(false)
            fireCallback(true)
            firstArg<Runnable>().run()
            true
        }

        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)

        // A genuinely later resume still clears the arm.
        every { handler.post(any()) } answers { firstArg<Runnable>().run(); true }
        fireCallback(false)
        fireCallback(true)
        assertFalse(mediaControl.wasRecentlyPausedByCap)
    }

    @Test
    fun `concurrent sendPause during sendPlay dispatch keeps the later pause armed`() = runTest {
        // Repro for the lost update: sendPlay's flag write used to land after sendKey's delay(100),
        // so a sendPause arming capPaused inside that window was overwritten back to false.
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        val dispatched = mutableListOf<KeyEvent>()
        // KeyEvent getters throw in plain JVM unit tests (mockable android.jar), so the dispatched
        // events are counted, and the pairs are told apart by the flag state each one was sent
        // under: the play pair dispatches with capPaused=false, the pause pair with capPaused=true.
        // Interleaved dispatch would therefore not produce false,false,true,true.
        val armedAtDispatch = mutableListOf<Boolean>()
        every { audioManager.dispatchMediaKeyEvent(capture(dispatched)) } answers {
            armedAtDispatch += mediaControl.wasRecentlyPausedByCap
        }

        val playJob = launch { mediaControl.sendPlay() }
        runCurrent()
        val pauseJob = launch { mediaControl.sendPause(rememberForResume = true) }
        runCurrent()
        advanceUntilIdle()
        playJob.join()
        pauseJob.join()

        assertTrue(mediaControl.wasRecentlyPausedByCap)
        assertEquals(4, dispatched.size)
        assertEquals(listOf(false, false, true, true), armedAtDispatch)
    }

    @Test
    fun `cancellation during sendPlay dispatch neither strands the flag nor an unpaired key event`() = runTest {
        every { audioManager.isMusicActive } returns true
        mediaControl.sendPause(rememberForResume = true)
        assertTrue(mediaControl.wasRecentlyPausedByCap)
        clearMocks(audioManager, answers = false, recordedCalls = true)

        val dispatched = mutableListOf<KeyEvent>()
        every { audioManager.dispatchMediaKeyEvent(capture(dispatched)) } just Runs

        val job = launch { mediaControl.sendPlay() }
        runCurrent() // DOWN is out, we are inside sendKey's delay
        job.cancel()
        advanceUntilIdle()

        // Cleared before the first suspension, so a cancelled resume cannot strand a stale arm.
        assertFalse(mediaControl.wasRecentlyPausedByCap)
        // NonCancellable completes the pair rather than leaving a held key.
        assertEquals(2, dispatched.size)
    }

    @Test
    fun `playback callback is registered with the injected handler rather than null`() {
        // Regression for the ANR cluster around the playback callback: registration itself is
        // binder work into AudioService and must not run on the main looper, which is what passing
        // `null` here would select. The handler choice also decides the Looper that serializes
        // callback delivery, and the snapshot-based edge detection depends on that ordering.
        // Scope of this assertion: it only proves the injected handler is forwarded, not which
        // Looper that handler is bound to — a JVM unit test cannot inspect a Looper here (this
        // module does not use Robolectric). The Looper identity is covered instead by
        // `AndroidModule.audioCallbackHandler()`, which is the single place that constructs it.
        verify { audioManager.registerAudioPlaybackCallback(any(), handler) }
    }

    @Test
    fun `construction does no binder work on the calling thread`() {
        // Regression for the ANR cluster in MediaControl's constructor: it runs during
        // App.onCreate via Hilt, so nothing here may touch AudioService inline.
        val freshAudioManager = mockk<AudioManager>(relaxed = true)
        val freshHandler = mockk<Handler>()
        every { freshHandler.post(any()) } returns true

        MediaControl(freshAudioManager, timeSource, freshHandler, focusRequestFactory)

        verify(exactly = 0) { freshAudioManager.isMusicActive }
        verify(exactly = 0) { freshAudioManager.registerAudioPlaybackCallback(any(), any()) }
        verify(exactly = 1) { freshHandler.post(any()) }
    }

    @Test
    fun `init registers the callback before seeding the active flag`() {
        // Seeding first would leave a gap in which a transition is neither delivered (not yet
        // registered) nor reflected in the seed.
        val freshAudioManager = mockk<AudioManager>(relaxed = true)
        every { freshAudioManager.isMusicActive } returns false
        every { freshAudioManager.registerAudioPlaybackCallback(any(), any()) } just Runs
        val freshHandler = mockk<Handler>()
        val runnableSlot = slot<Runnable>()
        every { freshHandler.post(capture(runnableSlot)) } returns true

        MediaControl(freshAudioManager, timeSource, freshHandler, focusRequestFactory)
        runnableSlot.captured.run()

        verifyOrder {
            freshAudioManager.registerAudioPlaybackCallback(any(), freshHandler)
            freshAudioManager.isMusicActive
        }
    }

    @Test
    fun `media control still works before the init runnable has drained`() = runTest {
        // The window between construction and the posted runnable running: no callback is live
        // yet, but the key-dispatch paths must behave normally.
        val freshAudioManager = mockk<AudioManager>(relaxed = true)
        every { freshAudioManager.isMusicActive } returns true
        every { freshAudioManager.dispatchMediaKeyEvent(any()) } just Runs
        every { freshAudioManager.registerAudioPlaybackCallback(any(), any()) } just Runs
        val freshHandler = mockk<Handler>()
        // The first post is the init runnable and is deliberately left undrained — that is the
        // premise of this test. Everything posted afterwards (the pause arm) runs inline.
        var initRunnable: Runnable? = null
        every { freshHandler.post(any()) } answers {
            val posted = firstArg<Runnable>()
            if (initRunnable == null) initRunnable = posted else posted.run()
            true
        }

        val undrained = MediaControl(freshAudioManager, timeSource, freshHandler, focusRequestFactory)

        assertFalse(undrained.wasRecentlyPausedByCap)

        assertTrue(undrained.sendPause(rememberForResume = true))
        assertTrue(undrained.wasRecentlyPausedByCap)
        verify(exactly = 2) { freshAudioManager.dispatchMediaKeyEvent(any()) }

        clearMocks(freshAudioManager, answers = false, recordedCalls = true)

        undrained.sendPlay()
        assertFalse(undrained.wasRecentlyPausedByCap)
        verify(exactly = 2) { freshAudioManager.dispatchMediaKeyEvent(any()) }
    }

    /**
     * The read-back deliberately differs from the requested target: Bluetooth absolute-volume routes
     * quantize, and the caller has to restore against what actually landed, not what was asked for.
     */
    @Test
    fun `duckMusicVolume reports the level the system actually applied`() {
        every { audioManager.isMusicActive } returns true
        every { audioManager.isVolumeFixed } returns false
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 100
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returnsMany listOf(40, 22)

        mediaControl.duckMusicVolume(50) shouldBe MediaControl.DuckOutcome.Ducked(priorVolume = 40, appliedVolume = 22)

        verify { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 20, 0) }
    }

    /**
     * A volume that came back *higher* (user raised it between the two reads) is not a refusal: it
     * must map to [MediaControl.DuckOutcome.Skipped], not `Unchanged`, or the caller would chase the
     * audio-focus fallback on a device whose volume writes work fine.
     */
    @Test
    fun `duckMusicVolume treats a raised volume as skipped, not a refusal`() {
        every { audioManager.isMusicActive } returns true
        every { audioManager.isVolumeFixed } returns false
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 100
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returnsMany listOf(40, 55)

        mediaControl.duckMusicVolume(50) shouldBe MediaControl.DuckOutcome.Skipped
    }

    /**
     * ColorOS 16 accepts `setStreamVolume` from a backgrounded app, raises nothing, and leaves the
     * volume where it was. That is the case the audio-focus fallback exists for, so it reports
     * [MediaControl.DuckOutcome.Unchanged] rather than a duck the caller would later "restore".
     */
    @Test
    fun `duckMusicVolume reports a silently ignored write as unchanged`() {
        every { audioManager.isMusicActive } returns true
        every { audioManager.isVolumeFixed } returns false
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 100
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returnsMany listOf(40, 40)

        mediaControl.duckMusicVolume(100) shouldBe MediaControl.DuckOutcome.Unchanged(priorVolume = 40)

        verify { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
    }

    @Test
    fun `duckMusicVolume skips when nothing is playing`() {
        every { audioManager.isMusicActive } returns false

        mediaControl.duckMusicVolume(50) shouldBe MediaControl.DuckOutcome.Skipped

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `duckMusicVolume skips on fixed-volume devices`() {
        every { audioManager.isMusicActive } returns true
        every { audioManager.isVolumeFixed } returns true

        mediaControl.duckMusicVolume(50) shouldBe MediaControl.DuckOutcome.Skipped

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `duckMusicVolume skips when the reduction leaves no headroom`() {
        every { audioManager.isMusicActive } returns true
        every { audioManager.isVolumeFixed } returns false
        every { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) } returns 100
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 0

        mediaControl.duckMusicVolume(50) shouldBe MediaControl.DuckOutcome.Skipped

        verify(exactly = 0) { audioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `requestDuckFocus reports a granted request as held`() {
        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        mediaControl.requestDuckFocus() shouldBe true
        mediaControl.isDuckFocusHeld shouldBe true
    }

    @Test
    fun `requestDuckFocus reports a denied request and retries on the next call`() {
        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_FAILED

        mediaControl.requestDuckFocus() shouldBe false
        mediaControl.isDuckFocusHeld shouldBe false

        // A denial leaves nothing held, so the next attempt must issue a fresh request.
        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        mediaControl.requestDuckFocus() shouldBe true
        mediaControl.isDuckFocusHeld shouldBe true

        verify(exactly = 2) { audioManager.requestAudioFocus(focusRequest) }
    }

    @Test
    fun `requestDuckFocus is idempotent while focus is held`() {
        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        mediaControl.requestDuckFocus() shouldBe true
        mediaControl.requestDuckFocus() shouldBe true

        verify(exactly = 1) { audioManager.requestAudioFocus(focusRequest) }
    }

    @Test
    fun `abandonDuckFocus only abandons what is actually held`() {
        // Not held: abandoning must not touch the audio system at all.
        mediaControl.abandonDuckFocus()
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }

        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        mediaControl.requestDuckFocus() shouldBe true

        mediaControl.abandonDuckFocus()
        mediaControl.abandonDuckFocus()
        mediaControl.isDuckFocusHeld shouldBe false
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(focusRequest) }

        // Re-requesting after an abandon starts a new request rather than reusing the stale state.
        mediaControl.requestDuckFocus() shouldBe true
        mediaControl.isDuckFocusHeld shouldBe true
        verify(exactly = 2) { audioManager.requestAudioFocus(focusRequest) }
    }

    @Test
    fun `the focus listener drops the held state on a permanent loss only`() {
        every { audioManager.requestAudioFocus(focusRequest) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        mediaControl.requestDuckFocus() shouldBe true
        val listener = focusListenerSlot.captured

        // A transient loss is temporary — the request stays valid and we still hold it.
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        mediaControl.isDuckFocusHeld shouldBe true

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        mediaControl.isDuckFocusHeld shouldBe false

        // Nothing left to release: the system already took it.
        mediaControl.abandonDuckFocus()
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
    }
}
