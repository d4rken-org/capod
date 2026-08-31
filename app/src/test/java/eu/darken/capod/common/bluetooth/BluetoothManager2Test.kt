package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import java.time.Duration

class BluetoothManager2Test : BaseTest() {

    private val deviceA = mockk<BluetoothDevice>().apply {
        every { address } returns ADDRESS_A
        every { name } returns "Pods A"
    }
    private val deviceB = mockk<BluetoothDevice>().apply {
        every { address } returns ADDRESS_B
        every { name } returns "Pods B"
    }
    private val btAdapter = mockk<BluetoothAdapter>().apply {
        every { bondedDevices } returns setOf(deviceA, deviceB)
    }
    private val btManager = mockk<BluetoothManager>().apply {
        every { adapter } returns btAdapter
    }
    private val timeSource = TestTimeSource()

    // Unconfined, so the appScope.launch bodies of the mark* methods complete in place.
    private val appScope = CoroutineScope(UnconfinedTestDispatcher())

    @AfterEach
    fun teardown() {
        appScope.cancel()
    }

    private fun create() = BluetoothManager2(
        appScope = appScope,
        dispatcherProvider = TestDispatcherProvider(),
        context = mockk<Context>(),
        manager = btManager,
        timeSource = timeSource,
    )

    private suspend fun BluetoothManager2.bonded(address: BluetoothAddress) =
        bondedDevices().first().single { it.address == address }

    /**
     * A bonded device is not necessarily a connected one, so querying bonded devices must not put a
     * timestamp into the cache that [BluetoothManager2.connectedDevices] later reads as a connect
     * time.
     */
    @Test
    fun `querying bonded devices does not cache a timestamp`() = runTest {
        val manager = create()

        manager.bonded(ADDRESS_A).seenFirstAt shouldBe timeSource.now()

        timeSource.advanceBy(Duration.ofSeconds(60))

        manager.bonded(ADDRESS_A).seenFirstAt shouldBe timeSource.now()
    }

    /**
     * The ACL broadcast arrives whether or not anything is collecting the connected-devices flow,
     * so the stamp it leaves has to survive until the device disconnects.
     */
    @Test
    fun `an ACL connect stamp survives the passage of time`() = runTest {
        val manager = create()
        val connectedAt = timeSource.now()

        manager.markDeviceConnected(ADDRESS_A)

        timeSource.advanceBy(Duration.ofSeconds(60))

        manager.bonded(ADDRESS_A).seenFirstAt shouldBe connectedAt
    }

    @Test
    fun `a repeated ACL connect keeps the first stamp`() = runTest {
        val manager = create()
        val connectedAt = timeSource.now()

        manager.markDeviceConnected(ADDRESS_A)

        timeSource.advanceBy(Duration.ofSeconds(60))
        manager.markDeviceConnected(ADDRESS_A)

        manager.bonded(ADDRESS_A).seenFirstAt shouldBe connectedAt
    }

    @Test
    fun `an ACL disconnect drops the stamp`() = runTest {
        val manager = create()

        manager.markDeviceConnected(ADDRESS_A)

        timeSource.advanceBy(Duration.ofSeconds(60))
        manager.markDeviceDisconnected(ADDRESS_A)

        manager.bonded(ADDRESS_A).seenFirstAt shouldBe timeSource.now()
    }

    companion object {
        private const val ADDRESS_A = "AA:BB:CC:DD:EE:F1"
        private const val ADDRESS_B = "AA:BB:CC:DD:EE:F2"
    }
}
