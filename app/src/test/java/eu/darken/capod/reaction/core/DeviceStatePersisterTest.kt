package eu.darken.capod.reaction.core

import eu.darken.capod.monitor.core.CachedDeviceState
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.DeviceStateCache
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.profiles.core.AppleDeviceProfile
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceStatePersisterTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var deviceStateCache: DeviceStateCache
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var cachedStatesFlow: MutableStateFlow<Map<String, CachedDeviceState>>

    private val testProfile = AppleDeviceProfile(
        id = "test-profile",
        label = "Test AirPods",
        model = PodModel.AIRPODS_PRO3,
        address = "AA:BB:CC:DD:EE:FF",
    )

    @BeforeEach
    fun setup() {
        devicesFlow = MutableStateFlow(emptyList())
        cachedStatesFlow = MutableStateFlow(emptyMap())

        deviceMonitor = mockk {
            every { devices } returns devicesFlow
        }

        deviceStateCache = mockk(relaxed = true) {
            every { cachedStates } returns cachedStatesFlow
        }
    }

    private fun createPersister() = DeviceStatePersister(
        deviceMonitor = deviceMonitor,
        deviceStateCache = deviceStateCache,
    )

    private fun createLiveDevice(
        leftBattery: Float? = 0.8f,
        rightBattery: Float? = 0.7f,
        caseBattery: Float? = 0.5f,
    ): PodDevice {
        val bleMeta = ApplePods.AppleMeta(profile = testProfile)
        val blePod: DualApplePods = mockk(relaxed = true) {
            every { meta } returns bleMeta
            every { batteryLeftPodPercent } returns leftBattery
            every { batteryRightPodPercent } returns rightBattery
            every { batteryCasePercent } returns caseBattery
            every { isLeftPodCharging } returns false
            every { isRightPodCharging } returns false
            every { isCaseCharging } returns false
            every { model } returns PodModel.AIRPODS_PRO3
            every { address } returns "5A:3B:1C:2D:4E:6F"
            every { seenLastAt } returns Instant.now()
        }
        return PodDevice(profileId = "test-profile", ble = blePod, aap = null, cached = null)
    }

    @Nested
    inner class Persistence {

        @Test
        fun `persists state for live device with battery`() = runTest(testDispatcher) {
            val persister = createPersister()

            val job = launch { persister.monitor().toList() }
            devicesFlow.value = listOf(createLiveDevice())
            advanceUntilIdle()

            coVerify(exactly = 1) { deviceStateCache.save("test-profile", any()) }

            job.cancel()
        }

        @Test
        fun `skips device with all null live batteries`() = runTest(testDispatcher) {
            val persister = createPersister()

            val job = launch { persister.monitor().toList() }
            devicesFlow.value = listOf(createLiveDevice(leftBattery = null, rightBattery = null, caseBattery = null))
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceStateCache.save(any(), any()) }

            job.cancel()
        }

        @Test
        fun `skips cached-only devices`() = runTest(testDispatcher) {
            val persister = createPersister()
            val cachedOnlyDevice = PodDevice(
                profileId = "test-profile", ble = null,
                aap = null,
                cached = CachedDeviceState(
                    profileId = "test-profile",
                    model = PodModel.AIRPODS_PRO3,
                    left = CachedDeviceState.CachedBatterySlot(0.5f, Instant.now()),
                    lastSeenAt = Instant.now(),
                ),
            )

            val job = launch { persister.monitor().toList() }
            devicesFlow.value = listOf(cachedOnlyDevice)
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceStateCache.save(any(), any()) }

            job.cancel()
        }

        @Test
        fun `skips write when values unchanged`() = runTest(testDispatcher) {
            val persister = createPersister()

            val existingCached = CachedDeviceState(
                profileId = "test-profile",
                model = PodModel.AIRPODS_PRO3,
                left = CachedDeviceState.CachedBatterySlot(0.8f, Instant.now()),
                right = CachedDeviceState.CachedBatterySlot(0.7f, Instant.now()),
                case = CachedDeviceState.CachedBatterySlot(0.5f, Instant.now()),
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = false,
                isHeadsetCharging = false,
                lastSeenAt = Instant.now(),
            )
            cachedStatesFlow.value = mapOf("test-profile" to existingCached)

            val job = launch { persister.monitor().toList() }
            devicesFlow.value = listOf(createLiveDevice())
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceStateCache.save(any(), any()) }

            job.cancel()
        }
    }
}
