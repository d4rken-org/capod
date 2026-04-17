package eu.darken.capod.monitor.core.aap

import eu.darken.capod.monitor.core.resolvedAncCycleMask
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile

data class EffectiveAncState(
    val current: AapSetting.AncMode.Value,
    val supported: List<AapSetting.AncMode.Value>,
    val cycleMask: Int?,
    val allowOffEnabled: Boolean,
)

fun resolveEffectiveAncState(
    aapState: AapPodState?,
    profile: AppleDeviceProfile,
): EffectiveAncState? {
    val anc = aapState?.setting<AapSetting.AncMode>() ?: return null
    val current = aapState.pendingAncMode ?: anc.current
    val mask = aapState.setting<AapSetting.ListeningModeCycle>()?.modeMask
        ?: profile.lastRequestedListeningModeCycleMask
        ?: resolvedAncCycleMask(profile.model.features.hasListeningModeCycle, null)
    val allowOff = aapState.setting<AapSetting.AllowOffOption>()?.enabled
        ?: profile.learnedAllowOffEnabled
        ?: true
    return EffectiveAncState(
        current = current,
        supported = anc.supported,
        cycleMask = mask,
        allowOffEnabled = allowOff,
    )
}

private fun AapSetting.AncMode.Value.cycleBit(): Int = when (this) {
    AapSetting.AncMode.Value.OFF -> 0x01
    AapSetting.AncMode.Value.ON -> 0x02
    AapSetting.AncMode.Value.TRANSPARENCY -> 0x04
    AapSetting.AncMode.Value.ADAPTIVE -> 0x08
}

fun nextGestureAncMode(state: EffectiveAncState): AapSetting.AncMode.Value? {
    val mask = state.cycleMask ?: return null
    val cycleModes = state.supported.filter { mode ->
        val bit = mode.cycleBit()
        (mask and bit) != 0 && (mode != AapSetting.AncMode.Value.OFF || state.allowOffEnabled)
    }
    if (cycleModes.size < 2) return null
    val idx = cycleModes.indexOf(state.current)
    return if (idx < 0) cycleModes.first() else cycleModes[(idx + 1) % cycleModes.size]
}
