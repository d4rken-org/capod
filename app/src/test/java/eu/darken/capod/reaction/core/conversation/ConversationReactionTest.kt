package eu.darken.capod.reaction.core.conversation

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.ConversationAwarenessEvent
import eu.darken.capod.profiles.core.ReactionConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource

class ConversationReactionTest : BaseTest() {

    private val primaryAddress: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private val otherAddress: BluetoothAddress = "11:22:33:44:55:66"

    // Mirrors of ConversationReaction's private timing constants. STALE_TIMEOUT: long backstop
    // while only START frames were seen. WIND_DOWN_TIMEOUT: short fuse armed by a HOLD frame
    // (wind-down begun, terminal imminent — but sometimes dropped, #608).
    private val staleTimeoutMs = 5L * 60 * 1000
    private val windDownTimeoutMs = 6_000L

    private lateinit var eventsFlow: MutableSharedFlow<Pair<BluetoothAddress, ConversationAwarenessEvent>>
    private lateinit var statesFlow: MutableStateFlow<Map<BluetoothAddress, AapPodState>>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var aapManager: AapConnectionManager
    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var mediaControl: MediaControl
    private lateinit var timeSource: TestTimeSource

    private fun mockPodDevice(
        address: BluetoothAddress,
        action: ConversationAction,
        reduction: Int = 50,
        worn: Boolean = true,
    ): PodDevice = mockk(relaxed = true) {
        every { profileId } returns address
        every { this@mockk.address } returns address
        every { reactions } returns ReactionConfig(
            conversationAction = action,
            conversationVolumeReduction = reduction,
        )
        every { isBeingWorn } returns worn
    }

