package eu.darken.capod.pods.core.apple.aap.engine

import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import java.time.Instant
import kotlin.reflect.KClass

internal data class AncRuntimeState(
    val latestObservedAncMode: AapSetting.AncMode? = null,
    val pendingDebouncedAnc: PendingDebouncedAnc? = null,
    /**
     * Set when the device reported OFF while we had a request for a different mode outstanding.
     *
     * AirPods Pro 3 have been seen answering a listening mode write with OFF while actually
     * switching to the requested mode. Such a report must not be fed to the Allow Off inference,
     * or a single glitch permanently teaches CAPod that OFF is a permitted mode. Cleared as soon
     * as any non-OFF mode is reported, so an unsolicited switch into OFF (stem press, another
     * phone) still trains the inference normally.
     */
    val offReportContradicted: Boolean = false,
)

internal data class PendingDebouncedAnc(
    val key: KClass<out AapSetting>,
    val value: AapSetting.AncMode,
    val previous: AapSetting?,
)

internal data class AncDecision(
    val podState: AapPodState,
    val runtimeState: AncRuntimeState,
    val timerActions: List<EngineTimerAction> = emptyList(),
    val logs: List<String> = emptyList(),
)

internal class AapAncController {

    fun onAncSetting(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
        key: KClass<out AapSetting>,
        value: AapSetting.AncMode,
        isRecentAncSend: Boolean,
        now: Instant,
    ): AncDecision {
        val previous = podState.settings[key]
        val pendingMode = podState.pendingAncMode
        // An OFF report with no competing request of our own is taken at face value, which both
        // clears any earlier contradiction and keeps the Allow Off self-heal working after a
        // glitch (stem press / another phone switching the pods into OFF for real).
        val contradicted = value.current == AapSetting.AncMode.Value.OFF &&
                pendingMode != null &&
                pendingMode != AapSetting.AncMode.Value.OFF
        val updatedRuntime = runtimeState.copy(
            latestObservedAncMode = value,
            offReportContradicted = contradicted,
        )
        val timerActions = mutableListOf<EngineTimerAction>()
        timerActions.plusAssign(planAllowOffInferenceTimer(podState, updatedRuntime))

        val isFirstAncMode = podState.setting<AapSetting.AncMode>() == null
        return if (isFirstAncMode || isRecentAncSend) {
            timerActions.plusAssign(EngineTimerAction.Cancel(EngineTimerKey.AncDebounce))
            AncDecision(
                podState = applyAncSetting(podState, key, value, now, isRecentAncSend),
                runtimeState = updatedRuntime.copy(pendingDebouncedAnc = null),
                timerActions = timerActions,
                logs = listOf("Setting: ${key.simpleName} = $value [was: $previous]"),
            )
        } else {
            timerActions.plusAssign(EngineTimerAction.Start(EngineTimerKey.AncDebounce, 1500L))
            AncDecision(
                podState = podState,
                runtimeState = updatedRuntime.copy(
                    pendingDebouncedAnc = PendingDebouncedAnc(key = key, value = value, previous = previous),
                ),
                timerActions = timerActions,
            )
        }
    }

    fun onEarDetectionUpdated(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
    ): AncDecision = AncDecision(
        podState = podState,
        runtimeState = runtimeState,
        timerActions = planAllowOffInferenceTimer(podState, runtimeState),
    )

    fun onAncDebounceTimerFired(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
        now: Instant,
        isRecentAncSend: Boolean,
    ): AncDecision {
        val pending = runtimeState.pendingDebouncedAnc ?: return AncDecision(podState, runtimeState)
        return AncDecision(
            podState = applyAncSetting(podState, pending.key, pending.value, now, isRecentAncSend),
            runtimeState = runtimeState.copy(pendingDebouncedAnc = null),
            logs = listOf("Setting: ${pending.key.simpleName} = ${pending.value} [was: ${pending.previous}]"),
        )
    }

    fun onAllowOffInferenceTimerFired(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
    ): AncDecision {
        val latestAncMode = runtimeState.latestObservedAncMode ?: podState.setting<AapSetting.AncMode>()
        val latestEarDetection = podState.setting<AapSetting.EarDetection>()
        val latestAllowOffOption = podState.setting<AapSetting.AllowOffOption>()
        if (latestAncMode?.current == AapSetting.AncMode.Value.OFF &&
            !runtimeState.offReportContradicted &&
            latestEarDetection?.isEitherPodInEar == true &&
            latestAllowOffOption?.enabled != true
        ) {
            return AncDecision(
                podState = podState.withSetting(
                    AapSetting.AllowOffOption::class,
                    AapSetting.AllowOffOption(enabled = true),
                ),
                runtimeState = runtimeState,
                logs = listOf(
                    "Inferred: AllowOffOption = AllowOffOption(enabled=true) (from stable in-ear AncMode=OFF)",
                ),
            )
        }
        return AncDecision(podState, runtimeState)
    }

    fun onOffRejected(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
    ): AncDecision {
        val prev = podState.setting<AapSetting.AllowOffOption>()
        if (prev == null || prev.enabled) {
            return AncDecision(
                podState = podState.withSetting(
                    AapSetting.AllowOffOption::class,
                    AapSetting.AllowOffOption(enabled = false),
                ),
                runtimeState = runtimeState,
                timerActions = listOf(EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference)),
                logs = listOf("Inferred: AllowOffOption = false (OFF mode rejected by device)"),
            )
        }
        return AncDecision(
            podState = podState,
            runtimeState = runtimeState,
            timerActions = listOf(EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference)),
        )
    }

    private fun applyAncSetting(
        podState: AapPodState,
        key: KClass<out AapSetting>,
        value: AapSetting.AncMode,
        now: Instant,
        isRecentAncSend: Boolean,
    ): AapPodState {
        val updated = podState.withSetting(key, value).copy(lastMessageAt = now)
        return if (isRecentAncSend && updated.pendingAncMode == value.current) {
            updated.copy(pendingAncMode = null)
        } else {
            updated
        }
    }

    private fun planAllowOffInferenceTimer(
        podState: AapPodState,
        runtimeState: AncRuntimeState,
    ): List<EngineTimerAction> {
        val observedAncMode = runtimeState.latestObservedAncMode ?: podState.setting<AapSetting.AncMode>()
        val earDetection = podState.setting<AapSetting.EarDetection>()
        val allowOffOption = podState.setting<AapSetting.AllowOffOption>()
        return if (observedAncMode?.current == AapSetting.AncMode.Value.OFF &&
            !runtimeState.offReportContradicted &&
            earDetection?.isEitherPodInEar == true &&
            allowOffOption?.enabled != true
        ) {
            listOf(EngineTimerAction.Start(EngineTimerKey.AllowOffInference, 1500L))
        } else {
            listOf(EngineTimerAction.Cancel(EngineTimerKey.AllowOffInference))
        }
    }
}
