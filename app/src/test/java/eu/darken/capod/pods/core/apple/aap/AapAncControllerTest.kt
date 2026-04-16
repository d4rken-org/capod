package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class AapAncControllerTest : BaseTest() {

    private val controller = AapAncController()
    private val now = Instant.ofEpochMilli(1000L)
    private val supportedModes = listOf(
        AapSetting.AncMode.Value.OFF,
        AapSetting.AncMode.Value.ON,
        AapSetting.AncMode.Value.ADAPTIVE,
    )

    private fun podStateWith(vararg settings: Pair<kotlin.reflect.KClass<out AapSetting>, AapSetting>): AapPodState =
        AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = settings.toMap(),
        )

    @Test
    fun `first in-ear OFF applies immediately and schedules AllowOff inference`() {
        val podState = podStateWith(
            AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ),
        )

        val decision = controller.onAncSetting(
            podState = podState,
            runtimeState = AncRuntimeState(),
            key = AapSetting.AncMode::class,
            value = AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ),
            isRecentAncSend = false,
            now = now,
        )

        decision.podState.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.OFF
        decision.runtimeState.pendingDebouncedAnc.shouldBeNull()
        decision.timerActions shouldBe listOf(
            EngineTimerAction.Start(EngineTimerKey.AllowOffInference, 1500L),
            EngineTimerAction.Cancel(EngineTimerKey.AncDebounce),
        )
    }

    @Test
    fun `in-case OFF does not schedule AllowOff inference`() {
        val podState = podStateWith(
            AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
            ),
        )

        val decision = controller.onAncSetting(
            podState = podState,
            runtimeState = AncRuntimeState(),
            key = AapSetting.AncMode::class,
            value = AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ),
            isRecentAncSend = false,
            now = now,
        )

        decision.timerActions shouldBe listOf(
            EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference),
            EngineTimerAction.Cancel(EngineTimerKey.AncDebounce),
        )
    }

    @Test
    fun `unsolicited ANC change is deferred through debounce state`() {
        val podState = podStateWith(
            AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ),
            AapSetting.AncMode::class to AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ON,
                supported = supportedModes,
            ),
        )

        val decision = controller.onAncSetting(
            podState = podState,
            runtimeState = AncRuntimeState(),
            key = AapSetting.AncMode::class,
            value = AapSetting.AncMode(
                current = AapSetting.AncMode.Value.ADAPTIVE,
                supported = supportedModes,
            ),
            isRecentAncSend = false,
            now = now,
        )

        decision.podState.setting<AapSetting.AncMode>()!!.current shouldBe AapSetting.AncMode.Value.ON
        decision.runtimeState.pendingDebouncedAnc!!.value.current shouldBe AapSetting.AncMode.Value.ADAPTIVE
        decision.timerActions shouldBe listOf(
            EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference),
            EngineTimerAction.Start(EngineTimerKey.AncDebounce, 1500L),
        )
    }

    @Test
    fun `AllowOff timer infers true from stable in-ear OFF`() {
        val podState = podStateWith(
            AapSetting.EarDetection::class to AapSetting.EarDetection(
                primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
            ),
            AapSetting.AncMode::class to AapSetting.AncMode(
                current = AapSetting.AncMode.Value.OFF,
                supported = supportedModes,
            ),
        )

        val decision = controller.onAllowOffInferenceTimerFired(
            podState = podState,
            runtimeState = AncRuntimeState(
                latestObservedAncMode = AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.OFF,
                    supported = supportedModes,
                ),
            ),
        )

        decision.podState.setting<AapSetting.AllowOffOption>()!!.enabled shouldBe true
    }

    @Test
    fun `OFF rejection forces AllowOff false and cancels inference timer`() {
        val podState = podStateWith(
            AapSetting.AllowOffOption::class to AapSetting.AllowOffOption(enabled = true),
        )

        val decision = controller.onOffRejected(podState, AncRuntimeState())

        decision.podState.setting<AapSetting.AllowOffOption>()!!.enabled shouldBe false
        decision.timerActions shouldBe listOf(EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference))
    }
}
