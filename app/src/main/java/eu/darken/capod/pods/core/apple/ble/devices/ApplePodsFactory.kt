package eu.darken.capod.pods.core.apple.ble.devices

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.apple.ble.history.KnownDevice
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload

interface ApplePodsFactory {
    fun isResponsible(message: ProximityMessage): Boolean
    suspend fun create(scanResult: BleScanResult, payload: ProximityPayload, meta: ApplePods.AppleMeta): ApplePods

    data class ModelInfo(
        val full: UShort,
        val dirty: UByte,
    )

    fun ProximityMessage.getModelInfo(): ModelInfo = ModelInfo(
        full = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
        dirty = data[1]
    )

    fun KnownDevice.getLatestCaseBattery(): Float? = this.lastCaseBattery

    fun KnownDevice.getLatestCaseLidState(basic: DualApplePods): DualApplePods.LidState? {
        // A pod broadcasting from inside the case has authoritative case state
        if (basic.hasCaseContext && basic.caseLidState in setOf(
                DualApplePods.LidState.OPEN,
                DualApplePods.LidState.CLOSED,
            )
        ) {
            return basic.caseLidState
        }

        // Current pod lacks case context (e.g. pod on desk) or reports UNKNOWN.
        // Check recent history for a sibling broadcast that has case context.
        val fromCaseContext = history
            .takeLast(4)
            .filterIsInstance<DualApplePods>()
            .lastOrNull { it.hasCaseContext && it.caseLidState != DualApplePods.LidState.UNKNOWN }
            ?.caseLidState

        if (fromCaseContext != null) return fromCaseContext

        // No case-context broadcast in recent history — current value is best we have
        if (basic.caseLidState != DualApplePods.LidState.UNKNOWN) return basic.caseLidState

        // Last resort: any non-UNKNOWN from history
        return history
            .takeLast(2)
            .filterIsInstance<DualApplePods>()
            .lastOrNull { it.caseLidState != DualApplePods.LidState.UNKNOWN }
            ?.caseLidState
            ?: DualApplePods.LidState.NOT_IN_CASE
    }
}