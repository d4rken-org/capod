package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapPodStateTest : BaseTest() {

    @Test
    fun `setting lookup returns typed setting`() {
        val state = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AapSetting.AncMode.Value.ON,
                    listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY),
                ),
            )
        )
        val anc = state.setting<AapSetting.AncMode>()
        anc.shouldNotBeNull()
        anc.current shouldBe AapSetting.AncMode.Value.ON
    }

    @Test
    fun `setting lookup returns null for missing type`() {
        val state = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        state.setting<AapSetting.AncMode>().shouldBeNull()
    }

    @Test
    fun `withSetting merges into existing settings`() {
        val state = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AapSetting.AncMode.Value.ON,
                    listOf(AapSetting.AncMode.Value.ON)
                ),
            )
        )
        val updated = state.withSetting(
            AapSetting.ConversationalAwareness::class,
            AapSetting.ConversationalAwareness(true),
        )
        updated.setting<AapSetting.AncMode>().shouldNotBeNull()
        updated.setting<AapSetting.ConversationalAwareness>()!!.enabled shouldBe true
    }

    @Test
    fun `withSetting replaces existing setting of same type`() {
        val state = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AapSetting.AncMode.Value.ON,
                    listOf(AapSetting.AncMode.Value.ON)
                ),
            )
        )
        val updated = state.withSetting(
            AapSetting.AncMode::class,
            AapSetting.AncMode(AapSetting.AncMode.Value.TRANSPARENCY, listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY)),
        )
        updated.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
        updated.settings.size shouldBe 1
    }

    @Test
    fun `default state is disconnected with no data`() {
        val state = AapPodState()
        state.connectionState shouldBe AapPodState.ConnectionState.DISCONNECTED
        state.deviceInfo.shouldBeNull()
        state.settings shouldBe emptyMap()
        state.batteries shouldBe emptyMap()
        state.lastMessageAt.shouldBeNull()
    }

    @Test
    fun `battery accessors map to correct types`() {
        val state = AapPodState(
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(
                    AapPodState.BatteryType.LEFT,
                    0.8f,
                    AapPodState.ChargingState.NOT_CHARGING
                ),
                AapPodState.BatteryType.RIGHT to AapPodState.Battery(
                    AapPodState.BatteryType.RIGHT,
                    0.77f,
                    AapPodState.ChargingState.CHARGING
                ),
                AapPodState.BatteryType.CASE to AapPodState.Battery(
                    AapPodState.BatteryType.CASE,
                    0.48f,
                    AapPodState.ChargingState.CHARGING
                ),
            ),
        )
        state.batteryLeft shouldBe 0.8f
        state.batteryRight shouldBe 0.77f
        state.batteryCase shouldBe 0.48f
        state.batteryHeadset.shouldBeNull()
    }

    @Test
    fun `battery accessors null when type not present`() {
        val state = AapPodState()
        state.batteryLeft.shouldBeNull()
        state.batteryRight.shouldBeNull()
        state.batteryCase.shouldBeNull()
        state.batteryHeadset.shouldBeNull()
    }

    @Test
    fun `headset battery for single-pod devices`() {
        val state = AapPodState(
            batteries = mapOf(
                AapPodState.BatteryType.SINGLE to AapPodState.Battery(
                    AapPodState.BatteryType.SINGLE,
                    0.6f,
                    AapPodState.ChargingState.NOT_CHARGING
                ),
            ),
        )
        state.batteryHeadset shouldBe 0.6f
        state.batteryLeft.shouldBeNull()
    }

    @Test
    fun `isCharging true for CHARGING state`() {
        val state = AapPodState(
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(
                    AapPodState.BatteryType.LEFT,
                    0.8f,
                    AapPodState.ChargingState.CHARGING
                ),
            ),
        )
        state.isLeftCharging shouldBe true
    }

    @Test
    fun `isCharging true for CHARGING_OPTIMIZED state`() {
        val state = AapPodState(
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(
                    AapPodState.BatteryType.LEFT,
                    0.8f,
                    AapPodState.ChargingState.CHARGING_OPTIMIZED
                ),
            ),
        )
        state.isLeftCharging shouldBe true
    }

    @Test
    fun `isCharging false for NOT_CHARGING`() {
        val state = AapPodState(
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(
                    AapPodState.BatteryType.LEFT,
                    0.8f,
                    AapPodState.ChargingState.NOT_CHARGING
                ),
            ),
        )
        state.isLeftCharging shouldBe false
    }

    @Test
    fun `isCharging null when battery type absent`() {
        val state = AapPodState()
        state.isLeftCharging.shouldBeNull()
        state.isRightCharging.shouldBeNull()
        state.isCaseCharging.shouldBeNull()
        state.isHeadsetCharging.shouldBeNull()
    }

    // ── Ear Detection ───────────────────────────────────────

    @Test
    fun `ear detection accessor returns typed setting`() {
        val state = AapPodState(
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    AapSetting.EarDetection.PodPlacement.IN_EAR,
                    AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
            ),
        )
        state.aapEarDetection.shouldNotBeNull()
        state.aapEarDetection!!.primaryPod shouldBe AapSetting.EarDetection.PodPlacement.IN_EAR
    }

    @Test
    fun `ear detection accessor null when missing`() {
        val state = AapPodState()
        state.aapEarDetection.shouldBeNull()
    }

    @Test
    fun `isEitherPodInEar true when primary in ear`() {
        val state = AapPodState(
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    AapSetting.EarDetection.PodPlacement.IN_EAR,
                    AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
            ),
        )
        state.isEitherPodInEar shouldBe true
    }

    @Test
    fun `isEitherPodInEar true when secondary in ear`() {
        val state = AapPodState(
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        state.isEitherPodInEar shouldBe true
    }

    @Test
    fun `isEitherPodInEar false when neither in ear`() {
        val state = AapPodState(
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    AapSetting.EarDetection.PodPlacement.IN_CASE,
                    AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        state.isEitherPodInEar shouldBe false
    }

    @Test
    fun `isEitherPodInEar null when no ear detection`() {
        val state = AapPodState()
        state.isEitherPodInEar.shouldBeNull()
    }

    // ── Primary Pod ───────────────────────────────────────────

    @Test
    fun `primary pod accessor returns stored PrimaryPod setting`() {
        val state = AapPodState(
            settings = mapOf(
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.LEFT),
            ),
        )
        state.aapPrimaryPod.shouldNotBeNull()
        state.aapPrimaryPod!!.pod shouldBe AapSetting.PrimaryPod.Pod.LEFT
    }

    @Test
    fun `primary pod accessor null when missing`() {
        val state = AapPodState()
        state.aapPrimaryPod.shouldBeNull()
    }

    // ── Pending ANC Mode ────────────────────────────────────

    @Test
    fun `pendingAncMode defaults to null`() {
        AapPodState().pendingAncMode.shouldBeNull()
    }

    @Test
    fun `pendingAncMode preserved in copy`() {
        val state = AapPodState().copy(pendingAncMode = AapSetting.AncMode.Value.ADAPTIVE)
        state.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
    }
}
