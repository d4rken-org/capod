package eu.darken.capod.main.ui.equalizer

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.livedata.InstantExecutorExtension

@ExtendWith(InstantExecutorExtension::class)
class EqualizerViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testProfileId: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private var vm: EqualizerViewModel? = null

    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var aapManager: AapConnectionManager
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>

    private fun makeDevice(customEq: AapSetting.CustomEq?) = mockk<PodDevice>(relaxed = true).also {
        every { it.profileId } returns testProfileId
        every { it.address } returns testProfileId
        every { it.isAapReady } returns true
        every { it.customEq } returns customEq
    }

    private fun setDevice(customEq: AapSetting.CustomEq?) {
        val device = makeDevice(customEq)
        coEvery { deviceMonitor.getDeviceForProfile(testProfileId) } returns device
        devicesFlow.value = listOf(device)
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        devicesFlow = MutableStateFlow(emptyList())
        deviceMonitor = mockk {
            every { devices } returns devicesFlow
        }
        setDevice(null)
        aapManager = mockk(relaxed = true)
        upgradeInfoFlow = MutableStateFlow(mockk<UpgradeRepo.Info>(relaxed = true).also {
            every { it.isPro } returns true
            // Hot flow + settled + no error: isProForUi resolves immediately.
            every { it.isSettled } returns true
            every { it.error } returns null
        })
        upgradeRepo = mockk {
            every { upgradeInfo } returns upgradeInfoFlow
        }
    }

    @AfterEach
    fun teardown() {
        vm?.vmScope?.cancel()
        vm = null
        Dispatchers.resetMain()
    }

    private fun runVmTest(testBody: suspend TestScope.() -> Unit) = runTest(testDispatcher) {
        try {
            testBody()
        } finally {
            vm?.vmScope?.cancel()
            vm = null
        }
    }

    private fun createViewModel() = EqualizerViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        deviceMonitor = deviceMonitor,
        aapManager = aapManager,
        upgradeRepo = upgradeRepo,
    ).also { vm = it }

    private suspend fun startedVm(): EqualizerViewModel = createViewModel().also {
        it.initialize(testProfileId)
        it.state.first()
    }

    private fun eq(enabled: Boolean, low: Int, mid: Int, high: Int) =
        AapSetting.CustomEq(enabled = enabled, low = low, mid = mid, high = high)

    private fun command(enabled: Boolean, low: Int, mid: Int, high: Int) =
        AapCommand.SetCustomEq(enabled = enabled, low = low, mid = mid, high = high)

    private fun goesToUpgrade(event: NavEvent) {
        event.shouldBeInstanceOf<NavEvent.GoTo>().destination shouldBe Nav.Main.Upgrade()
    }

    @Test
    fun `set-up applies a neutral equalizer and makes it the draft`() = runVmTest {
        val vm = startedVm()

        vm.setUpNeutral()

        coVerify { aapManager.sendCommand(testProfileId, command(enabled = true, 50, 50, 50)) }
        vm.state.first().draft shouldBe eq(enabled = true, 50, 50, 50)
    }

    @Test
    fun `a free user reaches upgrade and sends nothing`() = runVmTest {
        setDevice(eq(enabled = true, 60, 50, 40))
        every { upgradeInfoFlow.value.isPro } returns false

        val vm = startedVm()

        vm.setUpNeutral()
        goesToUpgrade(vm.navEvents.first())
        vm.setEnabled(false)
        goesToUpgrade(vm.navEvents.first())
        vm.setLow(70)
        goesToUpgrade(vm.navEvents.first())
        vm.resetToNeutral()
        goesToUpgrade(vm.navEvents.first())

        coVerify(exactly = 0) { aapManager.sendCommand(any(), any()) }
        // Nothing was applied, so the draft still mirrors what the device reported.
        vm.state.first().draft shouldBe eq(enabled = true, 60, 50, 40)
    }

    @Test
    fun `a second band change carries the first one, not the device's stale value`() = runVmTest {
        setDevice(eq(enabled = true, 50, 50, 50))

        val vm = startedVm()

        vm.setLow(70)
        vm.setHigh(30)

        coVerify { aapManager.sendCommand(testProfileId, command(enabled = true, 70, 50, 50)) }
        coVerify { aapManager.sendCommand(testProfileId, command(enabled = true, 70, 50, 30)) }
        coVerify(exactly = 0) { aapManager.sendCommand(testProfileId, command(enabled = true, 50, 50, 30)) }
        vm.state.first().draft shouldBe eq(enabled = true, 70, 50, 30)
    }

    @Test
    fun `reset flattens the bands but keeps the equalizer switched off`() = runVmTest {
        setDevice(eq(enabled = false, 70, 40, 60))

        val vm = startedVm()

        vm.resetToNeutral()

        coVerify { aapManager.sendCommand(testProfileId, command(enabled = false, 50, 50, 50)) }
        vm.state.first().draft shouldBe eq(enabled = false, 50, 50, 50)
    }

    @Test
    fun `switching the equalizer off keeps the band values`() = runVmTest {
        setDevice(eq(enabled = true, 70, 40, 60))

        val vm = startedVm()

        vm.setEnabled(false)

        coVerify { aapManager.sendCommand(testProfileId, command(enabled = false, 70, 40, 60)) }
        vm.state.first().draft shouldBe eq(enabled = false, 70, 40, 60)
    }

    @Test
    fun `an inbound equalizer is adopted while nothing has been edited`() = runVmTest {
        val vm = startedVm()
        vm.state.first().draft.shouldBeNull()

        setDevice(eq(enabled = true, 62, 50, 42))

        vm.state.first().draft shouldBe eq(enabled = true, 62, 50, 42)
    }

    @Test
    fun `an inbound equalizer does not overwrite an edited draft`() = runVmTest {
        setDevice(eq(enabled = true, 50, 50, 50))
        val vm = startedVm()

        vm.setLow(70)

        setDevice(eq(enabled = true, 10, 10, 10))

        val state = vm.state.first()
        state.draft shouldBe eq(enabled = true, 70, 50, 50)
        state.deviceState shouldBe eq(enabled = true, 10, 10, 10)
    }

    @Test
    fun `a failed send surfaces as an event`() = runVmTest {
        coEvery {
            aapManager.sendCommand(testProfileId, command(enabled = true, 50, 50, 50))
        } throws IllegalStateException("socket closed")

        val vm = startedVm()

        vm.setUpNeutral()

        val sendFailed = vm.events.first().shouldBeInstanceOf<EqualizerViewModel.Event.SendFailed>()
        sendFailed.command shouldBe command(enabled = true, 50, 50, 50)
        sendFailed.message shouldBe "socket closed"
    }
}