    @BeforeEach
    fun setup() {
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        statesFlow = MutableStateFlow(mapOf(primaryAddress to mockk(relaxed = true)))
        devicesFlow = MutableStateFlow(listOf(mockPodDevice(primaryAddress, ConversationAction.LOWER_VOLUME)))
        aapManager = mockk(relaxed = true) {
            every { conversationalAwarenessEvents } returns eventsFlow
            every { allStates } returns statesFlow
        }
        deviceMonitor = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }
        mediaControl = mockk(relaxed = true) {
            coEvery { sendPause(any()) } returns true
            every { isPlaying } returns false
            every { duckMusicVolume(any()) } returns MediaControl.VolumeDuck(priorVolume = 10, appliedVolume = 5)
            every { currentMusicVolume() } returns 5
        }
        timeSource = TestTimeSource()
    }

    // appScope = the test scope so the stale timer runs on the controllable virtual clock.
    private fun TestScope.launchReaction() = ConversationReaction(
        aapManager = aapManager,
        deviceMonitor = deviceMonitor,
        mediaControl = mediaControl,
        appScope = this,
        timeSource = timeSource,
    ).monitor().launchIn(this)

    private suspend fun TestScope.emit(address: BluetoothAddress, event: ConversationAwarenessEvent) {
        eventsFlow.emit(address to event)
        runCurrent()
    }

    @Test
    fun `LOWER_VOLUME start ducks, stop restores`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        verify(exactly = 1) { mediaControl.duckMusicVolume(50) }
        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }

        emit(primaryAddress, ConversationAwarenessEvent.STOP)
        verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
        job.cancel()
    }

    @Test
    fun `LOWER_VOLUME restores unconditionally even if the volume was moved meanwhile`() = runTest(UnconfinedTestDispatcher()) {
        // Something else (e.g. another volume-manager app) moved the volume during the duck, so the
        // current reading no longer matches what we applied — we must still restore to the saved prior.
        every { mediaControl.currentMusicVolume() } returns 7
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.STOP)

        verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
        job.cancel()
    }

    @Test
    fun `LOWER_VOLUME owner leaving the AAP state map restores volume`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        statesFlow.value = emptyMap() // device disconnected before STOP arrived
        runCurrent()

        verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
        job.cancel()
    }

    @Test
    fun `LOWER_VOLUME missed STOP restores via stale timeout`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        verify(exactly = 1) { mediaControl.duckMusicVolume(50) }
        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }

        // No STOP arrives; frames cease. After the stale timeout, volume must be restored.
        advanceTimeBy(staleTimeoutMs + 500)
        runCurrent()

        verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
        job.cancel()
    }

    @Test
    fun `dropped terminal frame restores via the wind-down fuse`() = runTest(UnconfinedTestDispatcher()) {
        // Regression for #608 (fw …6589): the wind-down flurry 1,2,3,0xB,4 arrives but the terminal
        // 8/9 is dropped entirely. The HOLD frames prove the wind-down began, so the short fuse must
        // restore the volume — not strand it low until the 5-minute backstop.
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START) // 1
        emit(primaryAddress, ConversationAwarenessEvent.START) // 2
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 3
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 0x0B
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 4 — last frame ever, no terminal
        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }

        advanceTimeBy(windDownTimeoutMs + 500)
        runCurrent()
        verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
        job.cancel()
    }

    @Test
    fun `HOLD frames re-arm the wind-down fuse so intra-flurry gaps do not fire it`() =
        runTest(UnconfinedTestDispatcher()) {
            // Gaps of up to 2.8s were observed between consecutive wind-down frames — each HOLD
            // must re-arm the short fuse rather than letting a mid-flurry gap disengage early.
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START)
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)
            advanceTimeBy(windDownTimeoutMs * 2 / 3)
            runCurrent()
            emit(primaryAddress, ConversationAwarenessEvent.HOLD) // re-arms
            advanceTimeBy(windDownTimeoutMs * 2 / 3) // <1 fuse since the last HOLD
            runCurrent()
            verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }

            advanceTimeBy(windDownTimeoutMs) // now >1 fuse since the last frame
            runCurrent()
            verify(exactly = 1) { mediaControl.restoreMusicVolume(10) }
            job.cancel()
        }

    @Test
    fun `fresh START during a wind-down cancels the short fuse`() = runTest(UnconfinedTestDispatcher()) {
        // Wearer resumes speaking while the wind-down fuse is burning: the START must switch back
        // to the long backstop — otherwise media resumes ~6s into the renewed conversation.
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // wind-down begins, short fuse armed
        emit(primaryAddress, ConversationAwarenessEvent.START) // speaking again
        advanceTimeBy(windDownTimeoutMs * 3)
        runCurrent()

        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }
        job.cancel()
    }

    @Test
    fun `PAUSE with dropped terminal frame resumes via the wind-down fuse`() = runTest(UnconfinedTestDispatcher()) {
        // Same #608 single-pod scenario but with the PAUSE action: the fuse-driven disengage goes
        // through the resume guards (age window, worn, already-playing) — at ~6s the pause is well
        // inside PAUSE_RESUME_WINDOW, so media must resume rather than stay paused indefinitely.
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        coVerify(exactly = 1) { mediaControl.sendPause(false) }
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // wind-down begins, terminal dropped
        coVerify(exactly = 0) { mediaControl.sendPlay() }

        advanceTimeBy(windDownTimeoutMs + 500)
        runCurrent()
        coVerify(exactly = 1) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `HOLD without a prior START does not engage`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.HOLD)

        verify(exactly = 0) { mediaControl.duckMusicVolume(any()) }
        job.cancel()
    }

    @Test
    fun `PAUSE start pauses, stop resumes when worn and idle`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        coVerify(exactly = 1) { mediaControl.sendPause(false) }

        emit(primaryAddress, ConversationAwarenessEvent.STOP)
        coVerify(exactly = 1) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `PAUSE stop does not resume when pods not worn`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE, worn = false))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.STOP)

        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `PAUSE stop does not resume when something is already playing`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        every { mediaControl.isPlaying } returns true // user/app restarted playback during the talk
        emit(primaryAddress, ConversationAwarenessEvent.STOP)

        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `PAUSE resumes on stop even if the action was switched away mid-talk`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        coVerify(exactly = 1) { mediaControl.sendPause(false) }

        // User changes the action mid-conversation; we must still undo the pause WE caused.
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.LOWER_VOLUME))
        emit(primaryAddress, ConversationAwarenessEvent.STOP)

        coVerify(exactly = 1) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `NOTHING action ignores speaking`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.NOTHING))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)

        verify(exactly = 0) { mediaControl.duckMusicVolume(any()) }
        coVerify(exactly = 0) { mediaControl.sendPause(any()) }
        job.cancel()
    }

    @Test
    fun `non-primary device start is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(otherAddress, ConversationAwarenessEvent.START)

        verify(exactly = 0) { mediaControl.duckMusicVolume(any()) }
        job.cancel()
    }

    @Test
    fun `stop without a prior start is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.STOP)

        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }
        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `duplicate start does not duck twice`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.START) // status 1 then 2 both classify as START

        verify(exactly = 1) { mediaControl.duckMusicVolume(any()) }
        job.cancel()
    }

    @Test
    fun `PAUSE stays paused through frame silence, resumes only on explicit STOP, then re-engages`() =
        runTest(UnconfinedTestDispatcher()) {
            // The pod sends NO frames during continuous speech (29s silent gaps observed), then a
            // terminal STOP. The old 12s stale timeout resumed media mid-speech; the long backstop
            // must not, and a fresh talk must re-arm.
            devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START)
            emit(primaryAddress, ConversationAwarenessEvent.START) // status 1 then 2
            coVerify(exactly = 1) { mediaControl.sendPause(false) }

            advanceTimeBy(20_000) // 20s of silence — well under the backstop
            runCurrent()
            coVerify(exactly = 0) { mediaControl.sendPlay() } // NOT resumed mid-speech

            emit(primaryAddress, ConversationAwarenessEvent.STOP) // wearer stopped → pod's terminal frame
            coVerify(exactly = 1) { mediaControl.sendPlay() }

            emit(primaryAddress, ConversationAwarenessEvent.START) // a fresh talk re-arms
            coVerify(exactly = 2) { mediaControl.sendPause(false) }
            job.cancel()
        }

    @Test
    fun `HOLD frames keep media paused, only terminal STOP resumes`() = runTest(UnconfinedTestDispatcher()) {
        // fw …6503-style wind-down 1,2,3,0xB,4,8,9: transitional frames must not resume.
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 3
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 0x0B
        emit(primaryAddress, ConversationAwarenessEvent.HOLD) // 4
        coVerify(exactly = 0) { mediaControl.sendPlay() }

        emit(primaryAddress, ConversationAwarenessEvent.STOP) // 8
        coVerify(exactly = 1) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `RESUME keeps media paused through a bursty conversation, resumes only at the real terminal`() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression for the fw …6861 status-5 bug. Bursty talking emits 1,2 then 3,5 (pause,
            // resume) pairs while CA stays engaged, ending with the real wind-down 3,0xB,4,8,9.
            // Status 5 was misclassified as a terminal STOP, so media resumed on the first burst
            // pause and — with no fresh 1/2 onset mid-conversation — never paused again. RESUME must
            // keep media paused until the genuine terminal.
            devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START) // 1
            emit(primaryAddress, ConversationAwarenessEvent.START) // 2
            coVerify(exactly = 1) { mediaControl.sendPause(false) }

            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3 pause
            emit(primaryAddress, ConversationAwarenessEvent.RESUME) // 5 resume
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3 pause
            emit(primaryAddress, ConversationAwarenessEvent.RESUME) // 5 resume
            coVerify(exactly = 0) { mediaControl.sendPlay() }       // stayed paused through the bursts

            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 0x0B
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 4
            emit(primaryAddress, ConversationAwarenessEvent.STOP)   // 8 terminal
            coVerify(exactly = 1) { mediaControl.sendPlay() }
            job.cancel()
        }

    @Test
    fun `RESUME cancels the wind-down fuse`() = runTest(UnconfinedTestDispatcher()) {
        // A pause (3) arms the short fuse; a resume (5) must cancel it and switch back to the long
        // backstop — otherwise media resumes ~6s into renewed speech.
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3 — wind-down fuse armed
        advanceTimeBy(windDownTimeoutMs * 2 / 3)
        runCurrent()
        emit(primaryAddress, ConversationAwarenessEvent.RESUME) // 5 — speech resumed, cancel fuse
        advanceTimeBy(windDownTimeoutMs * 2)                    // well past the original fuse
        runCurrent()

        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `wind-down after a RESUME still disengages via the fuse (dropped terminal)`() =
        runTest(UnconfinedTestDispatcher()) {
            // After a resume re-arms the long backstop, a later genuine wind-down (0xB,4 with the
            // 8,9 terminal dropped — single-pod) must still disengage via the short fuse. Proves the
            // RESUME keep-alive doesn't permanently disable #608 recovery.
            devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START)
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3 pause
            emit(primaryAddress, ConversationAwarenessEvent.RESUME) // 5 resume → long backstop
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3 pause again
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 0x0B wind-down
            emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 4 — terminal dropped
            coVerify(exactly = 0) { mediaControl.sendPlay() }

            advanceTimeBy(windDownTimeoutMs + 500)
            runCurrent()
            coVerify(exactly = 1) { mediaControl.sendPlay() }
            job.cancel()
        }

    @Test
    fun `PAUSE resumes on the terminal even after a conversation longer than the resume window`() =
        runTest(UnconfinedTestDispatcher()) {
            // A bursty conversation that runs past PAUSE_RESUME_WINDOW: the explicit STOP must still
            // resume. The age guard applies only to the inferred stale backstop, not to a real
            // terminal. Advance BOTH clocks so the window is genuinely exercised (TestTimeSource
            // drives `age`).
            devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START)
            coVerify(exactly = 1) { mediaControl.sendPause(false) }

            repeat(3) {
                timeSource.advanceBy(java.time.Duration.ofSeconds(60))
                advanceTimeBy(60_000) // < STALE_TIMEOUT, so the backstop never fires
                runCurrent()
                emit(primaryAddress, ConversationAwarenessEvent.RESUME)
            }
            coVerify(exactly = 0) { mediaControl.sendPlay() } // 3 min in, still paused

            emit(primaryAddress, ConversationAwarenessEvent.STOP) // explicit terminal
            coVerify(exactly = 1) { mediaControl.sendPlay() }
            job.cancel()
        }

    @Test
    fun `PAUSE resumes on a direct STOP after long frame silence`() = runTest(UnconfinedTestDispatcher()) {
        // Continuous speech sends no frames; then a direct terminal (no preceding wind-down) arrives
        // past PAUSE_RESUME_WINDOW but before STALE_TIMEOUT. An explicit STOP is positive evidence CA
        // ended now, so it must resume regardless of engage-age. (Fails if the age guard is applied to
        // explicit terminals.)
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        coVerify(exactly = 1) { mediaControl.sendPause(false) }

        timeSource.advanceBy(java.time.Duration.ofSeconds(150)) // > 2-min window, < 5-min backstop
        advanceTimeBy(150_000)
        runCurrent()
        coVerify(exactly = 0) { mediaControl.sendPlay() } // still paused, no frames yet

        emit(primaryAddress, ConversationAwarenessEvent.STOP)
        coVerify(exactly = 1) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `PAUSE does not resume when the stale backstop fires past the resume window`() =
        runTest(UnconfinedTestDispatcher()) {
            // The inferred end (5-min backstop, no terminal ever) keeps the age guard: a long-stale
            // pause must NOT surprise-resume.
            devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
            val job = launchReaction()

            emit(primaryAddress, ConversationAwarenessEvent.START)
            coVerify(exactly = 1) { mediaControl.sendPause(false) }

            timeSource.advanceBy(java.time.Duration.ofMinutes(5))
            advanceTimeBy(5L * 60 * 1000 + 500) // STALE_TIMEOUT fires
            runCurrent()

            coVerify(exactly = 0) { mediaControl.sendPlay() } // inferred stale end → not resumed
            job.cancel()
        }

    @Test
    fun `RESUME without a prior start is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.RESUME) // stray 5, nothing engaged

        coVerify(exactly = 0) { mediaControl.sendPause(any()) }
        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `LOWER_VOLUME RESUME keeps the volume ducked`() = runTest(UnconfinedTestDispatcher()) {
        // Same status-5 bug seen on the default action: it restored volume on the first 3→5 pause.
        val job = launchReaction() // devicesFlow default = LOWER_VOLUME

        emit(primaryAddress, ConversationAwarenessEvent.START)
        verify(exactly = 1) { mediaControl.duckMusicVolume(any()) }
        emit(primaryAddress, ConversationAwarenessEvent.HOLD)   // 3
        emit(primaryAddress, ConversationAwarenessEvent.RESUME) // 5
        verify(exactly = 0) { mediaControl.restoreMusicVolume(any()) }
        job.cancel()
    }

    @Test
    fun `STOP from a non-owner does not disengage the active owner`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(otherAddress, ConversationAwarenessEvent.STOP) // a different device's STOP

        coVerify(exactly = 0) { mediaControl.sendPlay() }
        job.cancel()
    }

    @Test
    fun `explicit STOP then stale backstop does not double-resume`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, ConversationAction.PAUSE))
        val job = launchReaction()

        emit(primaryAddress, ConversationAwarenessEvent.START)
        emit(primaryAddress, ConversationAwarenessEvent.STOP)
        coVerify(exactly = 1) { mediaControl.sendPlay() }

        advanceTimeBy(staleTimeoutMs + 500) // backstop would fire if STOP hadn't cancelled it
        runCurrent()
        coVerify(exactly = 1) { mediaControl.sendPlay() } // still only once
        job.cancel()
    }
}
