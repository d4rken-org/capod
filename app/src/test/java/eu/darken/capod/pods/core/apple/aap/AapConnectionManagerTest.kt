package eu.darken.capod.pods.core.apple.aap

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class AapConnectionManagerTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var socketFactory: L2capSocketFactory
    private lateinit var manager: AapConnectionManager
    private val timeSource: TimeSource = TestTimeSource()

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testDevice: BluetoothDevice = mockk(relaxed = true) {
        every { address } returns testAddress
    }

    @BeforeEach
    fun setup() {
        socketFactory = mockk(relaxed = true)
        manager = AapConnectionManager(
            socketFactory = socketFactory,
            scope = testScope,
            timeSource = timeSource,
        )
    }

    @Nested
    inner class InitialState {
        @Test
        fun `allStates starts empty`() = testScope.runTest {
            manager.allStates.value.shouldBeEmpty()
        }
    }

    @Nested
    inner class SendCommand {
        @Test
        fun `sendCommand throws when not connected`() = testScope.runTest {
            shouldThrow<IllegalStateException> {
                manager.sendCommand(testAddress, AapCommand.SetAncMode(AapSetting.AncMode.Value.ON))
            }
        }
    }

    @Nested
    inner class Disconnect {
        @Test
        fun `disconnect on unknown address is no-op`() = testScope.runTest {
            manager.disconnect(testAddress)
            manager.allStates.value.shouldBeEmpty()
        }
    }

    @Nested
    inner class Connect {
        @Test
        fun `connect failure cleans up state`() = testScope.runTest {
            every { socketFactory.createSocket(any(), any()) } throws IOException("Connection refused")

            shouldThrow<IOException> {
                manager.connect(testAddress, testDevice, PodModel.AIRPODS_PRO3)
            }

            manager.allStates.value.shouldBeEmpty()
        }

        @Test
        fun `remote disconnect cleans up allStates`() = testScope.runTest {
            // Empty inputStream → readLoop gets -1 immediately → DISCONNECTED
            val socket = mockk<BluetoothSocket>(relaxed = true) {
                every { outputStream } returns ByteArrayOutputStream()
                every { inputStream } returns ByteArrayInputStream(byteArrayOf())
            }
            every { socketFactory.createSocket(any(), any()) } returns socket

            manager.connect(testAddress, testDevice, PodModel.AIRPODS_PRO3)
            advanceUntilIdle()

            // readLoop exited, collector cleaned up
            manager.allStates.value.containsKey(testAddress) shouldBe false
        }

        @Test
        fun `can reconnect after remote disconnect`() = testScope.runTest {
            val socket = mockk<BluetoothSocket>(relaxed = true) {
                every { outputStream } returns ByteArrayOutputStream()
                every { inputStream } returns ByteArrayInputStream(byteArrayOf())
            }
            every { socketFactory.createSocket(any(), any()) } returns socket

            manager.connect(testAddress, testDevice, PodModel.AIRPODS_PRO3)
            advanceUntilIdle()

            // Stale entry should be cleaned up — second connect should not throw "Already connected"
            manager.connect(testAddress, testDevice, PodModel.AIRPODS_PRO3)
            advanceUntilIdle()
        }
    }
}
