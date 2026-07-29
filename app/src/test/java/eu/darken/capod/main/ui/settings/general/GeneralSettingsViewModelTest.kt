package eu.darken.capod.main.ui.settings.general

import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import testhelpers.datastore.FakeDataStoreValue
import testhelpers.livedata.InstantExecutorExtension
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeStyle

@ExtendWith(InstantExecutorExtension::class)
class GeneralSettingsViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var vm: GeneralSettingsViewModel? = null

    private lateinit var generalSettings: GeneralSettings
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>

    private lateinit var themeMode: FakeDataStoreValue<ThemeMode>
    private lateinit var themeStyle: FakeDataStoreValue<ThemeStyle>
    private lateinit var themeColor: FakeDataStoreValue<ThemeColor>

    /**
     * Hot flow, never a finite `flowOf`: `isProForUi` waits for a settled emission, and a finished
     * flow would push every gate onto its fail-open timeout path instead of the real decision.
     */
    private fun info(isPro: Boolean, isSettled: Boolean = true, error: Throwable? = null) =
        mockk<UpgradeRepo.Info>(relaxed = true).also {
            every { it.isPro } returns isPro
            every { it.isSettled } returns isSettled
            every { it.error } returns error
            every { it.type } returns UpgradeRepo.Type.GPLAY
        }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        themeMode = FakeDataStoreValue(ThemeMode.SYSTEM)
        themeStyle = FakeDataStoreValue(ThemeStyle.DEFAULT)
        themeColor = FakeDataStoreValue(ThemeColor.BLUE)

        generalSettings = mockk<GeneralSettings>().also {
            every { it.useExtraMonitorNotification } returns FakeDataStoreValue(false).mock
            every { it.keepConnectedNotificationAfterDisconnect } returns FakeDataStoreValue(false).mock
            every { it.isOffloadedFilteringDisabled } returns FakeDataStoreValue(false).mock
            every { it.isOffloadedBatchingDisabled } returns FakeDataStoreValue(false).mock
            every { it.useIndirectScanResultCallback } returns FakeDataStoreValue(false).mock
            every { it.hideUnmatchedDevices } returns FakeDataStoreValue(false).mock
            every { it.themeMode } returns themeMode.mock
            every { it.themeStyle } returns themeStyle.mock
            every { it.themeColor } returns themeColor.mock
        }

        upgradeInfoFlow = MutableStateFlow(info(isPro = false))
        upgradeRepo = mockk<UpgradeRepo>().also {
            every { it.upgradeInfo } returns upgradeInfoFlow
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

    private fun createViewModel() = GeneralSettingsViewModel(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        generalSettings = generalSettings,
        upgradeRepo = upgradeRepo,
    ).also { vm = it }

    // --- Presentation ---

    @Test
    fun `a settled free user sees the hard-locked upgrade presentation`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false, isSettled = true)

        createViewModel().state.first().isUpgradeLocked shouldBe true
    }

    @Test
    fun `the unsettled cold start is not presented as hard-locked`() = runVmTest {
        // GPlay seed before the first billing result reports non-Pro even for paying users —
        // rendering the upgrade branch here would route them to upgrade without a setter ever
        // running (the screen never reaches the isProForUi gate).
        upgradeInfoFlow.value = info(isPro = false, isSettled = false)

        createViewModel().state.first().isUpgradeLocked shouldBe false
    }

    @Test
    fun `a settled error state is not presented as hard-locked`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false, isSettled = true, error = IllegalStateException("nope"))

        createViewModel().state.first().isUpgradeLocked shouldBe false
    }

    @Test
    fun `a pro user is never hard-locked`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = true, isSettled = true)

        createViewModel().state.first().isUpgradeLocked shouldBe false
    }

    // --- Setter gates ---

    @Test
    fun `a pro user can change the theme mode`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = true)
        val vm = createViewModel()
        vm.state.first()

        vm.setThemeMode(ThemeMode.DARK)

        themeMode.value shouldBe ThemeMode.DARK
    }

    @Test
    fun `a settled free user is routed to upgrade instead of changing the theme mode`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)
        val vm = createViewModel()
        vm.state.first()

        val navEvent = async { vm.navEvents.first() }
        vm.setThemeMode(ThemeMode.DARK)

        themeMode.value shouldBe ThemeMode.SYSTEM
        navEvent.await().shouldBeInstanceOf<NavEvent.GoTo>().destination shouldBe Nav.Main.Upgrade()
    }

    @Test
    fun `a paying user tapping during the unsettled cold start still gets the write`() = runVmTest {
        // isProForUi waits for the first settled Info instead of denying off the seed.
        upgradeInfoFlow.value = info(isPro = false, isSettled = false)
        val vm = createViewModel()
        vm.state.first()

        vm.setThemeStyle(ThemeStyle.MATERIAL_YOU)
        themeStyle.value shouldBe ThemeStyle.DEFAULT

        upgradeInfoFlow.value = info(isPro = true, isSettled = true)

        themeStyle.value shouldBe ThemeStyle.MATERIAL_YOU
    }

    @Test
    fun `a settled free user is routed to upgrade instead of changing the theme color`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)
        val vm = createViewModel()
        vm.state.first()

        val navEvent = async { vm.navEvents.first() }
        vm.setThemeColor(ThemeColor.AMBER)

        themeColor.value shouldBe ThemeColor.BLUE
        navEvent.await().shouldBeInstanceOf<NavEvent.GoTo>().destination shouldBe Nav.Main.Upgrade()
    }

    @Test
    fun `non-pro settings are writable without an entitlement`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)
        val vm = createViewModel()

        vm.setHideUnmatchedDevices(true)

        vm.state.first().hideUnmatchedDevices shouldBe true
    }
}
