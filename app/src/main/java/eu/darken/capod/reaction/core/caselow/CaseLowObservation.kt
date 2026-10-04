package eu.darken.capod.reaction.core.caselow

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import kotlin.math.roundToInt

/** Case state read from a single BLE advert. Null fields mean that advert doesn't say. */
data class CaseLowObservation(
    val bothPodsInCase: Boolean,
    /** Whole percent 0..100. */
    val casePercent: Int?,
    val caseCharging: Boolean?,
)

/** Reads only the current BLE advert; AAP state, scan history and the state cache are deliberately not used. */
internal fun PodDevice.caseLowObservation(): CaseLowObservation? {
    val frame = ble as? DualApplePods ?: return null
    val hasCaseContext = frame.hasCaseContext
    return CaseLowObservation(
        bothPodsInCase = frame.areBothPodsInCase,
        casePercent = frame.advertBatteryCasePercent
            ?.takeIf { hasCaseContext && it >= 0f }
            ?.let { (it * 100).roundToInt() },
        caseCharging = if (hasCaseContext) frame.isCaseCharging else null,
    )
}
