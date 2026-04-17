package eu.darken.capod.monitor.core.aap

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapLearnedSettingsPersisterTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testProfile = AppleDeviceProfile(
        label = "Test AirPods",
        model = PodModel.AIRPODS_PRO,
        address = testAddress,
    )

    private fun stateWithSettings(
        allowOffEnabled: Boolean? = null,
        cycleMask: Int? = null,
    ): AapPodState {
        val settings = buildMap<kotlin.reflect.KClass<out AapSetting>, AapSetting> {
            allowOffEnabled?.let { put(AapSetting.AllowOffOption::class, AapSetting.AllowOffOption(it)) }
            cycleMask?.let { put(AapSetting.ListeningModeCycle::class, AapSetting.ListeningModeCycle(it)) }
        }
        return AapPodState(settings = settings)
    }

    @Test
    fun `persists AllowOffOption value to matching profile`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(emptyMap())
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxUnitFun = true) {
            every { profiles } returns flowOf(listOf<eu.darken.capod.profiles.core.DeviceProfile>(testProfile))
        }
        val persister = AapLearnedSettingsPersister(aapManager, profilesRepo)

        val job = launch { persister.monitor().collect {} }

        allStates.value = mapOf(testAddress to stateWithSettings(allowOffEnabled = false))
        advanceUntilIdle()

        val transform = slot<(AppleDeviceProfile) -> AppleDeviceProfile>()
        coVerify { profilesRepo.updateAppleProfile(eq(testProfile.id), capture(transform)) }
        val updated = transform.captured(testProfile)
        assert(updated.learnedAllowOffEnabled == false) { "Expected learnedAllowOffEnabled=false" }

        job.cancel()
    }

    @Test
    fun `persists ListeningModeCycle mask to matching profile`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(emptyMap())
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxUnitFun = true) {
            every { profiles } returns flowOf(listOf<eu.darken.capod.profiles.core.DeviceProfile>(testProfile))
        }
        val persister = AapLearnedSettingsPersister(aapManager, profilesRepo)

        val job = launch { persister.monitor().collect {} }

        allStates.value = mapOf(testAddress to stateWithSettings(cycleMask = 0x0F))
        advanceUntilIdle()

        val transform = slot<(AppleDeviceProfile) -> AppleDeviceProfile>()
        coVerify { profilesRepo.updateAppleProfile(eq(testProfile.id), capture(transform)) }
        val updated = transform.captured(testProfile)
        assert(updated.lastRequestedListeningModeCycleMask == 0x0F) { "Expected lastRequestedListeningModeCycleMask=0x0F" }

        job.cancel()
    }

    @Test
    fun `does not write when settings match already-persisted values`() = runTest(UnconfinedTestDispatcher()) {
        val allStates = MutableStateFlow<Map<String, AapPodState>>(emptyMap())
        val aapManager = mockk<AapConnectionManager>(relaxed = true) {
            every { this@mockk.allStates } returns allStates
        }
        val profileWithStored = testProfile.copy(
            learnedAllowOffEnabled = true,
            lastRequestedListeningModeCycleMask = 0x0F,
        )
        val profilesRepo = mockk<DeviceProfilesRepo>(relaxUnitFun = true) {
            every { profiles } returns flowOf(listOf<eu.darken.capod.profiles.core.DeviceProfile>(profileWithStored))
        }
        val persister = AapLearnedSettingsPersister(aapManager, profilesRepo)

        val job = launch { persister.monitor().collect {} }

        allStates.value = mapOf(testAddress to stateWithSettings(allowOffEnabled = true, cycleMask = 0x0F))
        advanceUntilIdle()

        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(any(), any()) }

        job.cancel()
    }
}
