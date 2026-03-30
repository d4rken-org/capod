package eu.darken.capod.reaction.core.aap

import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.BlePodMonitor
import eu.darken.capod.pods.core.BlePodSnapshot
import eu.darken.capod.pods.core.PodModel
import eu.darken.capod.pods.core.apple.protocol.aap.AapConnectionManager

import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AapAutoConnectTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var aapManager: AapConnectionManager
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var bluetoothManager: BluetoothManager2
    private lateinit var blePodMonitor: BlePodMonitor

    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var disconnectEventsFlow: MutableSharedFlow<String>
    private lateinit var allStatesFlow: MutableStateFlow<Map<String, AapPodState>>

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testProfile = AppleDeviceProfile(
        label = "Test AirPods",
        model = PodModel.AIRPODS_PRO3,
        address = testAddress,
    )
    private val testBondedDevice: BluetoothDevice2 = mockk(relaxed = true) {
        every { address } returns testAddress
    }

    @BeforeEach
    fun setup() {
        profilesFlow = MutableStateFlow(emptyList())
        disconnectEventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        allStatesFlow = MutableStateFlow(emptyMap())

        aapManager = mockk(relaxed = true) {
            every { allStates } returns allStatesFlow
            every { disconnectEvents } returns disconnectEventsFlow
        }

        profilesRepo = mockk {
            every { profiles } returns profilesFlow
        }

        bluetoothManager = mockk {
            every { bondedDevices() } returns flowOf(setOf(testBondedDevice))
        }

        blePodMonitor = mockk {
            every { devices } returns flowOf(
                listOf(mockk<BlePodSnapshot>(relaxed = true) { every { address } returns testAddress })
            )
        }
    }

    private fun createAutoConnect() = AapAutoConnect(
        aapManager = aapManager,
        profilesRepo = profilesRepo,
        bluetoothManager = bluetoothManager,
        blePodMonitor = blePodMonitor,
    )

    @Nested
    inner class InitialConnect {

        @Test
        fun `connects when profiled device is bonded`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `skips profiles without address`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            val noAddressProfile = AppleDeviceProfile(label = "No Address", model = PodModel.AIRPODS_PRO3)

            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(noAddressProfile)
            advanceUntilIdle()

            coVerify(exactly = 0) { aapManager.connect(any(), any(), any()) }

            job.cancel()
        }

        @Test
        fun `skips profiles not in bonded devices`() = runTest(testDispatcher) {
            every { bluetoothManager.bondedDevices() } returns flowOf(emptySet())

            val autoConnect = createAutoConnect()

            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 0) { aapManager.connect(any(), any(), any()) }

            job.cancel()
        }

        @Test
        fun `skips already connected devices`() = runTest(testDispatcher) {
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
            )

            val autoConnect = createAutoConnect()

            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 0) { aapManager.connect(any(), any(), any()) }

            job.cancel()
        }

        @Test
        fun `does not skip disconnected devices`() = runTest(testDispatcher) {
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
            )

            val autoConnect = createAutoConnect()

            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }
    }

    @Nested
    inner class Reconnect {

        /**
         * For reconnect tests: set the device as "already connected" in allStates
         * so initialConnect() skips it, then clear allStates before emitting disconnect event.
         */
        private fun kotlinx.coroutines.test.TestScope.setupForReconnect(autoConnect: AapAutoConnect): kotlinx.coroutines.Job {
            // Pre-set as connected so initialConnect doesn't fire for this address
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
            )
            profilesFlow.value = listOf(testProfile)
            return launch { autoConnect.monitor().toList() }
        }

        @Test
        fun `reconnect stops when device no longer profiled`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            val job = setupForReconnect(autoConnect)
            advanceUntilIdle()

            // Clear profiles, then disconnect
            profilesFlow.value = emptyList()
            allStatesFlow.value = emptyMap()
            disconnectEventsFlow.tryEmit(testAddress)
            advanceUntilIdle()

            // connect should only have been called by the profile change trigger (which also finds no profiles)
            // The reconnect loop should not call connect since profile is gone
            coVerify(exactly = 0) { aapManager.connect(testAddress, any(), any()) }

            job.cancel()
        }

        @Test
        fun `reconnect stops when device no longer bonded`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            val job = setupForReconnect(autoConnect)
            advanceUntilIdle()

            // Remove bonded device, then disconnect
            every { bluetoothManager.bondedDevices() } returns flowOf(emptySet())
            allStatesFlow.value = emptyMap()
            disconnectEventsFlow.tryEmit(testAddress)
            advanceUntilIdle()

            // Reconnect should not call connect since not bonded
            coVerify(exactly = 0) { aapManager.connect(testAddress, any(), any()) }

            job.cancel()
        }

        @Test
        fun `reconnect stops when device no longer visible in BLE`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            val job = setupForReconnect(autoConnect)
            advanceUntilIdle()

            // Remove from BLE scans, then disconnect
            every { blePodMonitor.devices } returns flowOf(emptyList())
            allStatesFlow.value = emptyMap()
            disconnectEventsFlow.tryEmit(testAddress)
            advanceUntilIdle()

            // Reconnect should not call connect since not visible in BLE
            coVerify(exactly = 0) { aapManager.connect(testAddress, any(), any()) }

            job.cancel()
        }

        @Test
        fun `reconnect stops when device already reconnected`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            val job = setupForReconnect(autoConnect)
            advanceUntilIdle()

            // Keep as READY, emit disconnect event
            // allStatesFlow still shows READY → reconnect should skip
            disconnectEventsFlow.tryEmit(testAddress)
            advanceUntilIdle()

            // Should not attempt connect — already connected
            coVerify(exactly = 0) { aapManager.connect(testAddress, any(), any()) }

            job.cancel()
        }
    }
}
