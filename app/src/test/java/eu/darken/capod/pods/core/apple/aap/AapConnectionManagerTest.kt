package eu.darken.capod.pods.core.apple.aap

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.engine.AapConnection
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.TimeoutCancellationException
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
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

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
        fun `connect timeout closes in-flight socket and allows reconnect`() = testScope.runTest {
            val closeCalled = CountDownLatch(1)
            val closeCalls = AtomicInteger(0)
            val blockingSocket = mockk<BluetoothSocket>(relaxed = true) {
                every { connect() } answers {
                    closeCalled.await(1, TimeUnit.SECONDS)
                    throw IOException("closed")
                }
                every { close() } answers {
                    closeCalls.incrementAndGet()
                    closeCalled.countDown()
                }
            }
            val reconnectSocket = mockk<BluetoothSocket>(relaxed = true) {
                every { outputStream } returns ByteArrayOutputStream()
                every { inputStream } returns ByteArrayInputStream(byteArrayOf())
            }
            every { socketFactory.createSocket(any(), any()) } returnsMany listOf(blockingSocket, reconnectSocket)
            val connection = AapConnection(
                device = testDevice,
                profile = AapDeviceProfile.forModel(PodModel.AIRPODS_PRO3),
                socketFactory = socketFactory,
                timeSource = timeSource,
                connectTimeout = 50.milliseconds,
            )

            shouldThrow<TimeoutCancellationException> {
                connection.connect(testScope)
            }

            closeCalled.await(1, TimeUnit.SECONDS) shouldBe true
            closeCalls.get() shouldBe 1
            connection.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED

            connection.connect(testScope)
            advanceUntilIdle()
        }

        @Test
        fun `handshake timeout disconnects a silent session`() = testScope.runTest {
            // Socket connects and the handshake is sent, but the peer never replies: read() blocks
            // forever. The handshake watchdog must time out and tear the session down.
            val readBlocked = CountDownLatch(1)
            val closeCalls = AtomicInteger(0)
            val blockingInput = object : InputStream() {
                override fun read(): Int {
                    readBlocked.await(5, TimeUnit.SECONDS)
                    return -1
                }

                override fun read(b: ByteArray): Int {
                    readBlocked.await(5, TimeUnit.SECONDS)
                    return -1
                }
            }
            val silentSocket = mockk<BluetoothSocket>(relaxed = true) {
                every { connect() } just Runs
                every { outputStream } returns ByteArrayOutputStream()
                every { inputStream } returns blockingInput
                every { close() } answers {
                    closeCalls.incrementAndGet()
                    readBlocked.countDown()
                }
            }
            every { socketFactory.createSocket(any(), any()) } returns silentSocket
            val connection = AapConnection(
                device = testDevice,
                profile = AapDeviceProfile.forModel(PodModel.AIRPODS_PRO3),
                socketFactory = socketFactory,
                timeSource = timeSource,
                connectTimeout = 50.milliseconds,
                handshakeTimeout = 100.milliseconds,
            )

            connection.connect(testScope)
            // Fire the 100ms handshake watchdog.
            advanceUntilIdle()

            // disconnect() runs engine.reset() before closing the socket, so awaiting the close latch
            // guarantees the state has already flipped to DISCONNECTED.
            readBlocked.await(5, TimeUnit.SECONDS) shouldBe true
            connection.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
            // The socket must be closed (this unblocks the wedged read). It may be closed more than
            // once — the watchdog's disconnect() and the read loop's finally both run cleanupSocket()
            // and can race — so assert "at least once" rather than an exact, timing-dependent count.
            (closeCalls.get() >= 1) shouldBe true
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
