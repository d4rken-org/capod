package eu.darken.capod.pods.core.apple.ble.devices

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.apple.ble.history.KnownDevice
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import java.time.Duration

/**
 * How far back to look when recovering the lid state from a recent in-case-pod broadcast.
 * Time-bounded rather than count-bounded: BLE scan batching means a fixed number of frames is not a
 * stable time window, and an old reading must not be allowed to resurrect a stale OPEN/CLOSED.
 */
private val MAX_LID_RECOVERY_AGE: Duration = Duration.ofSeconds(2)

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
        // A pod broadcasting from inside the case has authoritative case state. Out-of-case frames
        // (one pod removed) report UNKNOWN via DualApplePods.caseLidState and are skipped here.
        if (basic.hasCaseContext && basic.caseLidState in setOf(
                DualApplePods.LidState.OPEN,
                DualApplePods.LidState.CLOSED,
            )
        ) {
            return basic.caseLidState
        }

        // Current pod lacks case context (e.g. pod on desk) or reports UNKNOWN (out-of-case pod's
        // stale lid byte). Recover the last authoritative reading from a recent in-case broadcast,
        // bounded by time so a missed CLOSED can't keep a stale OPEN alive across scan gaps.
        val recentHistory = history
            .filterIsInstance<DualApplePods>()
            .filter { Duration.between(it.seenLastAt, basic.seenLastAt).abs() <= MAX_LID_RECOVERY_AGE }

        val fromCaseContext = recentHistory
            .lastOrNull { it.hasCaseContext && it.caseLidState != DualApplePods.LidState.UNKNOWN }
            ?.caseLidState

        if (fromCaseContext != null) return fromCaseContext

        // No case-context broadcast in recent history — current value is best we have
        if (basic.caseLidState != DualApplePods.LidState.UNKNOWN) return basic.caseLidState

        // Last resort: any non-UNKNOWN from recent history
        return recentHistory
            .lastOrNull { it.caseLidState != DualApplePods.LidState.UNKNOWN }
            ?.caseLidState
            ?: DualApplePods.LidState.NOT_IN_CASE
    }
}