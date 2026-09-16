package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothDevice
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AclPagerTest : BaseTest() {

    private val rawDevice = mockk<BluetoothDevice>().apply {
        every { address } returns ADDRESS
        every { name } returns "Pods"
    }
    private val device = BluetoothDevice2(
        address = ADDRESS,
        name = "Pods",
        seenFirstAt = Instant.EPOCH,
        internal = rawDevice,
    )
    private val addressLessDevice = BluetoothDevice2(
        address = ADDRESS,
        name = "Pods",
        seenFirstAt = Instant.EPOCH,
        internal = null,
    )

    private val connectedFlow = MutableStateFlow<List<BluetoothDevice2>>(emptyList())
    private val aclFlow = MutableStateFlow<Set<BluetoothAddress>>(emptySet())
    private val bluetoothManager = mockk<BluetoothManager2>().apply {
        every { connectedDevices } returns connectedFlow
        every { aclConnectedAddresses } returns aclFlow
    }
    private val socketFactory = mockk<L2capSocketFactory>().apply {
        every { createSocket(any(), any()) } throws IOException("no socket in tests")
    }

    /** Clock that follows the test scheduler's virtual time so cooldown/deadlines advance with `delay`. */
    private fun TestScope.schedulerTimeSource() = object : TimeSource {
        override fun now(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
        override fun currentTimeMillis(): Long = testScheduler.currentTime
        override fun elapsedRealtime(): Long = testScheduler.currentTime
        override fun elapsedRealtimeNanos(): Long = testScheduler.currentTime * 1_000_000
        override fun uptimeMillis(): Long = testScheduler.currentTime
    }

    private fun TestScope.createPager() = AclPager(
        context = mockk(relaxed = true),
        bluetoothManager = bluetoothManager,
        socketFactory = socketFactory,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        timeSource = schedulerTimeSource(),
    )

    @Test
    fun `device without internal handle is UNAVAILABLE`() = runTest {
        val pager = createPager()

        pager.page(addressLessDevice) shouldBe AclPager.Result.UNAVAILABLE

        verify(exactly = 0) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `SDP page succeeds when the device shows up in the headset profile`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        val pager = createPager()

        val result = async { pager.page(device) }
        runCurrent()
        advanceTimeBy(AclPager.POLL_MS * 2)
        connectedFlow.value = listOf(device)
        advanceTimeBy(AclPager.POLL_MS * 2)

        result.await() shouldBe AclPager.Result.CONNECTED
        verify(exactly = 0) { socketFactory.createSocket(any(), any()) }
    }

    @Test
    fun `re-pages while the ACL link is down and gives up after the window`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        val pager = createPager()

        val result = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)

        // RFCOMM and L2CAP socket creation both fail in tests, but re-pages are still attempted,
        // and every missed broadcast starts another SDP cycle.
        result.await() shouldBe AclPager.Result.NOT_CONNECTED
        verify(atLeast = 3) { socketFactory.createSocket(rawDevice, any()) }
        verify(exactly = AclPager.MAX_CYCLES) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `does not re-page or start new cycles while the ACL link is up`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        aclFlow.value = setOf(ADDRESS)
        val pager = createPager()

        val result = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)

        result.await() shouldBe AclPager.Result.NOT_CONNECTED
        verify(exactly = 0) { socketFactory.createSocket(any(), any()) }
        verify(exactly = 1) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `stops re-paging once the UUID broadcast window has passed`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        val pager = createPager()

        val result = async { pager.page(device) }
        // Just past the first broadcast: still one cycle, re-paging has stopped for it.
        advanceTimeBy(AclPager.UUID_INTENT_DELAY_MS + AclPager.UUID_INTENT_SLACK_MS + AclPager.POLL_MS * 2)
        verify(exactly = 1) { rawDevice.fetchUuidsWithSdp() }

        // Missed broadcast with the link down starts the next cycle.
        advanceTimeBy(AclPager.CYCLE_RETRY_AFTER_MS)
        verify(exactly = 2) { rawDevice.fetchUuidsWithSdp() }

        advanceTimeBy(AclPager.WINDOW_MS)
        result.await() shouldBe AclPager.Result.NOT_CONNECTED
        verify(exactly = AclPager.MAX_CYCLES) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `second page within the cooldown window is skipped`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns false
        val pager = createPager()

        val first = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)
        first.await() shouldBe AclPager.Result.UNAVAILABLE
        pager.page(device) shouldBe AclPager.Result.COOLDOWN

        verify(exactly = AclPager.MAX_CYCLES) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `page runs again once the cooldown has elapsed`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns false
        val pager = createPager()

        val first = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)
        first.await() shouldBe AclPager.Result.UNAVAILABLE
        advanceTimeBy(AclPager.COOLDOWN_MS + 1)
        val second = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)
        second.await() shouldBe AclPager.Result.UNAVAILABLE

        verify(exactly = 2 * AclPager.MAX_CYCLES) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `a page attempt while another is in flight is BUSY and does not count as an attempt`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        val pager = createPager()

        val first = async { pager.page(device) }
        runCurrent()
        advanceTimeBy(AclPager.POLL_MS)

        pager.page(device) shouldBe AclPager.Result.BUSY

        connectedFlow.value = listOf(device)
        advanceTimeBy(AclPager.POLL_MS * 2)
        first.await() shouldBe AclPager.Result.CONNECTED

        // Lock was released; only the cooldown stands in the way now.
        pager.page(device) shouldBe AclPager.Result.COOLDOWN
        advanceTimeBy(AclPager.COOLDOWN_MS + 1)
        connectedFlow.value = emptyList()
        every { rawDevice.fetchUuidsWithSdp() } returns false
        val third = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)
        third.await() shouldBe AclPager.Result.UNAVAILABLE

        // One cycle for the page that connected, a full set for the one that did not.
        verify(exactly = 1 + AclPager.MAX_CYCLES) { rawDevice.fetchUuidsWithSdp() }
    }

    @Test
    fun `cancelling an in-flight page releases the lock`() = runTest {
        every { rawDevice.fetchUuidsWithSdp() } returns true
        val pager = createPager()

        val job = launch { pager.page(device) }
        runCurrent()
        advanceTimeBy(AclPager.POLL_MS)
        job.cancel()
        runCurrent()

        advanceTimeBy(AclPager.COOLDOWN_MS + 1)
        every { rawDevice.fetchUuidsWithSdp() } returns false
        val second = async { pager.page(device) }
        advanceTimeBy(AclPager.WINDOW_MS + AclPager.POLL_MS * 2)
        second.await() shouldBe AclPager.Result.UNAVAILABLE
    }

    companion object {
        private const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
