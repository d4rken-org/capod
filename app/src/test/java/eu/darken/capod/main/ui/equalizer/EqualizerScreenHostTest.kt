package eu.darken.capod.main.ui.equalizer

import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import testhelpers.coroutine.TestDispatcherProvider

/**
 * The screen is useless without an AAP session, so it pops itself when one drops. The latch exists
 * because "no session yet" and "session lost" look identical in the state — without it the screen
 * would pop while it is still loading, before a session was ever seen.
 */
class EqualizerScreenHostTest : BaseComposeRobolectricTest() {

    private val profileId = "AA:BB:CC:DD:EE:FF"
    private val devicesFlow = MutableStateFlow<List<PodDevice>>(emptyList())
    private val navEvents = mutableListOf<NavEvent>()
    private val collectorScope = CoroutineScope(Dispatchers.Unconfined + Job())

    private lateinit var vm: EqualizerViewModel

    private fun device(aapConnected: Boolean) = PodDevice(
        profileId = profileId,
        label = "Test AirPods",
        ble = MockPodDataProvider.airPodsProWithKeys(),
        aap = if (aapConnected) AapPodState(connectionState = AapPodState.ConnectionState.READY) else null,
        profileAddress = profileId,
    )

    @Before
    fun setup() {
        val deviceMonitor = mockk<DeviceMonitor> {
            every { devices } returns devicesFlow
            coEvery { getDeviceForProfile(profileId) } answers { devicesFlow.value.firstOrNull() }
        }
        val upgradeRepo = mockk<UpgradeRepo> {
            every { upgradeInfo } returns MutableStateFlow(mockk(relaxed = true))
        }
        vm = EqualizerViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            deviceMonitor = deviceMonitor,
            aapManager = mockk<AapConnectionManager>(relaxed = true),
            upgradeRepo = upgradeRepo,
        )
        collectorScope.launch { vm.navEvents.collect { navEvents.add(it) } }
    }

    @After
    fun teardown() {
        collectorScope.cancel()
        vm.vmScope.cancel()
    }

    private fun setContent() {
        composeRule.setContent {
            PreviewWrapper {
                EqualizerScreenHost(profileId = profileId, vm = vm)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a device that has not connected yet does not navigate up`() {
        devicesFlow.value = listOf(device(aapConnected = false))

        setContent()

        navEvents.shouldBeEmpty()
    }

    @Test
    fun `losing the AAP session navigates up`() {
        devicesFlow.value = listOf(device(aapConnected = true))
        setContent()
        navEvents.shouldBeEmpty()

        devicesFlow.value = listOf(device(aapConnected = false))
        composeRule.waitForIdle()

        navEvents shouldContain NavEvent.Up
    }
}
