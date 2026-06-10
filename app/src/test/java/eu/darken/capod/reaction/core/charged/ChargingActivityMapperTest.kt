package eu.darken.capod.reaction.core.charged

import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Slot
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ChargingActivityMapperTest : BaseTest() {

    private fun aapWith(
        primary: AapSetting.EarDetection.PodPlacement,
        secondary: AapSetting.EarDetection.PodPlacement,
        primaryPod: AapSetting.PrimaryPod.Pod = AapSetting.PrimaryPod.Pod.LEFT,
    ) = AapPodState(
        connectionState = AapPodState.ConnectionState.READY,
        settings = mapOf(
            AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = primary,
                secondaryPod = secondary,
            ),
            AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(primaryPod),
        ),
    )

    @Test
    fun `BLE-only in-ear is ignored as phantom for in-case pods`() {
        // Pods report in-ear over BLE but there's no AAP session — the normal in-case charging
        // state. Worn must be unknown so phantom/flapping BLE bits can't dismiss the notification.
        val device = PodDevice(
            profileId = "p",
            ble = MockPodDataProvider.airPodsGen1Wearing(),
            aap = null,
        )

        device.chargingActivity().wornSlots shouldBe null
    }

    @Test
    fun `AAP ear detection is trusted`() {
        val device = PodDevice(
            profileId = "p",
            ble = MockPodDataProvider.airPodsGen1Wearing(),
            aap = aapWith(
                primary = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondary = AapSetting.EarDetection.PodPlacement.IN_CASE,
            ),
        )

        // Both pods worn over BLE, but AAP says only the primary (LEFT) is in-ear — AAP wins.
        device.chargingActivity().wornSlots shouldBe setOf(Slot.LEFT)
    }

    @Test
    fun `AAP reporting nothing worn yields an empty set, not null`() {
        val device = PodDevice(
            profileId = "p",
            ble = MockPodDataProvider.airPodsGen1Wearing(),
            aap = aapWith(
                primary = AapSetting.EarDetection.PodPlacement.IN_CASE,
                secondary = AapSetting.EarDetection.PodPlacement.IN_CASE,
            ),
        )

        device.chargingActivity().wornSlots shouldBe emptySet<Slot>()
    }

    private fun chargingAap(vararg slots: AapPodState.BatteryType) = AapPodState(
        connectionState = AapPodState.ConnectionState.READY,
        batteries = slots.associateWith {
            AapPodState.Battery(it, 0.9f, AapPodState.ChargingState.CHARGING)
        },
    )

    @Test
    fun `PODS scope excludes the case slot`() {
        val device = PodDevice(
            profileId = "p",
            ble = null,
            aap = chargingAap(
                AapPodState.BatteryType.LEFT,
                AapPodState.BatteryType.RIGHT,
                AapPodState.BatteryType.CASE,
            ),
        )

        device.liveChargingSlots(ChargedSlotScope.PODS).keys shouldBe setOf(Slot.LEFT, Slot.RIGHT)
    }

    @Test
    fun `CASE scope keeps only the case slot`() {
        val device = PodDevice(
            profileId = "p",
            ble = null,
            aap = chargingAap(
                AapPodState.BatteryType.LEFT,
                AapPodState.BatteryType.RIGHT,
                AapPodState.BatteryType.CASE,
            ),
        )

        device.liveChargingSlots(ChargedSlotScope.CASE).keys shouldBe setOf(Slot.CASE)
    }

    @Test
    fun `PODS_AND_CASE scope keeps every charging slot`() {
        val device = PodDevice(
            profileId = "p",
            ble = null,
            aap = chargingAap(
                AapPodState.BatteryType.LEFT,
                AapPodState.BatteryType.RIGHT,
                AapPodState.BatteryType.CASE,
            ),
        )

        device.liveChargingSlots(ChargedSlotScope.PODS_AND_CASE).keys shouldBe
            setOf(Slot.LEFT, Slot.RIGHT, Slot.CASE)
    }

    @Test
    fun `headset counts as a pod slot under PODS scope`() {
        val device = PodDevice(
            profileId = "p",
            ble = null,
            aap = chargingAap(AapPodState.BatteryType.SINGLE),
        )

        device.liveChargingSlots(ChargedSlotScope.PODS).keys shouldBe setOf(Slot.HEADSET)
        // ...and CASE scope yields nothing for a headset device.
        device.liveChargingSlots(ChargedSlotScope.CASE).keys shouldBe emptySet<Slot>()
    }
}
