package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.apple.history.KnownDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload

interface ApplePodsFactory {
    fun isResponsible(message: ProximityMessage): Boolean
    fun create(scanResult: BleScanResult, payload: ProximityPayload, meta: ApplePods.AppleMeta): ApplePods

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
        val definitive = setOf(
            DualApplePods.LidState.OPEN,
            DualApplePods.LidState.CLOSED,
            DualApplePods.LidState.NOT_IN_CASE,
        )
        if (definitive.contains(basic.caseLidState)) return basic.caseLidState

        return history
            .takeLast(2)
            .filterIsInstance<DualApplePods>()
            .lastOrNull { it.caseLidState != DualApplePods.LidState.UNKNOWN }
            ?.caseLidState
            ?: DualApplePods.LidState.NOT_IN_CASE
    }
}