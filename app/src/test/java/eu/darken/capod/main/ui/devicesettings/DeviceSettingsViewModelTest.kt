package eu.darken.capod.main.ui.devicesettings

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.livedata.InstantExecutorExtension

@ExtendWith(InstantExecutorExtension::class)
class DeviceSettingsViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testAddress: BluetoothAddress = "AA:BB:CC:DD:EE:FF"

    private var vm: DeviceSettingsViewModel? = null

    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var aapManager: AapConnectionManager
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var bluetoothManager: BluetoothManager2
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var generalSettings: GeneralSettings
    private lateinit var fakeMonitorMode: FakeDataStoreValue<MonitorMode>
    private val timeSource: TimeSource = TestTimeSource()

    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>
    private lateinit var connectedDevicesFlow: MutableStateFlow<List<BluetoothDevice2>>
    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var offRejectedFlow: kotlinx.coroutines.flow.MutableSharedFlow<BluetoothAddress>

    private fun mockBondedDevice(address: BluetoothAddress): BluetoothDevice2 = mockk {
        every { this@mockk.address } returns address
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        devicesFlow = MutableStateFlow(emptyList())
        upgradeInfoFlow = MutableStateFlow(mockk<UpgradeRepo.Info>(relaxed = true).also {
            every { it.isPro } returns false
        })

        val syntheticDevice = mockk<PodDevice>(relaxed = true).also {
            every { it.profileId } returns testAddress
            every { it.address } returns testAddress
        }
        deviceMonitor = mockk<DeviceMonitor>().also {
            every { it.devices } returns devicesFlow
            coEvery { it.getDeviceForProfile(testAddress) } returns syntheticDevice
        }
        offRejectedFlow = kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 16)
        aapManager = mockk(relaxed = true) {
            every { offRejectedEvents } returns offRejectedFlow
        }
        upgradeRepo = mockk<UpgradeRepo>().also {
            every { it.upgradeInfo } returns upgradeInfoFlow
        }
        connectedDevicesFlow = MutableStateFlow(emptyList())
        bluetoothManager = mockk(relaxed = true) {
            every { isNudgeAvailable } returns true
            every { bondedDevices() } returns flowOf(emptySet())
            every { connectedDevices } returns connectedDevicesFlow
        }
        profilesFlow = MutableStateFlow(
            listOf(
                AppleDeviceProfile(id = testAddress, label = "Test", address = testAddress)
            )
        )
        profilesRepo = mockk(relaxed = true) {
            every { profiles } returns profilesFlow
        }
        fakeMonitorMode = FakeDataStoreValue(MonitorMode.AUTOMATIC)
        generalSettings = mockk<GeneralSettings>().also {
            every { it.monitorMode } returns fakeMonitorMode.mock
        }
    }

    @AfterEach
    fun teardown() {
        vm?.vmScope?.cancel()
        vm = null
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DeviceSettingsViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        deviceMonitor = deviceMonitor,
        aapManager = aapManager,
        upgradeRepo = upgradeRepo,
        bluetoothManager = bluetoothManager,
        profilesRepo = profilesRepo,
        generalSettings = generalSettings,
        timeSource = timeSource,
        webpageTool = mockk(relaxed = true),
    ).also { vm = it }

    private fun runVmTest(testBody: suspend TestScope.() -> Unit) = runTest(testDispatcher) {
        try {
            testBody()
        } finally {
            vm?.vmScope?.cancel()
            vm = null
        }
    }

    @Test
    fun `forceConnect happy path - bonded exists, nudge accepted, no event emitted`() = runVmTest {
        val bonded = mockBondedDevice(testAddress)
        every { bluetoothManager.bondedDevices() } returns flowOf(setOf(bonded))
        coEvery { bluetoothManager.nudgeConnection(bonded) } returns true

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        coVerify { bluetoothManager.nudgeConnection(bonded) }
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect when nudge not accepted - emits OpenBluetoothSettings`() = runVmTest {
        val bonded = mockBondedDevice(testAddress)
        every { bluetoothManager.bondedDevices() } returns flowOf(setOf(bonded))
        coEvery { bluetoothManager.nudgeConnection(bonded) } returns false

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OpenBluetoothSettings
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect when nudge unavailable - emits OpenBluetoothSettings without calling nudge`() = runVmTest {
        val bonded = mockBondedDevice(testAddress)
        every { bluetoothManager.bondedDevices() } returns flowOf(setOf(bonded))
        every { bluetoothManager.isNudgeAvailable } returns false

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OpenBluetoothSettings
        coVerify(exactly = 0) { bluetoothManager.nudgeConnection(any()) }
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect when no bonded device - emits OpenBluetoothSettings`() = runVmTest {
        every { bluetoothManager.bondedDevices() } returns flowOf(emptySet())

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OpenBluetoothSettings
        coVerify(exactly = 0) { bluetoothManager.nudgeConnection(any()) }
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect when bondedDevices throws SecurityException - emits OpenBluetoothSettings`() = runVmTest {
        every { bluetoothManager.bondedDevices() } throws SecurityException("BLUETOOTH_CONNECT denied")

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OpenBluetoothSettings
        coVerify(exactly = 0) { bluetoothManager.nudgeConnection(any()) }
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect when nudgeConnection throws - emits OpenBluetoothSettings and resets in-flight`() = runVmTest {
        val bonded = mockBondedDevice(testAddress)
        every { bluetoothManager.bondedDevices() } returns flowOf(setOf(bonded))
        coEvery { bluetoothManager.nudgeConnection(bonded) } throws RuntimeException("oops")

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OpenBluetoothSettings
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `forceConnect concurrent calls - second call is a no-op while first in flight`() = runVmTest {
        val bonded = mockBondedDevice(testAddress)
        every { bluetoothManager.bondedDevices() } returns flowOf(setOf(bonded))

        val gate = CompletableDeferred<Boolean>()
        coEvery { bluetoothManager.nudgeConnection(bonded) } coAnswers { gate.await() }

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.forceConnect()
        vm.forceConnect()

        gate.complete(true)

        coVerify(exactly = 1) { bluetoothManager.nudgeConnection(bonded) }
        vm.state.first().isForceConnecting shouldBe false
    }

    @Test
    fun `setDeviceName forwards SetDeviceName command to aapManager`() = runVmTest {
        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setDeviceName("NewName")

        coVerify { aapManager.sendCommand(testAddress, AapCommand.SetDeviceName("NewName")) }
    }

    @Test
    fun `setDeviceName when no target address is a no-op`() = runVmTest {
        val vm = createViewModel()

        vm.setDeviceName("NewName")

        coVerify(exactly = 0) { aapManager.sendCommand(any(), AapCommand.SetDeviceName("NewName")) }
    }

    @Test
    fun `setDeviceName failure emits SendFailed event`() = runVmTest {
        val failure = IllegalStateException("socket closed")
        coEvery {
            aapManager.sendCommand(testAddress, AapCommand.SetDeviceName("NewName"))
        } throws failure

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setDeviceName("NewName")

        val event = vm.events.first()
        val sendFailed = event.shouldBeInstanceOf<DeviceSettingsViewModel.Event.SendFailed>()
        sendFailed.command shouldBe AapCommand.SetDeviceName("NewName")
        sendFailed.message shouldBe "socket closed"
    }

    @Test
    fun `isClassicallyConnected is true when device address is in connected devices`() = runVmTest {
        connectedDevicesFlow.value = listOf(mockBondedDevice(testAddress))

        val vm = createViewModel()
        vm.initialize(testAddress)

        vm.state.first().isClassicallyConnected shouldBe true
    }

    @Test
    fun `isClassicallyConnected is false when device address is not in connected devices`() = runVmTest {
        connectedDevicesFlow.value = listOf(mockBondedDevice("XX:XX:XX:XX:XX:XX"))

        val vm = createViewModel()
        vm.initialize(testAddress)

        vm.state.first().isClassicallyConnected shouldBe false
    }

    @Test
    fun `state emits even when connected devices flow has not emitted yet`() = runVmTest {
        every { bluetoothManager.connectedDevices } returns flow { awaitCancellation() }

        val vm = createViewModel()
        vm.initialize(testAddress)

        val state = withTimeout(1_000) { vm.state.first() }

        state.device?.address shouldBe testAddress
        state.isClassicallyConnected shouldBe false
    }

    @Test
    fun `hasCustomLongPressStemAction is true when either long-press action is assigned`() = runVmTest {
        profilesFlow.value = listOf(
            AppleDeviceProfile(
                id = testAddress,
                label = "Test",
                address = testAddress,
                stemActions = StemActionsConfig(leftLong = StemAction.PlayPause),
            )
        )

        val vm = createViewModel()
        vm.initialize(testAddress)

        vm.state.first().hasCustomLongPressStemAction shouldBe true
    }

    @Test
    fun `setOnePodMode updates profile and does not send SetNcWithOneAirPod`() = runVmTest {
        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setOnePodMode(true)

        coVerify { profilesRepo.updateAppleProfile(testAddress, any()) }
        coVerify(exactly = 0) {
            aapManager.sendCommand(any(), any<AapCommand.SetNcWithOneAirPod>())
        }
    }

    @Test
    fun `setNcWithOneAirPod sends command and does not touch profile`() = runVmTest {
        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setNcWithOneAirPod(true)

        coVerify { aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true)) }
        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(any(), any()) }
    }

    @Test
    fun `setNcWithOneAirPod failure emits SendFailed event`() = runVmTest {
        val failure = IllegalStateException("socket closed")
        coEvery {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        } throws failure

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setNcWithOneAirPod(true)

        val event = vm.events.first()
        val sendFailed = event.shouldBeInstanceOf<DeviceSettingsViewModel.Event.SendFailed>()
        sendFailed.command shouldBe AapCommand.SetNcWithOneAirPod(true)
        sendFailed.message shouldBe "socket closed"
    }

    @Test
    fun `offRejectedEvents for current address emits OffModeRejectedByDevice`() = runVmTest {
        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        offRejectedFlow.emit(testAddress)

        val event = vm.events.first()
        event shouldBe DeviceSettingsViewModel.Event.OffModeRejectedByDevice
    }

    @Test
    fun `offRejectedEvents for other address is ignored`() = runVmTest {
        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        offRejectedFlow.emit("11:22:33:44:55:66")
        // No event should have been emitted — send another recognized event afterward
        // so we can assert that the first emission from vm.events is the later one.
        coEvery {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        } throws IllegalStateException("socket closed")
        vm.setNcWithOneAirPod(true)

        val event = vm.events.first()
        event.shouldBeInstanceOf<DeviceSettingsViewModel.Event.SendFailed>()
    }

    @Test
    fun `setAllowOffOption(true) as Pro sends only SetAllowOffOption`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns true

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setAllowOffOption(true)

        coVerify(exactly = 1) { aapManager.sendCommand(testAddress, AapCommand.SetAllowOffOption(true)) }
        coVerify(exactly = 0) { aapManager.sendCommand(any(), AapCommand.SetListeningModeCycle(0x0E)) }
    }

    @Test
    fun `setAllowOffOption(false) as Pro always sends SetListeningModeCycle + SetAllowOffOption(false) in order`() =
        runVmTest {
            every { upgradeInfoFlow.value.isPro } returns true

            val vm = createViewModel()
            vm.initialize(testAddress)
            vm.state.first()

            vm.setAllowOffOption(false)

            // Fallback cycle mask (0x0F) with OFF bit stripped = 0x0E.
            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                aapManager.sendCommand(testAddress, AapCommand.SetListeningModeCycle(0x0E))
                aapManager.sendCommand(testAddress, AapCommand.SetAllowOffOption(false))
            }
        }

    @Test
    fun `setAllowOffOption as non-Pro sends no commands`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns false

        val vm = createViewModel()
        vm.initialize(testAddress)
        vm.state.first()

        vm.setAllowOffOption(true)
        vm.setAllowOffOption(false)

        coVerify(exactly = 0) { aapManager.sendCommand(any(), AapCommand.SetAllowOffOption(true)) }
        coVerify(exactly = 0) { aapManager.sendCommand(any(), AapCommand.SetAllowOffOption(false)) }
        coVerify(exactly = 0) { aapManager.sendCommand(any(), AapCommand.SetListeningModeCycle(0x0E)) }
    }
}
