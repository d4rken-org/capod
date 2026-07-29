package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

class WidgetConfigurationViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testWidgetId = 42
    private val testProfileId = "AA:BB:CC:DD:EE:FF"
    private var vm: WidgetConfigurationViewModel? = null

    private lateinit var context: Context
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetSettings: WidgetSettings
    private lateinit var profilesRepo: DeviceProfilesRepo
    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var upgradeInfoFlow: MutableStateFlow<UpgradeRepo.Info>

    /**
     * Hot flow, never a finite `flowOf`: `isProForUi` waits for a settled emission, and a finished
     * flow would push the confirm gate onto its fail-open timeout path instead of the real decision.
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

        context = mockk(relaxed = true)
        appWidgetManager = mockk<AppWidgetManager>().also {
            // Null info -> not an ANC widget, so no ComponentName is constructed.
            every { it.getAppWidgetInfo(any()) } returns null
            every { it.getAppWidgetOptions(any()) } returns mockk<Bundle>(relaxed = true)
        }
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager

        widgetSettings = mockk<WidgetSettings>(relaxed = true).also {
            every { it.getWidgetConfig(any()) } returns WidgetConfig(profileId = testProfileId)
        }

        profilesFlow = MutableStateFlow(
            listOf(AppleDeviceProfile(id = testProfileId, label = "Test", address = testProfileId))
        )
        profilesRepo = mockk<DeviceProfilesRepo>().also {
            every { it.profiles } returns profilesFlow
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
        unmockkStatic(AppWidgetManager::class)
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

    private fun createViewModel(widgetId: Int = testWidgetId) = WidgetConfigurationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AppWidgetManager.EXTRA_APPWIDGET_ID to widgetId)),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        deviceProfilesRepo = profilesRepo,
        widgetSettings = widgetSettings,
        upgradeRepo = upgradeRepo,
        context = context,
    ).also { vm = it }

    @Test
    fun `a settled pro user confirms`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = true)

        createViewModel().decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.Confirmed
    }

    @Test
    fun `a settled free user needs an upgrade`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)

        createViewModel().decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.UpgradeRequired
    }

    @Test
    fun `a paying user tapping during the unsettled cold start still confirms`() = runVmTest {
        // Without the suspending gate the seed would look free and send an owner shopping again.
        upgradeInfoFlow.value = info(isPro = false, isSettled = false)
        val vm = createViewModel()

        val outcome = async { vm.decideConfirm() }
        upgradeInfoFlow.value = info(isPro = true, isSettled = true)

        outcome.await() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.Confirmed
    }

    @Test
    fun `billing that never settles falls back to the current state`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false, isSettled = false)
        val vm = createViewModel()

        val outcome = async { vm.decideConfirm() }
        advanceUntilIdle()

        outcome.await() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.UpgradeRequired
    }

    /**
     * The activity's upgrade-return callback re-asks [WidgetConfigurationViewModel.decideConfirm]
     * instead of trusting the upgrade activity's result code, so both return-path outcomes are the
     * same decision the confirm tap makes.
     */
    @Test
    fun `returning from the upgrade flow confirms once the entitlement arrived`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)
        val vm = createViewModel()
        vm.decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.UpgradeRequired

        upgradeInfoFlow.value = info(isPro = true)

        vm.decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.Confirmed
    }

    @Test
    fun `returning from the upgrade flow still free stays in the configuration`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = false)
        val vm = createViewModel()
        vm.decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.UpgradeRequired

        // The upgrade activity returned, but no entitlement materialized: never RESULT_OK.
        vm.decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.UpgradeRequired
    }

    @Test
    fun `an invalid widget id is never confirmable`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = true)

        val vm = createViewModel(widgetId = AppWidgetManager.INVALID_APPWIDGET_ID)

        vm.decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.Invalid
    }

    @Test
    fun `a selection without a matching profile is never confirmable`() = runVmTest {
        upgradeInfoFlow.value = info(isPro = true)
        profilesFlow.value = emptyList()

        createViewModel().decideConfirm() shouldBe WidgetConfigurationViewModel.ConfirmOutcome.Invalid
    }
}
