package eu.darken.capod.reaction.core.sleep

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import java.time.Duration

class SleepReactionTest : BaseTest() {

    private val primaryAddress: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private val otherAddress: BluetoothAddress = "11:22:33:44:55:66"

    private lateinit var incomingSleepFlow: MutableSharedFlow<BluetoothAddress>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var aapManager: AapConnectionManager
    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var mediaControl: MediaControl
    private lateinit var notifications: SleepReactionNotifications
    private lateinit var timeSource: TestTimeSource

    private fun mockPodDevice(address: BluetoothAddress, label: String? = "Custom Label"): PodDevice {
        val addrValue = address
        val labelValue = label
        return mockk(relaxed = true) {
            every { profileId } returns addrValue
            every { this@mockk.address } returns addrValue
            every { this@mockk.label } returns labelValue
            every { model } returns PodModel.AIRPODS_PRO3
        }
    }

    @BeforeEach
    fun setup() {
        incomingSleepFlow = MutableSharedFlow(extraBufferCapacity = 16)
        devicesFlow = MutableStateFlow(listOf(mockPodDevice(primaryAddress)))
        aapManager = mockk(relaxed = true) {
            every { sleepEvents } returns incomingSleepFlow
        }
        deviceMonitor = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }
        mediaControl = mockk(relaxed = true) {
            coEvery { sendPause() } returns true
        }
        notifications = mockk(relaxed = true)
        timeSource = TestTimeSource()
    }

    private fun createReaction(): SleepReaction = SleepReaction(
        aapManager = aapManager,
        deviceMonitor = deviceMonitor,
        mediaControl = mediaControl,
        notifications = notifications,
        timeSource = timeSource,
    )

    private fun TestScope.launchReaction(): kotlinx.coroutines.Job =
        createReaction().monitor().launchIn(this)

    @Test
    fun `primary device match triggers pause and notification`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 1) { mediaControl.sendPause() }
        verify(exactly = 1) { notifications.show("Custom Label") }
        job.cancel()
    }

    @Test
    fun `falls back to model label when profile label is null`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(mockPodDevice(primaryAddress, label = null))
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        verify(exactly = 1) { notifications.show(PodModel.AIRPODS_PRO3.label) }
        job.cancel()
    }

    @Test
    fun `non-primary device is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        incomingSleepFlow.emit(otherAddress)
        runCurrent()

        coVerify(exactly = 0) { mediaControl.sendPause() }
        verify(exactly = 0) { notifications.show(any()) }
        job.cancel()
    }

    @Test
    fun `cooldown suppresses second trigger within 5 minutes`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()
        coVerify(exactly = 1) { mediaControl.sendPause() }

        timeSource.advanceBy(Duration.ofMinutes(4))
        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 1) { mediaControl.sendPause() }
        verify(exactly = 1) { notifications.show(any()) }
        job.cancel()
    }

    @Test
    fun `cooldown expires after 5 minutes`() = runTest(UnconfinedTestDispatcher()) {
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        timeSource.advanceBy(Duration.ofMinutes(5).plusSeconds(1))
        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 2) { mediaControl.sendPause() }
        verify(exactly = 2) { notifications.show(any()) }
        job.cancel()
    }

    @Test
    fun `no primary device is ignored`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = emptyList()
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 0) { mediaControl.sendPause() }
        verify(exactly = 0) { notifications.show(any()) }
        job.cancel()
    }

    @Test
    fun `cooldown is per-device`() = runTest(UnconfinedTestDispatcher()) {
        // Each device has its own cooldown — a fresh address within another device's cooldown
        // window still triggers.
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()
        coVerify(exactly = 1) { mediaControl.sendPause() }

        devicesFlow.value = listOf(mockPodDevice(otherAddress, label = "B"))
        incomingSleepFlow.emit(otherAddress)
        runCurrent()

        coVerify(exactly = 2) { mediaControl.sendPause() }
        verify(exactly = 1) { notifications.show("Custom Label") }
        verify(exactly = 1) { notifications.show("B") }
        job.cancel()
    }

    @Test
    fun `sendPause returning false skips the notification`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { mediaControl.sendPause() } returns false
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        // sendPause is still attempted — the reaction relies on its return as the atomic check.
        coVerify(exactly = 1) { mediaControl.sendPause() }
        verify(exactly = 0) { notifications.show(any()) }
        job.cancel()
    }

    @Test
    fun `sendPause returning false does not consume cooldown`() = runTest(UnconfinedTestDispatcher()) {
        // First event fires while nothing is playing → sendPause returns false → no cooldown
        // recorded. Second event 30s later while music IS playing must still fire even though
        // we're well inside what would have been the 5-minute window.
        coEvery { mediaControl.sendPause() } returns false
        val job = launchReaction()

        incomingSleepFlow.emit(primaryAddress)
        runCurrent()
        verify(exactly = 0) { notifications.show(any()) }

        coEvery { mediaControl.sendPause() } returns true
        timeSource.advanceBy(Duration.ofSeconds(30))
        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 2) { mediaControl.sendPause() }
        verify(exactly = 1) { notifications.show("Custom Label") }
        job.cancel()
    }

    @Test
    fun `non-primary event never consumes cooldown`() = runTest(UnconfinedTestDispatcher()) {
        // A burst of non-primary events should not silently burn the cooldown — otherwise a
        // later primary-device event could be incorrectly suppressed.
        val job = launchReaction()

        incomingSleepFlow.emit(otherAddress)
        incomingSleepFlow.emit(otherAddress)
        incomingSleepFlow.emit(otherAddress)
        runCurrent()

        // Now the real primary-device event arrives immediately after — must fire.
        incomingSleepFlow.emit(primaryAddress)
        runCurrent()

        coVerify(exactly = 1) { mediaControl.sendPause() }
        verify(exactly = 1) { notifications.show("Custom Label") }
        job.cancel()
    }
}
