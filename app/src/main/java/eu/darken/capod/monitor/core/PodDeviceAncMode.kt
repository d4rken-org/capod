package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

private fun AapSetting.AncMode.Value.cycleBit(): Int = when (this) {
    AapSetting.AncMode.Value.OFF -> 0x01
    AapSetting.AncMode.Value.ON -> 0x02
    AapSetting.AncMode.Value.TRANSPARENCY -> 0x04
    AapSetting.AncMode.Value.ADAPTIVE -> 0x08
}

fun resolvedAncCycleMask(
    hasListeningModeCycle: Boolean,
    reportedCycleMask: Int?,
): Int? = if (hasListeningModeCycle) {
    reportedCycleMask ?: 0x0E
} else {
    null
}

/**
 * Whether the device is expected to be able to sit in [mode] at all, based on the listening mode
 * cycle and the Allow Off option. Both of those are inferred rather than device-reported: AirPods
 * never push 0x1A/0x34, so this is a belief, not ground truth.
 */
fun isAncModePermitted(
    mode: AapSetting.AncMode.Value,
    cycleMask: Int?,
    allowOffEnabled: Boolean,
): Boolean {
    val inCycle = if (cycleMask != null) {
        (cycleMask and mode.cycleBit()) != 0
    } else {
        true
    }
    return inCycle || (mode == AapSetting.AncMode.Value.OFF && allowOffEnabled)
}

fun visibleAncModes(
    supportedModes: List<AapSetting.AncMode.Value>,
    cycleMask: Int?,
    allowOffEnabled: Boolean,
): List<AapSetting.AncMode.Value> = supportedModes.filter { mode ->
    isAncModePermitted(mode, cycleMask, allowOffEnabled)
}

val PodDevice.resolvedAncCycleMask: Int?
    get() = resolvedAncCycleMask(
        hasListeningModeCycle = model.features.hasListeningModeCycle,
        reportedCycleMask = listeningModeCycle?.modeMask,
    )

// Unknown (null) is treated as allowed so OFF is visible optimistically. Only a
// confirmed enabled=false (direct device report or inferred rejection) hides OFF.
private val PodDevice.resolvedAllowOffEnabled: Boolean
    get() = allowOffOption?.enabled != false

val PodDevice.visibleAncModes: List<AapSetting.AncMode.Value>
    get() {
        val ancMode = ancMode ?: return emptyList()
        return visibleAncModes(
            supportedModes = ancMode.supported,
            cycleMask = resolvedAncCycleMask,
            allowOffEnabled = resolvedAllowOffEnabled,
        )
    }
