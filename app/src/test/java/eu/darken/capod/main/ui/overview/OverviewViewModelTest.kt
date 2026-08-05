package eu.darken.capod.main.ui.overview

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.review.ReviewTool
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.MonitorModeResolver
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.monitor.core.battery.BatteryEstimator
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.profiles.core.AppleDeviceProfile
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
import kotlinx.coroutines.test.advanceTimeBy
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
    private lateinit var batteryEstimator: BatteryEstimator
    private lateinit var reviewTool: ReviewTool
    private val timeSource: TimeSource = TestTimeSource()

    private lateinit var missingPermissionsFlow: MutableStateFlow<Set<Permission>>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var connectedDevicesFlow: MutableStateFlow<List<BluetoothDevice2>>
    private lateinit var isBluetoothEnabledFlow: MutableStateFlow<Boolean>
    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var hadLegacyReactionDataFlow: MutableStateFlow<Boolean>
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>
    private lateinit var effectiveModeFlow: MutableStateFlow<MonitorMode>
    private lateinit var reviewStateFlow: MutableStateFlow<ReviewTool.State>
    private lateinit var fakeReactionsHintDismissed: FakeDataStoreValue<Boolean>
    private lateinit var fakeHideUnmatchedDevices: FakeDataStoreValue<Boolean>

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        missingPermissionsFlow = MutableStateFlow(emptySet())
        devicesFlow = MutableStateFlow(emptyList())
        connectedDevicesFlow = MutableStateFlow(emptyList())
        isBluetoothEnabledFlow = MutableStateFlow(true)
        profilesFlow = MutableStateFlow(emptyList())
        hadLegacyReactionDataFlow = MutableStateFlow(false)
        upgradeInfoFlow = MutableStateFlow(mockk<UpgradeRepo.Info>(relaxed = true).also {
            every { it.isSettled } returns true
            every { it.error } returns null
        })
        effectiveModeFlow = MutableStateFlow(MonitorMode.AUTOMATIC)
        reviewStateFlow = MutableStateFlow(ReviewTool.State())
        fakeReactionsHintDismissed = FakeDataStoreValue(false)
        fakeHideUnmatchedDevices = FakeDataStoreValue(false)
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
            every { it.hideUnmatchedDevices } returns fakeHideUnmatchedDevices.mock
        }

        batteryEstimator = mockk<BatteryEstimator>().also {
            every { it.estimates } returns MutableStateFlow(emptyMap<String, BatteryEstimate>())
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

        // Explicitly stubbed, never relaxed: a relaxed mock hands back a flow that never emits,
        // which would starve the combine backing `state` and hang every test in this class.
        reviewTool = mockk<ReviewTool>().also {
            every { it.state } returns reviewStateFlow
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
        batteryEstimator = batteryEstimator,
        timeSource = timeSource,
        reviewTool = reviewTool,
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
                effectiveMode = MonitorMode.AUTOMATIC,
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
                effectiveMode = MonitorMode.AUTOMATIC,
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
                every { isSettled } returns true
                every { error } returns null
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = emptyList(),
                isDebug = false,
                isBluetoothEnabled = true,
                effectiveMode = MonitorMode.AUTOMATIC,
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
                every { isSettled } returns true
                every { error } returns null
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val profiled = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(profiled),
                isDebug = false,
                isBluetoothEnabled = true,
                effectiveMode = MonitorMode.AUTOMATIC,
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
                every { isSettled } returns true
                every { error } returns null
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
                effectiveMode = MonitorMode.AUTOMATIC,
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
                every { isSettled } returns true
                every { error } returns null
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
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(device1, device2, device3)
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `unsettled cold start does not truncate a paying users device list`() {
            // GPlay seed before the first billing result: non-Pro AND unsettled. Truncating here
            // would make a paying user's devices vanish and reappear on every launch.
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { isSettled } returns false
                every { error } returns null
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
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(device1, device2, device3)
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `a settled error state does not truncate the device list`() {
            // Billing settled into an error: ownership is unknown, so fail open like the gates do.
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { isSettled } returns true
                every { error } returns IllegalStateException("billing broke")
                every { type } returns UpgradeRepo.Type.GPLAY
            }
            val device1 = PodDevice(profileId = "id-1", ble = mockk(relaxed = true), aap = null)
            val device2 = PodDevice(profileId = "id-2", ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(device1, device2),
                isDebug = false,
                isBluetoothEnabled = true,
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.visibleProfiledDevices shouldBe listOf(device1, device2)
            state.hiddenProfiledDeviceCount shouldBe 0
        }

        @Test
        fun `unmatched devices unchanged regardless of pro status`() {
            val upgradeInfo = mockk<UpgradeRepo.Info> {
                every { isPro } returns false
                every { isSettled } returns true
                every { error } returns null
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
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = upgradeInfo,
                showUnmatchedDevices = false,
            )

            state.unmatchedDevices shouldBe listOf(unmatched)
            state.visibleProfiledDevices shouldBe listOf(profiled1)
            state.hiddenProfiledDeviceCount shouldBe 1
        }

        @Test
        fun `hideUnmatchedDevices defaults to false`() = runTest(testDispatcher) {
            val vm = createViewModel()
            val state = vm.state.first()

            state.hideUnmatchedDevices shouldBe false
        }

        @Test
        fun `enabling hideUnmatchedDevices setting propagates to state`() = runTest(testDispatcher) {
            fakeHideUnmatchedDevices.value = true

            val vm = createViewModel()
            val state = vm.state.first()

            state.hideUnmatchedDevices shouldBe true
        }

        @Test
        fun `hideUnmatchedDevices true hides the unmatched section`() {
            val unmatched = PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null)
            val shown = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(unmatched),
                isDebug = false,
                isBluetoothEnabled = true,
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = mockk(relaxed = true),
                showUnmatchedDevices = false,
                hideUnmatchedDevices = false,
            )
            shown.visibleUnmatchedDevices shouldBe listOf(unmatched)
            shown.shouldShowUnmatchedSection shouldBe true

            val hidden = shown.copy(hideUnmatchedDevices = true)
            hidden.visibleUnmatchedDevices shouldBe emptyList()
            hidden.shouldShowUnmatchedSection shouldBe false
        }

        @Test
        fun `only hidden unmatched devices present - no visible content`() {
            // Guards the dashboard blank-state: when only hidden unmatched devices are nearby there
            // are no profiled devices AND no visible unmatched section, so the screen shows the
            // "monitoring active" card instead of an empty list.
            val unmatched = PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null)
            val state = OverviewViewModel.State(
                now = java.time.Instant.now(),
                permissions = emptySet(),
                devices = listOf(unmatched),
                isDebug = false,
                isBluetoothEnabled = true,
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = emptyList(),
                upgradeInfo = mockk(relaxed = true),
                showUnmatchedDevices = false,
                hideUnmatchedDevices = true,
            )

            state.profiledDevices shouldBe emptyList()
            state.shouldShowUnmatchedSection shouldBe false
        }
    }

    @Nested
    inner class MonitoringStatusTests {

        private fun addressLessProfile(): DeviceProfile = AppleDeviceProfile(
            label = "My Headphones",
            model = PodModel.UNKNOWN,
        )

        private fun addressedProfile(): DeviceProfile = AppleDeviceProfile(
            label = "My AirPods",
            model = PodModel.AIRPODS_PRO2,
            address = "AA:BB:CC:DD:EE:FF",
        )

        private fun monitoringState(
            effectiveMode: MonitorMode,
            profiles: List<DeviceProfile>,
            devices: List<PodDevice> = emptyList(),
            permissions: Set<Permission> = emptySet(),
            isBluetoothEnabled: Boolean = true,
        ) = OverviewViewModel.State(
            now = java.time.Instant.now(),
            permissions = permissions,
            devices = devices,
            isDebug = false,
            isBluetoothEnabled = isBluetoothEnabled,
            effectiveMode = effectiveMode,
            profiles = profiles,
            upgradeInfo = mockk(relaxed = true),
            showUnmatchedDevices = false,
        )

        @Test
        fun `MANUAL with an address-less profile reports background monitoring off`() {
            val state = monitoringState(
                effectiveMode = MonitorMode.MANUAL,
                profiles = listOf(addressLessProfile()),
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.BACKGROUND_OFF
        }

        @Test
        fun `MANUAL still reports background monitoring off while an unknown device card shows`() {
            // The auto-created wildcard profile can match an unrecognised Apple payload, and that
            // card carries neither the missing-paired-device banner nor an edit action.
            val unknownDevice = PodDevice(
                profileId = "auto-created",
                ble = mockk<BlePodSnapshot>(relaxed = true) {
                    every { model } returns PodModel.UNKNOWN
                },
                aap = null,
            )
            val state = monitoringState(
                effectiveMode = MonitorMode.MANUAL,
                profiles = listOf(addressLessProfile()),
                devices = listOf(unknownDevice),
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.BACKGROUND_OFF
        }

        @Test
        fun `AUTOMATIC without devices reports searching`() {
            val state = monitoringState(
                effectiveMode = MonitorMode.AUTOMATIC,
                profiles = listOf(addressedProfile()),
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.SEARCHING
        }

        @Test
        fun `hidden while a scan-blocking permission is missing`() {
            val state = monitoringState(
                effectiveMode = MonitorMode.MANUAL,
                profiles = listOf(addressLessProfile()),
                permissions = setOf(Permission.BLUETOOTH_SCAN),
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.HIDDEN
        }

        @Test
        fun `hidden while bluetooth is disabled`() {
            val state = monitoringState(
                effectiveMode = MonitorMode.MANUAL,
                profiles = listOf(addressLessProfile()),
                isBluetoothEnabled = false,
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.HIDDEN
        }

        @Test
        fun `hidden when there are no profiles at all`() {
            val state = monitoringState(
                effectiveMode = MonitorMode.MANUAL,
                profiles = emptyList(),
            )

            state.monitoringStatus shouldBe OverviewViewModel.MonitoringStatus.HIDDEN
        }

        @Test
        fun `effectiveMode tracks the resolver`() = runTest(testDispatcher) {
            effectiveModeFlow.value = MonitorMode.MANUAL
            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(1_000)
            latest!!.effectiveMode shouldBe MonitorMode.MANUAL

            effectiveModeFlow.value = MonitorMode.ALWAYS

            advanceTimeBy(1_000)
            latest!!.effectiveMode shouldBe MonitorMode.ALWAYS
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

    @Nested
    inner class TroubleshootSuggestionTests {

        private val connectedAddress = "AA:BB:CC:DD:EE:FF"

        private fun connectedProfile(): DeviceProfile = AppleDeviceProfile(
            label = "Test",
            model = PodModel.AIRPODS_PRO2,
            address = connectedAddress,
        )

        private fun connectedBtDevice() = mockk<BluetoothDevice2>(relaxed = true) {
            every { address } returns connectedAddress
        }

        private fun setConnectedButNoLiveData() {
            profilesFlow.value = listOf(connectedProfile())
            connectedDevicesFlow.value = listOf(connectedBtDevice())
            devicesFlow.value = emptyList()
        }

        @Test
        fun `appears only after the debounce delay`() = runTest(testDispatcher) {
            setConnectedButNoLiveData()
            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(14_000)
            latest!!.showTroubleshootSuggestion shouldBe false

            advanceTimeBy(2_000)
            latest!!.showTroubleshootSuggestion shouldBe true
        }

        @Test
        fun `suppressed while any pod is live even after the delay`() = runTest(testDispatcher) {
            // #603 duplicate state: broadcasts ARE arriving (a live pod exists), so the card must
            // not claim "no data" even though a profiled card may momentarily read as non-live.
            profilesFlow.value = listOf(connectedProfile())
            connectedDevicesFlow.value = listOf(connectedBtDevice())
            devicesFlow.value = listOf(PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null))

            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(20_000)
            latest!!.showTroubleshootSuggestion shouldBe false
        }

        @Test
        fun `not shown when no profile is system-connected`() = runTest(testDispatcher) {
            profilesFlow.value = listOf(connectedProfile())
            connectedDevicesFlow.value = emptyList()
            devicesFlow.value = emptyList()

            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(20_000)
            latest!!.showTroubleshootSuggestion shouldBe false
        }

        @Test
        fun `hides immediately when live data returns before the delay`() = runTest(testDispatcher) {
            setConnectedButNoLiveData()
            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(10_000)
            latest!!.showTroubleshootSuggestion shouldBe false

            // A broadcast arrives before the 15s window elapses — the pending suggestion is cancelled.
            devicesFlow.value = listOf(PodDevice(profileId = null, ble = mockk(relaxed = true), aap = null))
            advanceTimeBy(10_000)
            latest!!.showTroubleshootSuggestion shouldBe false
        }
    }

    @Nested
    inner class ReviewCardTests {

        private val connectedAddress = "AA:BB:CC:DD:EE:FF"

        private fun profile(): DeviceProfile = AppleDeviceProfile(
            label = "Test",
            model = PodModel.AIRPODS_PRO2,
            address = connectedAddress,
        )

        /** Quiet overview: a set up profile, no missing permissions, nothing else to act on. */
        private fun quietOverview() {
            profilesFlow.value = listOf(profile())
            missingPermissionsFlow.value = emptySet()
            effectiveModeFlow.value = MonitorMode.AUTOMATIC
            reviewStateFlow.value = ReviewTool.State(shouldAskForReview = true)
        }

        @Test
        fun `shown on a quiet overview when the tool asks for it`() = runTest(testDispatcher) {
            quietOverview()

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe true
        }

        @Test
        fun `not shown while the tool does not ask for it`() = runTest(testDispatcher) {
            quietOverview()
            reviewStateFlow.value = ReviewTool.State(shouldAskForReview = false)

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe false
        }

        @Test
        fun `suppressed by a missing permission`() = runTest(testDispatcher) {
            quietOverview()
            missingPermissionsFlow.value = setOf(Permission.BLUETOOTH_SCAN)

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe false
        }

        @Test
        fun `suppressed by the no-profiles setup card`() = runTest(testDispatcher) {
            quietOverview()
            profilesFlow.value = emptyList()

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe false
        }

        @Test
        fun `suppressed by the background-monitoring-off card`() = runTest(testDispatcher) {
            quietOverview()
            effectiveModeFlow.value = MonitorMode.MANUAL

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe false
        }

        @Test
        fun `suppressed by the enable-Bluetooth prompt`() = runTest(testDispatcher) {
            quietOverview()
            isBluetoothEnabledFlow.value = false

            val vm = createViewModel()

            vm.state.first().showReviewCard shouldBe false
        }

        @Test
        fun `suppressed by the troubleshooter suggestion`() = runTest(testDispatcher) {
            quietOverview()
            // Connected via audio but no live data: the hint appears after its debounce window.
            connectedDevicesFlow.value = listOf(
                mockk<BluetoothDevice2>(relaxed = true) { every { address } returns connectedAddress }
            )
            devicesFlow.value = emptyList()

            val vm = createViewModel()
            var latest: OverviewViewModel.State? = null
            backgroundScope.launch { vm.state.collect { latest = it } }

            advanceTimeBy(20_000)

            latest!!.showTroubleshootSuggestion shouldBe true
            latest!!.showReviewCard shouldBe false
        }
    }

    @Test
    fun `estimateFor is null when the device has the estimate disabled`() {
        val device = mockk<PodDevice> {
            every { batteryEstimateEnabled } returns false
            every { isLive } returns true
            every { profileId } returns "p1"
        }
        val state = estimateState(device, mapOf("p1" to sampleEstimate()))

        state.estimateFor(device) shouldBe null
    }

    @Test
    fun `estimateFor returns the estimate when enabled and live`() {
        val device = mockk<PodDevice> {
            every { batteryEstimateEnabled } returns true
            every { isLive } returns true
            every { profileId } returns "p1"
        }
        val estimate = sampleEstimate()
        val state = estimateState(device, mapOf("p1" to estimate))

        state.estimateFor(device) shouldBe estimate
    }

    private fun sampleEstimate() = BatteryEstimate(
        left = BatteryEstimate.Pod(minutesRemaining = 120, fractionPerHour = 0.2f, source = BatteryEstimate.Source.LIVE),
    )

    private fun estimateState(device: PodDevice, estimates: Map<String, BatteryEstimate>) =
        OverviewViewModel.State(
            now = java.time.Instant.EPOCH,
            permissions = emptySet(),
            devices = listOf(device),
            isDebug = false,
            isBluetoothEnabled = true,
            effectiveMode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            upgradeInfo = mockk(relaxed = true),
            showUnmatchedDevices = false,
            batteryEstimates = estimates,
        )
}
