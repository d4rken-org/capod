package eu.darken.capod.upgrade.ui

import eu.darken.capod.common.upgrade.core.UpgradeRepoGplay
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class UpgradeViewModelTest : BaseTest() {

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(billingData = null))
    }

    private fun TestScope.createVm(repo: UpgradeRepoGplay) = UpgradeViewModel(
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        upgradeRepo = repo,
    )

    @Test
    fun `restore with no purchase emits RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(billingData = null)
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreFailed
    }

    @Test
    fun `restore that finds a purchase stays silent`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null,
        )
        val vm = createVm(repo)

        val events = mutableListOf<UpgradeViewModel.UpgradeEvent>()
        val errors = mutableListOf<Throwable>()
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }
        val errorJob = launch(UnconfinedTestDispatcher(testScheduler)) { vm.errorEvents.collect { errors.add(it) } }

        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
        events shouldBe emptyList()
        errors shouldBe emptyList()

        eventJob.cancel()
        errorJob.cancel()
    }

    @Test
    fun `restore that times out emits RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(UpgradeViewModel.RESTORE_TIMEOUT_MS * 2)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = createVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeViewModel.UpgradeEvent.RestoreFailed
    }

    @Test
    fun `restore that errors forwards the error instead of RestoreFailed`() = runTest2 {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.restorePurchaseNow() } throws boom
        val vm = createVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
    }
}
