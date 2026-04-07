package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.monitor.core.aap.AapLifecycleManager
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.monitor.core.cache.CachedDeviceState
import eu.darken.capod.monitor.core.cache.DeviceStateCache
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceMonitorTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testAddress: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private val testProfile = AppleDeviceProfile(
        label = "Test AirPods",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = testAddress,
    )
    private val noAddressProfile = AppleDeviceProfile(
        label = "No Address",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = null,
    )

    private val testCachedState = CachedDeviceState(
        profileId = testProfile.id,
        model = PodModel.AIRPODS_PRO2_USBC,
        address = testAddress,
        lastSeenAt = Instant.parse("2026-04-05T17:52:09.182Z"),
    )

    private val testAapState = AapPodState(
        connectionState = AapPodState.ConnectionState.READY,
        lastMessageAt = Instant.parse("2026-04-05T17:52:47.881Z"),
    )

    private fun mockBlePodWithProfile(profile: DeviceProfile): BlePodSnapshot {
        val bleMeta = object : BlePodSnapshot.Meta {
            override val profile: DeviceProfile? = profile
        }
        return mockk(relaxed = true) {
            every { meta } returns bleMeta
        }
    }

    private fun createMonitor(
        ble: List<BlePodSnapshot> = emptyList(),
        aap: Map<BluetoothAddress, AapPodState> = emptyMap(),
        cache: Map<String, CachedDeviceState> = emptyMap(),
        profiles: List<DeviceProfile> = emptyList(),
        scope: CoroutineScope,
    ): DeviceMonitor {
        val blePodMonitor: BlePodMonitor = mockk {
            every { devices } returns MutableStateFlow(ble)
        }
        val aapManager: AapConnectionManager = mockk {
            every { allStates } returns MutableStateFlow(aap)
        }
        val deviceStateCache: DeviceStateCache = mockk(relaxed = true) {
            every { cachedStates } returns MutableStateFlow(cache)
        }
        val profilesRepo: DeviceProfilesRepo = mockk {
            every { this@mockk.profiles } returns MutableStateFlow(profiles)
        }
        val aapLifecycleManager: AapLifecycleManager = mockk(relaxed = true)

        return DeviceMonitor(
            appScope = scope,
            blePodMonitor = blePodMonitor,
            aapManager = aapManager,
            deviceStateCache = deviceStateCache,
            profilesRepo = profilesRepo,
            aapLifecycleManager = aapLifecycleManager,
        )
    }

    @Test
    fun `BLE present and AAP present - live device has AAP attached`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = listOf(mockBlePodWithProfile(testProfile)),
            aap = mapOf(testAddress to testAapState),
            cache = mapOf(testProfile.id to testCachedState),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        val device = devices.single()
        device.profileId shouldBe testProfile.id
        device.isAapConnected shouldBe true
        device.isAapReady shouldBe true
    }

    /**
     * Regression test for https://github.com/d4rken-org/capod/issues/483 — settings disappeared
     * when BLE went stale even though the AAP socket was still healthy.
     */
    @Test
    fun `BLE stale and cache present and AAP present - cached-only device still has AAP attached`() =
        runTest(testDispatcher) {
            val monitor = createMonitor(
                ble = emptyList(), // BLE evicted
                aap = mapOf(testAddress to testAapState),
                cache = mapOf(testProfile.id to testCachedState),
                profiles = listOf(testProfile),
                scope = backgroundScope,
            )

            val devices = monitor.devices.first()

            devices.size shouldBe 1
            val device = devices.single()
            device.profileId shouldBe testProfile.id
            device.address shouldBe testAddress
            device.isAapConnected shouldBe true
            device.isAapReady shouldBe true
        }

    @Test
    fun `BLE stale and cache present and AAP absent - cached-only device has AAP null`() =
        runTest(testDispatcher) {
            val monitor = createMonitor(
                ble = emptyList(),
                aap = emptyMap(), // AAP also gone
                cache = mapOf(testProfile.id to testCachedState),
                profiles = listOf(testProfile),
                scope = backgroundScope,
            )

            val devices = monitor.devices.first()

            devices.size shouldBe 1
            val device = devices.single()
            device.profileId shouldBe testProfile.id
            device.isAapConnected shouldBe false
        }

    @Test
    fun `profile without address never binds to AAP entries`() = runTest(testDispatcher) {
        val cachedForNoAddress = CachedDeviceState(
            profileId = noAddressProfile.id,
            model = PodModel.AIRPODS_PRO2_USBC,
            address = null,
            lastSeenAt = Instant.parse("2026-04-05T17:52:09.182Z"),
        )
        val monitor = createMonitor(
            ble = emptyList(),
            // AAP map contains an entry, but it's for a different address.
            aap = mapOf(testAddress to testAapState),
            cache = mapOf(noAddressProfile.id to cachedForNoAddress),
            profiles = listOf(noAddressProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        val device = devices.single()
        device.profileId shouldBe noAddressProfile.id
        device.isAapConnected shouldBe false
    }
}
