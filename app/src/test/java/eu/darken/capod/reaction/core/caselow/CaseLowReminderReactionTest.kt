package eu.darken.capod.reaction.core.caselow

import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.caselow.CaseLowReminderReaction.Event
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CaseLowReminderReactionTest : BaseTest() {

    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var devicesFlow: MutableStateFlow<List<PodDevice>>
    private lateinit var deviceMonitor: DeviceMonitor
    private lateinit var profilesRepo: DeviceProfilesRepo

    @BeforeEach
    fun setup() {
        profilesFlow = MutableStateFlow(emptyList())
        devicesFlow = MutableStateFlow(emptyList())
        deviceMonitor = mockk(relaxed = true) { every { devices } returns devicesFlow }
        profilesRepo = mockk(relaxed = true) { every { profiles } returns profilesFlow }
    }

    private fun reaction() = CaseLowReminderReaction(deviceMonitor, profilesRepo)

    private fun profile(
        id: String = "p1",
        model: PodModel = PodModel.AIRPODS_PRO3,
        notify: Boolean = true,
        threshold: Int = 20,
    ) = AppleDeviceProfile(
        id = id,
        label = "Profile label",
        model = model,
        notifyWhenCaseLow = notify,
        caseLowThreshold = threshold,
    )

    private fun device(
        id: String = "p1",
        label: String? = "Device label",
        model: PodModel = PodModel.AIRPODS_PRO3,
        inCase: Boolean,
        percent: Int = 15,
        charging: Boolean = false,
    ) = PodDevice(
        profileId = id,
        label = label,
        ble = mockk<DualApplePods>(relaxed = true) {
            every { areBothPodsInCase } returns inCase
            every { hasCaseContext } returns true
            every { advertBatteryCasePercent } returns percent / 100f
            every { isCaseCharging } returns charging
        },
        aap = null,
        profileModel = model,
    )

    private fun TestScope.startCollecting(events: MutableList<Event>) =
        reaction().monitor().onEach { events.add(it) }.launchIn(this)

    private fun List<Event>.shows() = filterIsInstance<Event.ShowNotification>()
    private fun List<Event>.cancels() = filterIsInstance<Event.CancelNotification>()
    private fun List<Event>.reconciles() = filterIsInstance<Event.Reconcile>()

    @Test
    fun `disabled profile never shows`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile(notify = false))
        devicesFlow.value = listOf(device(inCase = false))
        val job = startCollecting(events)
        runCurrent()

        devicesFlow.value = listOf(device(inCase = true))
        runCurrent()

        events.shows().shouldBeEmpty()
        job.cancel()
    }

    @Test
    fun `out then in with a low idle case shows once`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(inCase = false, percent = 15))
        val job = startCollecting(events)
        runCurrent()

        devicesFlow.value = listOf(device(inCase = true, percent = 15))
        runCurrent()
        devicesFlow.value = listOf(device(inCase = true, percent = 15))
        runCurrent()

        events.shows() shouldContainExactly listOf(Event.ShowNotification("p1", "Device label", 15))
        job.cancel()
    }

    @Test
    fun `falls back to the profile label`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(label = null, inCase = false))
        val job = startCollecting(events)
        runCurrent()

        devicesFlow.value = listOf(device(label = null, inCase = true))
        runCurrent()

        events.shows().single().deviceLabel shouldBe "Profile label"
        job.cancel()
    }

    @Test
    fun `caseless model never shows`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile(model = PodModel.AIRPODS_MAX))
        devicesFlow.value = listOf(device(model = PodModel.AIRPODS_MAX, inCase = false))
        val job = startCollecting(events)
        runCurrent()

        devicesFlow.value = listOf(device(model = PodModel.AIRPODS_MAX, inCase = true))
        runCurrent()

        events.shows().shouldBeEmpty()
        events.reconciles() shouldContainExactly listOf(Event.Reconcile(emptySet()))
        job.cancel()
    }

    @Test
    fun `several devices for one profile are ambiguous`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(inCase = false), device(inCase = true))
        val job = startCollecting(events)
        runCurrent()

        devicesFlow.value = listOf(device(inCase = true), device(inCase = true))
        runCurrent()

        events.shows().shouldBeEmpty()
        job.cancel()
    }

    @Test
    fun `disabling after a show cancels`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(inCase = false))
        val job = startCollecting(events)
        runCurrent()
        devicesFlow.value = listOf(device(inCase = true))
        runCurrent()
        events.shows().size shouldBe 1
        events.cancels().shouldBeEmpty()

        profilesFlow.value = listOf(profile(notify = false))
        runCurrent()

        events.cancels() shouldContainExactly listOf(Event.CancelNotification("p1"))
        job.cancel()
    }

    @Test
    fun `threshold change cancels`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile(threshold = 20))
        devicesFlow.value = listOf(device(inCase = false))
        val job = startCollecting(events)
        runCurrent()
        devicesFlow.value = listOf(device(inCase = true))
        runCurrent()
        events.shows().size shouldBe 1
        events.cancels().shouldBeEmpty()

        profilesFlow.value = listOf(profile(threshold = 30))
        runCurrent()

        events.cancels() shouldContainExactly listOf(Event.CancelNotification("p1"))
        events.shows().size shouldBe 1
        job.cancel()
    }

    @Test
    fun `reconciles on first emission and when the enabled set changes`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile(id = "p1"), profile(id = "p2", notify = false))
        devicesFlow.value = listOf(device(inCase = false))
        val job = startCollecting(events)
        runCurrent()
        events.reconciles() shouldContainExactly listOf(Event.Reconcile(setOf("p1")))

        devicesFlow.value = listOf(device(inCase = true))
        runCurrent()
        events.reconciles().size shouldBe 1

        profilesFlow.value = listOf(profile(id = "p1"), profile(id = "p2", notify = true))
        runCurrent()

        events.reconciles() shouldContainExactly listOf(
            Event.Reconcile(setOf("p1")),
            Event.Reconcile(setOf("p1", "p2")),
        )
        job.cancel()
    }

    @Test
    fun `fresh reaction cancels on cancel evidence without a prior show`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(inCase = true, percent = 15, charging = true))
        val job = startCollecting(events)
        runCurrent()

        events.shows().shouldBeEmpty()
        events.cancels() shouldContainExactly listOf(Event.CancelNotification("p1"))
        job.cancel()
    }

    @Test
    fun `threshold is clamped to the maximum`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<Event>()
        profilesFlow.value = listOf(profile(threshold = 90))
        devicesFlow.value = listOf(device(inCase = false, percent = 60))
        val job = startCollecting(events)
        runCurrent()
        devicesFlow.value = listOf(device(inCase = true, percent = 60))
        runCurrent()

        events.shows().shouldBeEmpty()

        devicesFlow.value = listOf(device(inCase = false, percent = 45))
        runCurrent()
        devicesFlow.value = listOf(device(inCase = true, percent = 45))
        runCurrent()

        events.shows() shouldContainExactly listOf(Event.ShowNotification("p1", "Device label", 45))
        job.cancel()
    }
}
