package eu.darken.capod.reaction.ui

import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.reaction.core.ReactionSettings
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.livedata.InstantExecutorExtension
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantExecutorExtension::class)
class ReactionSettingsViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var reactionSettings: ReactionSettings
    private lateinit var generalSettings: GeneralSettings

    private lateinit var fakeOnePodMode: FakeDataStoreValue<Boolean>
    private lateinit var fakeAutoPlay: FakeDataStoreValue<Boolean>
    private lateinit var fakeAutoPause: FakeDataStoreValue<Boolean>
    private lateinit var fakeAutoConnect: FakeDataStoreValue<Boolean>
    private lateinit var fakeAutoConnectCondition: FakeDataStoreValue<AutoConnectCondition>
    private lateinit var fakeShowPopUpOnCaseOpen: FakeDataStoreValue<Boolean>
    private lateinit var fakeShowPopUpOnConnection: FakeDataStoreValue<Boolean>
    private lateinit var fakeMonitorMode: FakeDataStoreValue<MonitorMode>

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        upgradeInfoFlow = MutableStateFlow(mockUpgradeInfo(isPro = false))
        upgradeRepo = mockk<UpgradeRepo>().also {
            every { it.upgradeInfo } returns upgradeInfoFlow
        }

        fakeOnePodMode = FakeDataStoreValue(false)
        fakeAutoPlay = FakeDataStoreValue(false)
        fakeAutoPause = FakeDataStoreValue(false)
        fakeAutoConnect = FakeDataStoreValue(false)
        fakeAutoConnectCondition = FakeDataStoreValue(AutoConnectCondition.WHEN_SEEN)
        fakeShowPopUpOnCaseOpen = FakeDataStoreValue(false)
        fakeShowPopUpOnConnection = FakeDataStoreValue(false)

        reactionSettings = mockk<ReactionSettings>().also {
            every { it.onePodMode } returns fakeOnePodMode.mock
            every { it.autoPlay } returns fakeAutoPlay.mock
            every { it.autoPause } returns fakeAutoPause.mock
            every { it.autoConnect } returns fakeAutoConnect.mock
            every { it.autoConnectCondition } returns fakeAutoConnectCondition.mock
            every { it.showPopUpOnCaseOpen } returns fakeShowPopUpOnCaseOpen.mock
            every { it.showPopUpOnConnection } returns fakeShowPopUpOnConnection.mock
        }

        fakeMonitorMode = FakeDataStoreValue(MonitorMode.AUTOMATIC)
        generalSettings = mockk<GeneralSettings>().also {
            every { it.monitorMode } returns fakeMonitorMode.mock
        }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ReactionSettingsViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        reactionSettings = reactionSettings,
        generalSettings = generalSettings,
        upgradeRepo = upgradeRepo,
    )

    private fun mockUpgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().also {
        every { it.isPro } returns isPro
    }

    @Nested
    inner class StateTests {

        @Test
        fun `state combines all flows correctly`() = runTest(testDispatcher) {
            fakeAutoPlay.value = true
            fakeAutoPause.value = true
            fakeAutoConnect.value = true
            fakeOnePodMode.value = true
            fakeShowPopUpOnCaseOpen.value = true
            fakeShowPopUpOnConnection.value = true
            fakeAutoConnectCondition.value = AutoConnectCondition.CASE_OPEN
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)

            val vm = createViewModel()
            val state = vm.state.first()

            state.isPro shouldBe true
            state.autoPlay shouldBe true
            state.autoPause shouldBe true
            state.autoConnect shouldBe true
            state.onePodMode shouldBe true
            state.showPopUpOnCaseOpen shouldBe true
            state.showPopUpOnConnection shouldBe true
            state.autoConnectCondition shouldBe AutoConnectCondition.CASE_OPEN
        }
    }

    @Nested
    inner class AutoPlayTests {

        @Test
        fun `setAutoPlay true when pro - sets setting`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)
            val vm = createViewModel()
            vm.state.first() // ensure state is initialized

            vm.setAutoPlay(true)

            fakeAutoPlay.value shouldBe true
        }

        @Test
        fun `setAutoPlay true when not pro - navigates to upgrade, does NOT change setting`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = false)
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoPlay(true)

            fakeAutoPlay.value shouldBe false
            val event = vm.navEvents.first()
            (event as NavEvent.GoTo).destination shouldBe Nav.Main.Upgrade
        }

        @Test
        fun `setAutoPlay false - always sets (no pro check)`() = runTest(testDispatcher) {
            fakeAutoPlay.value = true
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoPlay(false)

            fakeAutoPlay.value shouldBe false
        }
    }

    @Nested
    inner class AutoPauseTests {

        @Test
        fun `setAutoPause true when pro - sets setting`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoPause(true)

            fakeAutoPause.value shouldBe true
        }

        @Test
        fun `setAutoPause true when not pro - navigates to upgrade`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = false)
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoPause(true)

            fakeAutoPause.value shouldBe false
            val event = vm.navEvents.first()
            (event as NavEvent.GoTo).destination shouldBe Nav.Main.Upgrade
        }

        @Test
        fun `setAutoPause false - always sets`() = runTest(testDispatcher) {
            fakeAutoPause.value = true
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoPause(false)

            fakeAutoPause.value shouldBe false
        }
    }

    @Nested
    inner class AutoConnectTests {

        @Test
        fun `setAutoConnect true when pro - sets setting AND sets monitorMode to ALWAYS`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoConnect(true)

            fakeAutoConnect.value shouldBe true
            fakeMonitorMode.value shouldBe MonitorMode.ALWAYS
        }

        @Test
        fun `setAutoConnect true when not pro - navigates to upgrade`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = false)
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoConnect(true)

            fakeAutoConnect.value shouldBe false
            val event = vm.navEvents.first()
            (event as NavEvent.GoTo).destination shouldBe Nav.Main.Upgrade
        }

        @Test
        fun `setAutoConnect false - sets setting, does NOT change monitorMode`() = runTest(testDispatcher) {
            fakeAutoConnect.value = true
            fakeMonitorMode.value = MonitorMode.ALWAYS
            val vm = createViewModel()
            vm.state.first()

            vm.setAutoConnect(false)

            fakeAutoConnect.value shouldBe false
            fakeMonitorMode.value shouldBe MonitorMode.ALWAYS
        }
    }

    @Nested
    inner class PopUpTests {

        @Test
        fun `setShowPopUpOnCaseOpen true when pro - sets setting`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)
            val vm = createViewModel()
            vm.state.first()

            vm.setShowPopUpOnCaseOpen(true)

            fakeShowPopUpOnCaseOpen.value shouldBe true
        }

        @Test
        fun `setShowPopUpOnCaseOpen true when not pro - navigates to upgrade`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = false)
            val vm = createViewModel()
            vm.state.first()

            vm.setShowPopUpOnCaseOpen(true)

            fakeShowPopUpOnCaseOpen.value shouldBe false
            val event = vm.navEvents.first()
            (event as NavEvent.GoTo).destination shouldBe Nav.Main.Upgrade
        }

        @Test
        fun `setShowPopUpOnConnection true when pro - sets setting`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = true)
            val vm = createViewModel()
            vm.state.first()

            vm.setShowPopUpOnConnection(true)

            fakeShowPopUpOnConnection.value shouldBe true
        }

        @Test
        fun `setShowPopUpOnConnection true when not pro - navigates to upgrade`() = runTest(testDispatcher) {
            upgradeInfoFlow.value = mockUpgradeInfo(isPro = false)
            val vm = createViewModel()
            vm.state.first()

            vm.setShowPopUpOnConnection(true)

            fakeShowPopUpOnConnection.value shouldBe false
            val event = vm.navEvents.first()
            (event as NavEvent.GoTo).destination shouldBe Nav.Main.Upgrade
        }
    }

    @Nested
    inner class DirectSettingTests {

        @Test
        fun `setOnePodMode - direct assignment (no pro check)`() = runTest(testDispatcher) {
            val vm = createViewModel()

            vm.setOnePodMode(true)

            fakeOnePodMode.value shouldBe true
        }

        @Test
        fun `setAutoConnectCondition - direct assignment (no pro check)`() = runTest(testDispatcher) {
            val vm = createViewModel()

            vm.setAutoConnectCondition(AutoConnectCondition.IN_EAR)

            fakeAutoConnectCondition.value shouldBe AutoConnectCondition.IN_EAR
        }
    }
}
