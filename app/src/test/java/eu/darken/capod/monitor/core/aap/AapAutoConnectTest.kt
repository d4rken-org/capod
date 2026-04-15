package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

class AapAutoConnectTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var aapManager: AapConnectionManager
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var bluetoothManager: BluetoothManager2
    private lateinit var blePodMonitor: BlePodMonitor

    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var connectedDevicesFlow: MutableStateFlow<List<BluetoothDevice2>>
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
        connectedDevicesFlow = MutableStateFlow(emptyList())
        disconnectEventsFlow = MutableSharedFlow(extraBufferCapacity = 16)
        allStatesFlow = MutableStateFlow(emptyMap())

        aapManager = mockk(relaxed = true) {
            every { allStates } returns allStatesFlow
            every { disconnectEvents } returns disconnectEventsFlow
        }

        profilesRepo = mockk(relaxUnitFun = true) {
            every { profiles } returns profilesFlow
        }

        bluetoothManager = mockk {
            every { bondedDevices() } returns flowOf(setOf(testBondedDevice))
            every { connectedDevices } returns connectedDevicesFlow
        }

        val bleMeta = object : BlePodSnapshot.Meta {
            override val profile = testProfile
        }
        blePodMonitor = mockk {
            every { devices } returns flowOf(
                listOf(mockk<BlePodSnapshot>(relaxed = true) {
                    every { address } returns "5A:3B:1C:2D:4E:6F"
                    every { meta } returns bleMeta
                })
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
        fun `connects when profiled device is classically connected`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `skips profiles not classically connected`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            // No classic BT connection
            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 0) { aapManager.connect(any(), any(), any()) }

            job.cancel()
        }

        @Test
        fun `skips profiles without address`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            val noAddressProfile = AppleDeviceProfile(label = "No Address", model = PodModel.AIRPODS_PRO3)

            connectedDevicesFlow.value = listOf(testBondedDevice)
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

            connectedDevicesFlow.value = listOf(testBondedDevice)
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

            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 0) { aapManager.connect(any(), any(), any()) }

            job.cancel()
        }

        @Test
        fun `connects when classic Bluetooth connects after service start`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()

            // Profiles already set, but no classic BT connection yet
            profilesFlow.value = listOf(testProfile)
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            // Should NOT connect — device not classically connected
            coVerify(exactly = 0) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            // Simulate classic BT connecting later
            connectedDevicesFlow.value = listOf(testBondedDevice)
            advanceUntilIdle()

            // Now should attempt connect
            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `does not skip disconnected devices`() = runTest(testDispatcher) {
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(connectionState = AapPodState.ConnectionState.DISCONNECTED)
            )

            val autoConnect = createAutoConnect()

            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }
            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }
    }

    @Nested
    inner class InitialConnectRetry {

        @Test
        fun `retries on initial connect failure then stops`() = runTest(testDispatcher) {
            coEvery { aapManager.connect(any(), any(), any()) } throws RuntimeException("ACL connection failed")

            val autoConnect = createAutoConnect()
            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }

            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            // 1 initial + 7 retries = 8 total
            coVerify(exactly = 8) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `succeeds on second retry`() = runTest(testDispatcher) {
            var callCount = 0
            coEvery { aapManager.connect(any(), any(), any()) } coAnswers {
                callCount++
                if (callCount <= 2) throw RuntimeException("ACL connection failed")
            }

            val autoConnect = createAutoConnect()
            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }

            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            // 1 initial fail + 1 retry fail + 1 retry success = 3
            coVerify(exactly = 3) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `stops retry when already reconnected by another path`() = runTest(testDispatcher) {
            var callCount = 0
            coEvery { aapManager.connect(any(), any(), any()) } coAnswers {
                callCount++
                if (callCount == 1) {
                    // Simulate another path reconnecting during the delay
                    allStatesFlow.value = mapOf(
                        testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
                    )
                    throw RuntimeException("ACL connection failed")
                }
            }

            val autoConnect = createAutoConnect()
            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }

            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            // 1 initial attempt, retries bail out because allStates shows READY
            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `stops retry when classic BT disconnects`() = runTest(testDispatcher) {
            var callCount = 0
            coEvery { aapManager.connect(any(), any(), any()) } coAnswers {
                callCount++
                if (callCount == 1) {
                    // Simulate classic BT disconnecting during the retry delay
                    connectedDevicesFlow.value = emptyList()
                    throw RuntimeException("ACL connection failed")
                }
            }

            val autoConnect = createAutoConnect()
            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }

            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            // 1 initial attempt, retries bail out because classic BT disconnected
            coVerify(exactly = 1) { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `does not retry when initial connect succeeds`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            connectedDevicesFlow.value = listOf(testBondedDevice)
            val job = launch { autoConnect.monitor().toList() }

            profilesFlow.value = listOf(testProfile)
            advanceUntilIdle()

            // Only 1 call, no retries needed
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
            connectedDevicesFlow.value = listOf(testBondedDevice)
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
        fun `reconnect stops when device no longer classically connected`() = runTest(testDispatcher) {
            val autoConnect = createAutoConnect()
            val job = setupForReconnect(autoConnect)
            advanceUntilIdle()

            // Remove classic BT connection, then disconnect
            connectedDevicesFlow.value = emptyList()
            allStatesFlow.value = emptyMap()
            disconnectEventsFlow.tryEmit(testAddress)
            advanceUntilIdle()

            // Reconnect should not call connect since not classically connected
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

    @Nested
    inner class ModelCorrection {

        private val testDeviceInfo = AapDeviceInfo(
            name = "AirPods Pro 3",
            modelNumber = "A3064",
            manufacturer = "Apple Inc.",
            serialNumber = "XXXX",
            firmwareVersion = "1.0",
        )

        @Test
        fun `corrects model when deviceInfo reports different model`() = runTest(testDispatcher) {
            val wrongModelProfile = AppleDeviceProfile(
                label = "Test AirPods",
                model = PodModel.UNKNOWN,
                address = testAddress,
            )
            profilesFlow.value = listOf(wrongModelProfile)
            connectedDevicesFlow.value = listOf(testBondedDevice)

            var capturedProfile: AppleDeviceProfile? = null
            coEvery { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) } coAnswers {
                capturedProfile = firstArg()
            }

            val autoConnect = createAutoConnect()
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            // Simulate AAP connection with device info reporting AirPods Pro 3
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(
                    connectionState = AapPodState.ConnectionState.READY,
                    deviceInfo = testDeviceInfo,
                )
            )
            advanceUntilIdle()

            capturedProfile!!.model shouldBe PodModel.AIRPODS_PRO3
            coVerify(exactly = 1) { aapManager.disconnect(testAddress) }
            coVerify { aapManager.connect(testAddress, any(), PodModel.AIRPODS_PRO3) }

            job.cancel()
        }

        @Test
        fun `does not correct when model already matches`() = runTest(testDispatcher) {
            // Profile already has correct model
            profilesFlow.value = listOf(testProfile) // PodModel.AIRPODS_PRO3
            connectedDevicesFlow.value = listOf(testBondedDevice)

            val autoConnect = createAutoConnect()
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            allStatesFlow.value = mapOf(
                testAddress to AapPodState(
                    connectionState = AapPodState.ConnectionState.READY,
                    deviceInfo = testDeviceInfo, // A3064 = AIRPODS_PRO3
                )
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) }
            coVerify(exactly = 0) { aapManager.disconnect(testAddress) }

            job.cancel()
        }

        @Test
        fun `does not correct when modelNumber is unrecognized`() = runTest(testDispatcher) {
            profilesFlow.value = listOf(testProfile)
            connectedDevicesFlow.value = listOf(testBondedDevice)

            val autoConnect = createAutoConnect()
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            allStatesFlow.value = mapOf(
                testAddress to AapPodState(
                    connectionState = AapPodState.ConnectionState.READY,
                    deviceInfo = testDeviceInfo.copy(modelNumber = "ZZZZ"),
                )
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) }

            job.cancel()
        }

        @Test
        fun `correction not re-triggered on subsequent state emissions`() = runTest(testDispatcher) {
            val wrongModelProfile = AppleDeviceProfile(
                label = "Test AirPods",
                model = PodModel.UNKNOWN,
                address = testAddress,
            )
            profilesFlow.value = listOf(wrongModelProfile)
            connectedDevicesFlow.value = listOf(testBondedDevice)

            val autoConnect = createAutoConnect()
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            val readyState = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                deviceInfo = testDeviceInfo,
            )

            // First emission with deviceInfo
            allStatesFlow.value = mapOf(testAddress to readyState)
            advanceUntilIdle()

            // Second emission — same modelNumber but different state (simulates battery/settings churn)
            // StateFlow needs a structurally different value to emit; distinctUntilChanged on
            // the modelNumber sub-map then suppresses re-processing
            allStatesFlow.value = mapOf(testAddress to readyState.copy(lastMessageAt = java.time.Instant.now()))
            advanceUntilIdle()

            coVerify(exactly = 1) { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) }

            job.cancel()
        }

        @Test
        fun `clears processed state on disconnect for future reconnects`() = runTest(testDispatcher) {
            val wrongModelProfile = AppleDeviceProfile(
                label = "Test AirPods",
                model = PodModel.UNKNOWN,
                address = testAddress,
            )
            profilesFlow.value = listOf(wrongModelProfile)
            connectedDevicesFlow.value = listOf(testBondedDevice)

            val autoConnect = createAutoConnect()
            val job = launch { autoConnect.monitor().toList() }
            advanceUntilIdle()

            // First connection with deviceInfo
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(
                    connectionState = AapPodState.ConnectionState.READY,
                    deviceInfo = testDeviceInfo,
                )
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) }

            // Simulate disconnect (address disappears)
            allStatesFlow.value = emptyMap()
            advanceUntilIdle()

            // Second connection — profile is now corrected, so no second updateProfile
            profilesFlow.value = listOf(wrongModelProfile.copy(model = PodModel.AIRPODS_PRO3))
            allStatesFlow.value = mapOf(
                testAddress to AapPodState(
                    connectionState = AapPodState.ConnectionState.READY,
                    deviceInfo = testDeviceInfo,
                )
            )
            advanceUntilIdle()

            // Still only 1 updateProfile — model now matches
            coVerify(exactly = 1) { profilesRepo.updateProfile(ofType<AppleDeviceProfile>()) }

            job.cancel()
        }
    }
}
