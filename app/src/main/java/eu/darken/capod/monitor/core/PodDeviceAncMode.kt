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

/**
 * The mode to display. Normally whatever the device reported.
 *
 * AirPods Pro 3 have been observed answering a listening mode write with a mode they cannot
 * actually be in - reporting OFF (wire 0x01) while audibly switching to Adaptive, on a device
 * where OFF is outside the cycle and Allow Off is disabled. Adopting that verbatim shows the
 * wrong mode as selected. While our own request is still outstanding, a reported mode that the
 * device should not be able to reach is treated as noise and the requested mode is shown instead.
 *
 * Once the request is resolved (confirmed or rejected) [pendingMode] is null and the reported
 * value is shown again - out-of-cycle it will simply not match any selectable entry.
 */
fun effectiveAncMode(
    reportedMode: AapSetting.AncMode.Value,
    pendingMode: AapSetting.AncMode.Value?,
    cycleMask: Int?,
    allowOffEnabled: Boolean,
): AapSetting.AncMode.Value = when {
    pendingMode == null -> reportedMode
    reportedMode == pendingMode -> reportedMode
    isAncModePermitted(reportedMode, cycleMask, allowOffEnabled) -> reportedMode
    else -> pendingMode
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

/** Display-facing listening mode. See [effectiveAncMode]. */
val PodDevice.effectiveAncMode: AapSetting.AncMode.Value?
    get() {
        val ancMode = ancMode ?: return null
        return effectiveAncMode(
            reportedMode = ancMode.current,
            pendingMode = pendingAncMode,
            cycleMask = resolvedAncCycleMask,
            allowOffEnabled = resolvedAllowOffEnabled,
        )
    }
