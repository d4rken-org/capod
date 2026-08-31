package eu.darken.capod.monitor.core.worker

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.main.core.MonitorMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The AUTOMATIC teardown is only cancelled by a new state emission, but a start request that finds
 * a live session short-circuits without producing one — the connection state that would abort the
 * countdown lags the Bluetooth event by roughly a second. A request landing in the tail of the
 * window used to be acknowledged and the session torn down anyway.
 */
class MonitorModeFlowTest : BaseTest() {

    private fun state(
        mode: MonitorMode = MonitorMode.AUTOMATIC,
        hasProfiles: Boolean = true,
        knownAddresses: Set<BluetoothAddress> = setOf(KNOWN_ADDRESS),
        connectedAddresses: Set<BluetoothAddress> = emptySet(),
        hasAapSession: Boolean = false,
    ) = MonitorModeState(
        mode = mode,
        hasProfiles = hasProfiles,
        knownAddresses = knownAddresses,
        connectedAddresses = connectedAddresses,
        hasAapSession = hasAapSession,
    )

    @Test
    fun `nothing connected tears the session down after the timeout`() = runTest {
        var teardowns = 0
        val job = monitorModeFlow(
            tag = TAG,
            modeStates = flowOf(state()),
            startSignal = MutableStateFlow(0L),
            onTeardown = { teardowns++ },
        ).launchIn(this)

        advanceTimeBy(TIMEOUT)
        teardowns shouldBe 0

        runCurrent()
        teardowns shouldBe 1

        job.cancel()
    }

    @Test
    fun `a start request during the window re-arms the countdown`() = runTest {
        var teardowns = 0
        val startSignal = MutableStateFlow(0L)
        val job = monitorModeFlow(
            tag = TAG,
            modeStates = flowOf(state()),
            startSignal = startSignal,
            onTeardown = { teardowns++ },
        ).launchIn(this)

        val bumpedAt = 14_750L
        advanceTimeBy(bumpedAt)
        runCurrent()
        startSignal.value++

        // The original window would have expired here.
        advanceTimeBy(TIMEOUT - bumpedAt)
        runCurrent()
        teardowns shouldBe 0

        // The re-armed window runs from the start request, not from the state emission.
        advanceTimeBy(bumpedAt)
        teardowns shouldBe 0

        runCurrent()
        teardowns shouldBe 1

        job.cancel()
    }

    @Test
    fun `a connected device aborts the countdown`() = runTest {
        var teardowns = 0
        val job = monitorModeFlow(
            tag = TAG,
            modeStates = flowOf(state(connectedAddresses = setOf(KNOWN_ADDRESS))),
            startSignal = MutableStateFlow(0L),
            onTeardown = { teardowns++ },
        ).launchIn(this)

        advanceTimeBy(10 * TIMEOUT)
        runCurrent()
        teardowns shouldBe 0

        job.cancel()
    }

    @Test
    fun `manual mode tears the session down immediately`() = runTest {
        var teardowns = 0
        val job = monitorModeFlow(
            tag = TAG,
            modeStates = flowOf(state(mode = MonitorMode.MANUAL)),
            startSignal = MutableStateFlow(0L),
            onTeardown = { teardowns++ },
        ).launchIn(this)

        runCurrent()
        teardowns shouldBe 1

        job.cancel()
    }

    companion object {
        private const val TAG = "MonitorModeFlowTest"
        private const val TIMEOUT = 15 * 1000L
        private const val KNOWN_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
