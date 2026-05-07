package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MonitorModeResolverTest : BaseTest() {

    private val profilesFlow = MutableStateFlow<List<DeviceProfile>>(emptyList())
    private val nudgeFlow = MutableStateFlow(NudgeAvailability.UNKNOWN)

    private val profilesRepo = mockk<DeviceProfilesRepo>().also {
        every { it.profiles } returns profilesFlow
    }
    private val nudgeStore = mockk<NudgeCapabilityStore>().also {
        every { it.availability } returns nudgeFlow
    }

    private val resolver = MonitorModeResolver(profilesRepo, nudgeStore)

    private fun profile(
        id: String = "p",
        address: String? = "AA:BB:CC:DD:EE:FF",
        autoConnect: Boolean = false,
    ): AppleDeviceProfile = AppleDeviceProfile(
        id = id,
        label = id,
        address = address,
        autoConnect = autoConnect,
    )

    @Test
    fun `case 1 - no profiles - MANUAL`() = runTest {
        profilesFlow.value = emptyList()

        resolver.effectiveMode.first() shouldBe MonitorMode.MANUAL
    }

    @Test
    fun `case 2 - paired profile, autoConnect off - AUTOMATIC`() = runTest {
        profilesFlow.value = listOf(profile(autoConnect = false))

        resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `case 3 - paired profile, autoConnect on, nudge AVAILABLE - ALWAYS`() = runTest {
        profilesFlow.value = listOf(profile(autoConnect = true))
        nudgeFlow.value = NudgeAvailability.AVAILABLE

        resolver.effectiveMode.first() shouldBe MonitorMode.ALWAYS
    }

    @Test
    fun `case 3 - paired profile, autoConnect on, nudge UNKNOWN - ALWAYS (assume available)`() = runTest {
        profilesFlow.value = listOf(profile(autoConnect = true))
        nudgeFlow.value = NudgeAvailability.UNKNOWN

        resolver.effectiveMode.first() shouldBe MonitorMode.ALWAYS
    }

    @Test
    fun `case 3b - paired profile, autoConnect on, nudge BROKEN - AUTOMATIC`() = runTest {
        profilesFlow.value = listOf(profile(autoConnect = true))
        nudgeFlow.value = NudgeAvailability.BROKEN

        resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `case 4 - profile without paired address - MANUAL`() = runTest {
        profilesFlow.value = listOf(profile(address = null, autoConnect = true))

        resolver.effectiveMode.first() shouldBe MonitorMode.MANUAL
    }

    @Test
    fun `multi-profile primary-only - primary case 2 + secondary case 3 - AUTOMATIC`() = runTest {
        profilesFlow.value = listOf(
            profile(id = "primary", autoConnect = false),
            profile(id = "secondary", autoConnect = true),
        )
        nudgeFlow.value = NudgeAvailability.AVAILABLE

        resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `multi-profile primary-only - primary case 4 + secondary case 2 - MANUAL`() = runTest {
        profilesFlow.value = listOf(
            profile(id = "primary", address = null),
            profile(id = "secondary", address = "AA:BB:CC:DD:EE:FF"),
        )

        resolver.effectiveMode.first() shouldBe MonitorMode.MANUAL
    }

    @Test
    fun `list reorder makes the secondary primary - mode flips`() = runTest {
        val a = profile(id = "a", autoConnect = true)
        val b = profile(id = "b", autoConnect = false)
        nudgeFlow.value = NudgeAvailability.AVAILABLE

        profilesFlow.value = listOf(a, b)
        resolver.effectiveMode.first() shouldBe MonitorMode.ALWAYS

        profilesFlow.value = listOf(b, a)
        resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `nudge availability flip from UNKNOWN to BROKEN drops case 3 to AUTOMATIC`() = runTest {
        profilesFlow.value = listOf(profile(autoConnect = true))
        nudgeFlow.value = NudgeAvailability.UNKNOWN

        resolver.effectiveMode.first() shouldBe MonitorMode.ALWAYS

        nudgeFlow.value = NudgeAvailability.BROKEN
        resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
    }
}
