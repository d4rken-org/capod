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

fun visibleAncModes(
    supportedModes: List<AapSetting.AncMode.Value>,
    currentMode: AapSetting.AncMode.Value,
    cycleMask: Int?,
    allowOffEnabled: Boolean,
): List<AapSetting.AncMode.Value> = supportedModes.filter { mode ->
    val inCycle = if (cycleMask != null) {
        (cycleMask and mode.cycleBit()) != 0
    } else {
        true
    }
    inCycle || (mode == AapSetting.AncMode.Value.OFF && allowOffEnabled) || mode == currentMode
}

val PodDevice.resolvedAncCycleMask: Int?
    get() = resolvedAncCycleMask(
        hasListeningModeCycle = model.features.hasListeningModeCycle,
        reportedCycleMask = listeningModeCycle?.modeMask,
    )

val PodDevice.visibleAncModes: List<AapSetting.AncMode.Value>
    get() {
        val ancMode = ancMode ?: return emptyList()
        return visibleAncModes(
            supportedModes = ancMode.supported,
            currentMode = ancMode.current,
            cycleMask = resolvedAncCycleMask,
            allowOffEnabled = allowOffOption?.enabled == true,
        )
    }
