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
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
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
    private val keyedProfile = AppleDeviceProfile(
        label = "Keyed AirPods",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = testAddress,
        identityKey = ByteArray(16) { 0x42 },
    )
    private val noKeyProfile = AppleDeviceProfile(
        label = "No Key AirPods",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = "11:22:33:44:55:66",
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
            every { this@mockk.model } returns profile.model
        }
    }

    private fun mockAnonymousBlePod(model: PodModel): BlePodSnapshot {
        val anonymousMeta = object : BlePodSnapshot.Meta {
            override val profile: DeviceProfile? = null
        }
        return mockk(relaxed = true) {
            every { meta } returns anonymousMeta
            every { this@mockk.model } returns model
        }
    }

    private fun mockAppleBlePod(
        profile: DeviceProfile,
        isIRKMatch: Boolean,
        signalQuality: Float = 0.5f,
    ): ApplePods {
        val appleMeta = ApplePods.AppleMeta(
            isIRKMatch = isIRKMatch,
            profile = profile as? AppleDeviceProfile,
        )
        return mockk(relaxed = true) {
            every { meta } returns appleMeta
            every { this@mockk.model } returns profile.model
            every { this@mockk.signalQuality } returns signalQuality
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
            // Relaxed mocks return a mocked child for unstubbed nullable returns,
            // so explicitly delegate load() to the test's cache map.
            coEvery { load(any()) } answers { cache[firstArg<String>()] }
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

    /**
     * Cold-start AAP-only synthesis: AAP socket is alive (CONNECTING) but no BLE
     * detection has happened yet and the cache hasn't been written. The merge must
     * still emit a PodDevice for the profile so DeviceSettingsScreen can render.
     */
    @Test
    fun `AAP-only synthesis CONNECTING - no BLE no cache`() = runTest(testDispatcher) {
        val connectingAap = AapPodState(
            connectionState = AapPodState.ConnectionState.CONNECTING,
            lastMessageAt = null,
        )
        val monitor = createMonitor(
            ble = emptyList(),
            aap = mapOf(testAddress to connectingAap),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        val device = devices.single()
        device.profileId shouldBe testProfile.id
        device.address shouldBe testProfile.address
        device.model shouldBe testProfile.model
        device.isAapConnected shouldBe true
        device.isAapReady shouldBe false
    }

    @Test
    fun `AAP-only synthesis READY - no BLE no cache`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = emptyList(),
            aap = mapOf(testAddress to testAapState),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        val device = devices.single()
        device.profileId shouldBe testProfile.id
        device.address shouldBe testProfile.address
        device.model shouldBe testProfile.model
        device.isAapConnected shouldBe true
        device.isAapReady shouldBe true
    }

    @Test
    fun `profile in repo with no BLE no cache no AAP - no device emitted`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = emptyList(),
            aap = emptyMap(),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 0
    }

    @Test
    fun `getDeviceForProfile AAP-only fallback - returns synthesized device`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = emptyList(),
            aap = mapOf(testAddress to testAapState),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val device = monitor.getDeviceForProfile(testProfile.id)

        device shouldNotBe null
        device!!.profileId shouldBe testProfile.id
        device.address shouldBe testProfile.address
        device.model shouldBe testProfile.model
        device.isAapConnected shouldBe true
    }

    @Test
    fun `getDeviceForProfile no BLE no cache no AAP - returns null`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = emptyList(),
            aap = emptyMap(),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val device = monitor.getDeviceForProfile(testProfile.id)

        device shouldBe null
    }

    /**
     * Stale-cache regression: after a model correction
     * (`AapAutoConnect.correctModelOnDeviceInfo`) the profile's model is updated, but the
     * cache still holds the old model from before correction. The PodDevice must reflect
     * the profile (current truth), not the cache (stale snapshot).
     */
    @Test
    fun `stale cache vs updated profile model - profile wins`() = runTest(testDispatcher) {
        val staleCache = CachedDeviceState(
            profileId = testProfile.id,
            model = PodModel.AIRPODS_PRO2,           // old model in cache
            address = testAddress,
            lastSeenAt = Instant.parse("2026-04-05T17:52:09.182Z"),
        )
        // testProfile.model = AIRPODS_PRO2_USBC (the correction)
        val monitor = createMonitor(
            ble = emptyList(),
            aap = emptyMap(),
            cache = mapOf(testProfile.id to staleCache),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        devices.single().model shouldBe PodModel.AIRPODS_PRO2_USBC
    }

    @Test
    fun `stale cache vs updated profile address - profile wins`() = runTest(testDispatcher) {
        val staleCache = CachedDeviceState(
            profileId = testProfile.id,
            model = PodModel.AIRPODS_PRO2_USBC,
            address = "00:00:00:00:00:00",            // old address in cache
            lastSeenAt = Instant.parse("2026-04-05T17:52:09.182Z"),
        )
        // testProfile.address = "AA:BB:CC:DD:EE:FF"
        val monitor = createMonitor(
            ble = emptyList(),
            aap = emptyMap(),
            cache = mapOf(testProfile.id to staleCache),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        devices.single().address shouldBe testAddress
    }

    /**
     * De-dupe: an anonymous BLE pod (no IRK match) of the same model as a synthesized
     * AAP-only profile is most likely the same physical device with broken/missing keys.
     * Hide the anonymous pod to avoid showing two cards.
     */
    @Test
    fun `de-dupe - anonymous BLE pod with matching model is hidden`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = listOf(mockAnonymousBlePod(PodModel.AIRPODS_PRO2_USBC)),
            aap = mapOf(testAddress to testAapState),
            cache = emptyMap(),
            profiles = listOf(testProfile),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 1
        val device = devices.single()
        device.profileId shouldBe testProfile.id
        device.isAapConnected shouldBe true
    }

    @Test
    fun `de-dupe - anonymous BLE pod with different model is kept`() = runTest(testDispatcher) {
        val monitor = createMonitor(
            ble = listOf(mockAnonymousBlePod(PodModel.AIRPODS_PRO)),   // different model
            aap = mapOf(testAddress to testAapState),
            cache = emptyMap(),
            profiles = listOf(testProfile),                              // model = PRO2_USBC
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 2
        // One anonymous BLE pod (no profile), one synthesized AAP-only profile
        devices.count { it.profileId == null } shouldBe 1
        devices.count { it.profileId == testProfile.id } shouldBe 1
    }

    @Test
    fun `profile dedup - two live pods with same IRK-backed profileId collapsed to IRK winner`() =
        runTest(testDispatcher) {
            val irkPod = mockAppleBlePod(keyedProfile, isIRKMatch = true, signalQuality = 0.6f)
            val strangerPod = mockAppleBlePod(keyedProfile, isIRKMatch = false, signalQuality = 0.8f)
            val monitor = createMonitor(
                ble = listOf(irkPod, strangerPod),
                profiles = listOf(keyedProfile),
                scope = backgroundScope,
            )

            val devices = monitor.devices.first()

            devices.size shouldBe 1
            devices.single().ble shouldBe irkPod
        }

    @Test
    fun `profile dedup - two IRK-matched pods for same profile keeps higher signal quality`() =
        runTest(testDispatcher) {
            val weakPod = mockAppleBlePod(keyedProfile, isIRKMatch = true, signalQuality = 0.3f)
            val strongPod = mockAppleBlePod(keyedProfile, isIRKMatch = true, signalQuality = 0.9f)
            val monitor = createMonitor(
                ble = listOf(weakPod, strongPod),
                profiles = listOf(keyedProfile),
                scope = backgroundScope,
            )

            val devices = monitor.devices.first()

            devices.size shouldBe 1
            devices.single().ble shouldBe strongPod
        }

    @Test
    fun `profile dedup - two pods with same no-IRK profileId are NOT collapsed`() =
        runTest(testDispatcher) {
            val pod1 = mockAppleBlePod(noKeyProfile, isIRKMatch = false, signalQuality = 0.6f)
            val pod2 = mockAppleBlePod(noKeyProfile, isIRKMatch = false, signalQuality = 0.8f)
            val monitor = createMonitor(
                ble = listOf(pod1, pod2),
                profiles = listOf(noKeyProfile),
                scope = backgroundScope,
            )

            val devices = monitor.devices.first()

            devices.size shouldBe 2
        }

    @Test
    fun `profile dedup - anonymous pods always pass through`() = runTest(testDispatcher) {
        val anon1 = mockAnonymousBlePod(PodModel.AIRPODS_PRO2_USBC)
        val anon2 = mockAnonymousBlePod(PodModel.AIRPODS_PRO2_USBC)
        val monitor = createMonitor(
            ble = listOf(anon1, anon2),
            profiles = emptyList(),
            scope = backgroundScope,
        )

        val devices = monitor.devices.first()

        devices.size shouldBe 2
    }
}
