package eu.darken.capod.main.ui.overview

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.MonitorModeResolver
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.livedata.InstantExecutorExtension

@ExtendWith(InstantExecutorExtension::class)
class OverviewViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var monitorControl: MonitorControl
    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var permissionTool: PermissionTool
    private lateinit var generalSettings: GeneralSettings
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var bluetoothManager: BluetoothManager2
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var monitorModeResolver: MonitorModeResolver
    private val timeSource: TimeSource = TestTimeSource()

    private lateinit var missingPermissionsFlow: MutableStateFlow<Set<Permission>>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var connectedDevicesFlow: MutableStateFlow<List<BluetoothDevice2>>
    private lateinit var isBluetoothEnabledFlow: MutableStateFlow<Boolean>
    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var hadLegacyReactionDataFlow: MutableStateFlow<Boolean>
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>
    private lateinit var effectiveModeFlow: MutableStateFlow<MonitorMode>
    private lateinit var fakeReactionsHintDismissed: FakeDataStoreValue<Boolean>

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        missingPermissionsFlow = MutableStateFlow(emptySet())
        devicesFlow = MutableStateFlow(emptyList())
        connectedDevicesFlow = MutableStateFlow(emptyList())
        isBluetoothEnabledFlow = MutableStateFlow(true)
        profilesFlow = MutableStateFlow(emptyList())
        hadLegacyReactionDataFlow = MutableStateFlow(false)
        upgradeInfoFlow = MutableStateFlow(mockk<UpgradeRepo.Info>(relaxed = true))
        effectiveModeFlow = MutableStateFlow(MonitorMode.AUTOMATIC)
        fakeReactionsHintDismissed = FakeDataStoreValue(false)
        Bugs.isDebug.value = false

        monitorControl = mockk(relaxed = true)

        deviceMonitor = mockk<DeviceMonitor>().also {
            every { it.devices } returns devicesFlow
        }

        permissionTool = mockk<PermissionTool>(relaxed = true).also {
            every { it.missingPermissions } returns missingPermissionsFlow
            every { it.missingScanPermissions } returns missingPermissionsFlow.map { perms ->
                perms.filter { it.isScanBlocking }.toSet()
            }
        }

        generalSettings = mockk<GeneralSettings>().also {
            every { it.reactionsHintDismissed } returns fakeReactionsHintDismissed.mock
        }

        monitorModeResolver = mockk<MonitorModeResolver>().also {
            every { it.effectiveMode } returns effectiveModeFlow
        }

        upgradeRepo = mockk<UpgradeRepo>().also {
            every { it.upgradeInfo } returns upgradeInfoFlow
        }

        bluetoothManager = mockk<BluetoothManager2>().also {
            every { it.connectedDevices } returns connectedDevicesFlow
            every { it.isBluetoothEnabled } returns isBluetoothEnabledFlow
        }

        profilesRepo = mockk<DeviceProfilesRepo>().also {
            every { it.profiles } returns profilesFlow
            every { it.hadLegacyReactionData } returns hadLegacyReactionDataFlow
        }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        Bugs.isDebug.value = false
    }

    private fun createViewModel() = OverviewViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        monitorControl = monitorControl,
        deviceMonitor = deviceMonitor,
        permissionTool = permissionTool,
        generalSettings = generalSettings,
        upgradeRepo = upgradeRepo,
        bluetoothManager = bluetoothManager,
        profilesRepo = profilesRepo,
        aapManager = mockk(relaxed = true),
        monitorModeResolver = monitorModeResolver,
        timeSource = timeSource,
    )

    @Nested
    inner class StateTests {

        @Test
        fun `state emits with correct initial values`() = runTest(testDispatcher) {
            val vm = createViewModel()
            val state = vm.state.first()

            state.permissions shouldBe emptySet()
            state.devices shouldBe emptyList()
            state.isDebug shouldBe false
            state.isBluetoothEnabled shouldBe true
            state.showUnmatchedDevices shouldBe false
        }

        @Test
        fun `devices empty when permissions missing`() = runTest(testDispatcher) {
            missingPermissionsFlow.value = setOf(Permission.BLUETOOTH)

            val vm = createViewModel()
            val state = vm.state.first()

            state.devices shouldBe emptyList()
        }

        @Test
        fun `devices passed through when permissions granted`() = runTest(testDispatcher) {
            val device = PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null)
            devicesFlow.value = listOf(device)

            val vm = createViewModel()
            val state = vm.state.first()

            state.devices shouldBe listOf(device)
        }

        @Test
        fun `profiledDevices returns only devices with non-null profile`() {
            val profiled = PodDevice(
                profileId = "test-id",
                ble = mockk(relaxed = true),
                aap = null,
            )
            val unmatched = PodDevice(
                profileId = null,
                ble = mockk(relaxed = true),
                aap = null,
            )

            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(profiled, unmatched),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = mockk(relaxed = true),
                showUnmatchedDevices = false,
            )

            state.profiledDevices shouldBe listOf(profiled)
        }

        @Test
        fun `unmatchedDevices returns only devices with null profile`() {
            val profiled = PodDevice(
                profileId = "test-id",
                ble = mockk(relaxed = true),
                aap = null,
            )
            val unmatched = PodDevice(
                profileId = null,
                ble = mockk(relaxed = true),
                aap = null,
            )

            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(profiled, unmatched),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = mockk(relaxed = true),
                showUnmatchedDevices = false,
            )

            state.unmatchedDevices shouldBe listOf(unmatched)
        }

        @Test
        fun `free user with no profiled devices - visible empty, hidden 0`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = emptyList(),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe emptyList()
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `free user with 1 profiled device - visible 1, hidden 0`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val profiled = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(profiled),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(profiled)
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `free user with 3 profiled devices - visible 1, hidden 2`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val device1 = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val device2 = PodDevice(profileId = "id-2", ble = mockk(relaxed = true), aap = null)
            val device3 = PodDevice(profileId = "id-3", ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(device1, device2, device3),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(device1)
            state.hiddenProfiledDeviceCount shouldBe 2
        }

        @Test
        fun `pro user with multiple profiled devices - all visible, hidden 0`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns true
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val device1 = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val device2 = PodDevice(profileId = "id-2", ble = mockk(relaxed = true), aap = null)
            val device3 = PodDevice(profileId = "id-3", ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(device1, device2, device3),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(device1, device2, device3)
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `unmatched devices unchanged regardless of pro status`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val profiled1 = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val profiled2 = PodDevice(profileId = "id-2", ble = mockk(relaxed = true), aap = null)
            val unmatched = PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(profiled1, profiled2, unmatched),
                isDebug = false,
                isBluetoothEnabled = true,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.unmatchedDevices shouldBe listOf(unmatched)
            state.visibleProfiledDevices shouldBe listOf(profiled1)
            state.hiddenProfiledDeviceCount shouldBe 1
        }
    }

    @Nested
    inner class WorkerAutolaunchTests {

        @Test
        fun `MANUAL mode - never starts monitor`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.MANUAL
            val vm = createViewModel()

            // Collect workerAutolaunch to trigger the side effect
            vm.workerAutolaunch.first()

            verify(exactly = 0) { monitorControl.startMonitor(any()) }
        }

        @Test
        fun `ALWAYS mode - starts monitor when permissions OK`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.ALWAYS
            val vm = createViewModel()

            vm.workerAutolaunch.first()

            verify(exactly = 1) { monitorControl.startMonitor(any()) }
        }

        @Test
        fun `ALWAYS mode - does NOT start monitor when permissions missing`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.ALWAYS
            missingPermissionsFlow.value = setOf(Permission.BLUETOOTH)
            val vm = createViewModel()

            vm.workerAutolaunch.first()

            verify(exactly = 0) { monitorControl.startMonitor(any()) }
        }

        @Test
        fun `AUTOMATIC mode - starts monitor when connected devices exist`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.AUTOMATIC
            connectedDevicesFlow.value = listOf(mockk(relaxed = true))
            val vm = createViewModel()

            vm.workerAutolaunch.first()

            verify(exactly = 1) { monitorControl.startMonitor(any()) }
        }

        @Test
        fun `AUTOMATIC mode - does NOT start monitor when no connected devices`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.AUTOMATIC
            connectedDevicesFlow.value = emptyList()
            val vm = createViewModel()

            vm.workerAutolaunch.first()

            verify(exactly = 0) { monitorControl.startMonitor(any()) }
        }

        @Test
        fun `mode transition AUTOMATIC to ALWAYS triggers startMonitor`() = runTest(testDispatcher) {
            // Regression guard: the old impl only re-evaluated on missingScanPermissions emissions,
            // so toggling auto-connect (which flips effective mode AUTOMATIC -> ALWAYS) wouldn't
            // actually start the monitor. The combine() over effectiveMode must keep it reactive.
            effectiveModeFlow.value = MonitorMode.AUTOMATIC
            connectedDevicesFlow.value = emptyList()
            val vm = createViewModel()

            // Subscribe so the workerAutolaunch onEach actually runs.
            val collectJob = launch { vm.workerAutolaunch.collect {} }
            advanceUntilIdle()

            verify(exactly = 0) { monitorControl.startMonitor(any()) }

            // Flip to ALWAYS — should start the monitor without any other input changing.
            effectiveModeFlow.value = MonitorMode.ALWAYS
            advanceUntilIdle()

            verify(exactly = 1) { monitorControl.startMonitor(any()) }

            collectJob.cancel()
        }

        @Test
        fun `connected device appearing in AUTOMATIC triggers startMonitor`() = runTest(testDispatcher) {
            // Symmetric to the mode-transition test: flipping connectedDevices from empty
            // to non-empty under AUTOMATIC must also trigger autolaunch.
            effectiveModeFlow.value = MonitorMode.AUTOMATIC
            connectedDevicesFlow.value = emptyList()
            val vm = createViewModel()

            val collectJob = launch { vm.workerAutolaunch.collect {} }
            advanceUntilIdle()

            verify(exactly = 0) { monitorControl.startMonitor(any()) }

            connectedDevicesFlow.value = listOf(mockk(relaxed = true))
            advanceUntilIdle()

            verify(exactly = 1) { monitorControl.startMonitor(any()) }

            collectJob.cancel()
        }
    }

    @Nested
    inner class ActionTests {

        @Test
        fun `toggleUnmatchedDevices flips state`() = runTest(testDispatcher) {
            val vm = createViewModel()
            val initial = vm.state.first()
            initial.showUnmatchedDevices shouldBe false

            vm.toggleUnmatchedDevices()

            val updated = vm.state.first()
            updated.showUnmatchedDevices shouldBe true
        }

        @Test
        fun `onPermissionResult calls permissionTool recheck`() = runTest(testDispatcher) {
            val vm = createViewModel()

            vm.onPermissionResult()

            verify(exactly = 1) { permissionTool.recheck() }
        }

        @Test
        fun `onSettingsPermissionResult retries permission recheck`() = runTest(testDispatcher) {
            val vm = createViewModel()

            vm.onSettingsPermissionResult()
            advanceUntilIdle()

            verify(exactly = 2) { permissionTool.recheck() }
        }

        @Test
        fun `requestPermission emits event`() = runTest(testDispatcher) {
            val vm = createViewModel()
            vm.requestPermission(Permission.BLUETOOTH)

            val event = vm.requestPermissionEvent.first()
            event shouldBe Permission.BLUETOOTH
        }
    }
}
