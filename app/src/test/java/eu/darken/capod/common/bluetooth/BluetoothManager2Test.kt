package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

    private fun TestScope.create() = BluetoothManager2(
        appScope = backgroundScope,
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

    companion object {
        private const val ADDRESS_A = "AA:BB:CC:DD:EE:F1"
        private const val ADDRESS_B = "AA:BB:CC:DD:EE:F2"
    }
}
