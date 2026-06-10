package eu.darken.capod.reaction.core.charged

import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ChargedReactionTest : BaseTest() {

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

    private fun reaction() = ChargedReaction(deviceMonitor, profilesRepo)

    private fun profile(
        id: String = "p1",
        model: PodModel = PodModel.AIRPODS_PRO2_USBC,
        notify: Boolean = true,
        threshold: Int = 80,
        scope: ChargedSlotScope = ChargedSlotScope.PODS_AND_CASE,
    ) = AppleDeviceProfile(
        id = id,
        label = "Test",
        model = model,
        notifyWhenCharged = notify,
        chargedThreshold = threshold,
        chargedSlotScope = scope,
    )

    private fun aap(vararg slots: Pair<AapPodState.BatteryType, Pair<Float, AapPodState.ChargingState>>) =
        AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            batteries = slots.associate { (type, v) ->
                type to AapPodState.Battery(type, v.first, v.second)
            },
        )

    private fun device(
        id: String = "p1",
        model: PodModel = PodModel.AIRPODS_PRO2_USBC,
        aap: AapPodState,
    ) = PodDevice(profileId = id, ble = null, aap = aap, profileModel = model)

    private fun bothPodsCharging(percent: Float, charging: AapPodState.ChargingState = AapPodState.ChargingState.CHARGING) =
        aap(
            AapPodState.BatteryType.LEFT to (percent to charging),
            AapPodState.BatteryType.RIGHT to (percent to charging),
        )

    @Test
    fun `fires once when all session slots reach the threshold`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<ChargedReaction.Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.7f)))
        val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
        runCurrent()

        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.9f)))
        runCurrent()

        events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1
        job.cancel()
    }

    @Test
    fun `disabling the toggle cancels the notification`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<ChargedReaction.Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.9f)))
        val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
        runCurrent()
        events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1

        profilesFlow.value = listOf(profile(notify = false))
        runCurrent()

        events.filterIsInstance<ChargedReaction.Event.CancelNotification>().size shouldBe 1
        job.cancel()
    }

    @Test
    fun `scope change resets a fired session and cancels`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<ChargedReaction.Event>()
        profilesFlow.value = listOf(profile(scope = ChargedSlotScope.PODS_AND_CASE))
        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.9f)))
        val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
        runCurrent()
        events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1

        // Switch to CASE scope: the device has no case slot, so after the reset nothing
        // qualifies and the notification must come down (and not re-fire).
        profilesFlow.value = listOf(profile(scope = ChargedSlotScope.CASE))
        runCurrent()

        events.filterIsInstance<ChargedReaction.Event.CancelNotification>().size shouldBe 1
        // No re-fire under the new scope (device has no case slot).
        events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1
        job.cancel()
    }

    @Test
    fun `deleted profile cancels its notification`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<ChargedReaction.Event>()
        profilesFlow.value = listOf(profile())
        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.9f)))
        val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
        runCurrent()
        events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1

        profilesFlow.value = emptyList()
        runCurrent()

        events.filterIsInstance<ChargedReaction.Event.CancelNotification>().size shouldBe 1
        job.cancel()
    }

    @Test
    fun `caseless model normalizes a stored CASE scope to pods and still fires`() =
        runTest(UnconfinedTestDispatcher()) {
            val events = mutableListOf<ChargedReaction.Event>()
            // AirPods Max has no case; a restored CASE scope must degrade to PODS or the
            // headset slot would be filtered out and the session could never complete.
            profilesFlow.value = listOf(
                profile(model = PodModel.AIRPODS_MAX, scope = ChargedSlotScope.CASE),
            )
            devicesFlow.value = listOf(
                device(
                    model = PodModel.AIRPODS_MAX,
                    aap = aap(AapPodState.BatteryType.SINGLE to (0.9f to AapPodState.ChargingState.CHARGING)),
                ),
            )
            val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
            runCurrent()

            events.filterIsInstance<ChargedReaction.Event.ShowNotification>().size shouldBe 1
            job.cancel()
        }

    @Test
    fun `non-enabled profiles never emit`() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<ChargedReaction.Event>()
        profilesFlow.value = listOf(profile(notify = false))
        devicesFlow.value = listOf(device(aap = bothPodsCharging(0.9f)))
        val job = reaction().monitor().onEach { events.add(it) }.launchIn(this)
        runCurrent()

        events.size shouldBe 0
        job.cancel()
    }
}
