package eu.darken.capod.main.ui.presscontrols

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
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
class PressControlsViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testProfileId: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private var vm: PressControlsViewModel? = null

    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var aapManager: AapConnectionManager
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>

    private fun makeProfile(stemActions: StemActionsConfig = StemActionsConfig()) =
        AppleDeviceProfile(id = testProfileId, label = "Test", address = testProfileId, stemActions = stemActions)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        devicesFlow = MutableStateFlow(emptyList())
        val syntheticDevice = mockk<PodDevice>(relaxed = true).also {
            every { it.profileId } returns testProfileId
            every { it.address } returns testProfileId
            every { it.isAapReady } returns true
        }
        deviceMonitor = mockk {
            every { devices } returns devicesFlow
            coEvery { getDeviceForProfile(testProfileId) } returns syntheticDevice
        }
        aapManager = mockk(relaxed = true)
        upgradeInfoFlow = MutableStateFlow(mockk<UpgradeRepo.Info>(relaxed = true).also {
            every { it.isPro } returns false
        })
        upgradeRepo = mockk {
            every { upgradeInfo } returns upgradeInfoFlow
        }
        profilesFlow = MutableStateFlow(listOf(makeProfile()))
        profilesRepo = mockk(relaxed = true) {
            every { profiles } returns profilesFlow
            // Make updateAppleProfile actually mutate the in-memory profile flow.
            coEvery { updateAppleProfile(testProfileId, any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val transform = arg<(AppleDeviceProfile) -> AppleDeviceProfile>(1)
                val current = profilesFlow.value
                    .filterIsInstance<AppleDeviceProfile>()
                    .first { it.id == testProfileId }
                profilesFlow.value = profilesFlow.value.map {
                    if (it.id == testProfileId) transform(current) else it
                }
            }
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

    private fun createViewModel() = PressControlsViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        deviceMonitor = deviceMonitor,
        aapManager = aapManager,
        upgradeRepo = upgradeRepo,
        profilesRepo = profilesRepo,
    ).also { vm = it }

    @Test
    fun `setLeftSingle as Pro persists action to profile`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns true

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.PLAY_PAUSE)

        coVerify { profilesRepo.updateAppleProfile(testProfileId, any()) }
        profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions.leftSingle shouldBe StemAction.PLAY_PAUSE
    }

    @Test
    fun `setLeftSingle to non-NONE as free user navigates to Upgrade and does not mutate`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns false

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.PLAY_PAUSE)

        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(testProfileId, any()) }
        profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions.leftSingle shouldBe StemAction.NONE
    }

    @Test
    fun `setLeftSingle to NONE as free user is allowed and clears existing mapping`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns false
        profilesFlow.value = listOf(makeProfile(StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE)))

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.NONE)

        coVerify { profilesRepo.updateAppleProfile(testProfileId, any()) }
        profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions.leftSingle shouldBe StemAction.NONE
    }

    @Test
    fun `setLeftSingle to current value as free user is a no-op`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns false

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.NONE) // already NONE

        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(testProfileId, any()) }
    }

    @Test
    fun `setLeftSingle assigns NO_ACTION to right side when right was NONE`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns true

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.PLAY_PAUSE)

        val updated = profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions
        updated.leftSingle shouldBe StemAction.PLAY_PAUSE
        updated.rightSingle shouldBe StemAction.NO_ACTION
    }

    @Test
    fun `setLeftSingle to NONE clears the opposite side too`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns true
        profilesFlow.value = listOf(
            makeProfile(StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE, rightSingle = StemAction.NO_ACTION))
        )

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setLeftSingle(StemAction.NONE)

        val updated = profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions
        updated.leftSingle shouldBe StemAction.NONE
        updated.rightSingle shouldBe StemAction.NONE
    }

    @Test
    fun `resetAll wipes stemActions on the profile`() = runVmTest {
        profilesFlow.value = listOf(
            makeProfile(StemActionsConfig(leftSingle = StemAction.PLAY_PAUSE, rightLong = StemAction.VOLUME_UP))
        )

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.resetAll()

        profilesFlow.value
            .filterIsInstance<AppleDeviceProfile>()
            .first().stemActions shouldBe StemActionsConfig()
    }

    @Test
    fun `setPressSpeed sends AAP command and is not Pro-gated`() = runVmTest {
        every { upgradeInfoFlow.value.isPro } returns false

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setPressSpeed(AapSetting.PressSpeed.Value.SLOWER)

        coVerify {
            aapManager.sendCommand(testProfileId, AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.SLOWER))
        }
    }

    @Test
    fun `setPressSpeed failure emits SendFailed event`() = runVmTest {
        coEvery {
            aapManager.sendCommand(testProfileId, AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.SLOWEST))
        } throws IllegalStateException("socket closed")

        val vm = createViewModel()
        vm.initialize(testProfileId)
        vm.state.first()

        vm.setPressSpeed(AapSetting.PressSpeed.Value.SLOWEST)

        val event = vm.events.first()
        val sendFailed = event.shouldBeInstanceOf<PressControlsViewModel.Event.SendFailed>()
        sendFailed.command shouldBe AapCommand.SetPressSpeed(AapSetting.PressSpeed.Value.SLOWEST)
        sendFailed.message shouldBe "socket closed"
    }

    @Test
    fun `state isAapReady reflects device readiness`() = runVmTest {
        val notReady = mockk<PodDevice>(relaxed = true).also {
            every { it.profileId } returns testProfileId
            every { it.address } returns testProfileId
            every { it.isAapReady } returns false
        }
        coEvery { deviceMonitor.getDeviceForProfile(testProfileId) } returns notReady

        val vm = createViewModel()
        vm.initialize(testProfileId)

        vm.state.first().isAapReady shouldBe false
    }
}
