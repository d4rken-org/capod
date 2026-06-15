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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
import kotlin.time.Duration.Companion.seconds

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
        fun `handshake timeout disconnects a silent session`() = runBlocking {
            // Socket connects and the handshake is sent, but the peer never replies: read() blocks
            // until the socket is closed. The handshake watchdog must time out and tear the session
            // down. This exercises real dispatchers + a real socket close, so it runs on REAL time
            // with generous bounds — mixing runTest's virtual time with Dispatchers.IO made it flaky.
            val readBlocked = CountDownLatch(1)
            val closeCalls = AtomicInteger(0)
            val blockingInput = object : InputStream() {
                // Block well past the test's own wait so the watchdog (not a read self-timeout) is
                // always what ends the read.
                override fun read(): Int {
                    readBlocked.await(30, TimeUnit.SECONDS)
                    return -1
                }

                override fun read(b: ByteArray): Int {
                    readBlocked.await(30, TimeUnit.SECONDS)
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
                connectTimeout = 1.seconds,
                handshakeTimeout = 200.milliseconds,
            )

            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                connection.connect(scope)

                // The 200ms watchdog tears the silent session down: disconnect() resets the engine to
                // DISCONNECTED and THEN closes the socket (the close mock increments before counting
                // the latch down). Awaiting that latch is the unambiguous "watchdog fired" signal and
                // sidesteps any reset-vs-close ordering race. Generous real-time bound for loaded CI.
                readBlocked.await(15, TimeUnit.SECONDS) shouldBe true
                connection.state.value.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
                // close() may run once (watchdog) or twice (watchdog + read-loop finally race on
                // cleanupSocket before socket is nulled) — both are correct, so assert "at least once".
                (closeCalls.get() >= 1) shouldBe true
            } finally {
                readBlocked.countDown()
                scope.cancel()
            }
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
