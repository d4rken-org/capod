package eu.darken.capod.monitor.core

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
    @Test
    fun `addressed profiles use Automatic regardless of order or auto connect setting`() = runTest {
        val profiles = MutableStateFlow<List<DeviceProfile>>(emptyList())
        val repo = mockk<DeviceProfilesRepo> { every { this@mockk.profiles } returns profiles }
        val resolver = MonitorModeResolver(repo)
        resolver.effectiveMode.first() shouldBe MonitorMode.MANUAL
        for (address in listOf(null, "", "   ")) {
            profiles.value = listOf(AppleDeviceProfile(label = "Unpaired", address = address))
            resolver.effectiveMode.first() shouldBe MonitorMode.MANUAL
        }
        for (enabled in listOf(false, true)) {
            val paired = AppleDeviceProfile(label = "Pods", address = "test-device", autoConnect = enabled)
            profiles.value = listOf(AppleDeviceProfile(label = "Unpaired"), paired)
            resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
            profiles.value = profiles.value.reversed()
            resolver.effectiveMode.first() shouldBe MonitorMode.AUTOMATIC
        }
    }
}
