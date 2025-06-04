package eu.darken.capod.pods.core.apple

import android.R.id.message
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.collections.median
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Duration
import java.time.Instant
import kotlin.math.max

abstract class ApplePodsFactory<PodType : ApplePods>(
    private val tag: String,
) {

    data class Identifier(
        val device: UShort,
        val podBatteryData: Set<UShort>,
        val caseBatteryData: UShort,
        val deviceColor: UByte,
    )

    private fun ProximityPayload.getFuzzyIdentifier(): Identifier = Identifier(
        device = (((public.data[1].toInt() and 255) shl 8) or (public.data[2].toInt() and 255)).toUShort(),
        // Make comparison order independent
        podBatteryData = setOf(public.data[4].upperNibble, public.data[4].lowerNibble),
        caseBatteryData = public.data[5].lowerNibble,
        deviceColor = public.data[7]
    )

    data class KnownDevice(
        val id: PodDevice.Id,
        val seenFirstAt: Instant,
        val seenCounter: Int,
        val history: List<ApplePods>,
        val lastCaseBattery: Float?,
    ) {
        val lastPayload: ProximityPayload
            get() = history.last().payload

        val lastAddress: BluetoothAddress
            get() = history.last().address

        val reliability: Float
            get() {
                if (history.size < 2) return 0f

                val now = Instant.now()

                val pingInterval = List(history.dropLast(1).size) { index ->
                    Duration.between(history[index].seenLastAt, history[index + 1].seenLastAt).toMillis()
                }.map { it.toInt() }.median()

                val expectedPings: Float = LOOKBACK.toMillis() / pingInterval.toFloat()

                val recentPings = history.filter {
                    Duration.between(it.seenLastAt, now).toMillis() < LOOKBACK.toMillis() + pingInterval
                }.size

                val reliability: Float = (recentPings / expectedPings)
                // log("#####") { "interval=$pingInterval, expectedPerMinute=$expectedPings, count=$recentPings" }

                return max(0.0f, reliability).coerceAtMost(1f)
            }

        fun rssiSmoothed(latest: Int): Int {
            val now = Instant.now()
            return history
                .filter { Duration.between(it.seenLastAt, now) < LOOKBACK }
                .map { it.rssi }
                .plus(latest)
                .median()
        }

        fun isOlderThan(age: Duration): Boolean {
            val now = Instant.now()
            return Duration.between(history.last().seenLastAt, now) > age
        }

        override fun toString(): String = "KnownDevice(history=${history.size}, last=${history.last()})"

        companion object {
            // 30 seconds at fastest interval
            const val MAX_HISTORY = 60
            val LOOKBACK = Duration.ofSeconds(30)
        }
    }

    internal val knownDevices = mutableMapOf<PodDevice.Id, KnownDevice>()

    fun KnownDevice.getLatestCaseBattery(): Float? = this.lastCaseBattery

    private fun Collection<ApplePods>.determineLatestCaseBattery(): Float? = this
        .filterIsInstance<HasCase>()
        .mapNotNull { it.batteryCasePercent }
        .lastOrNull()

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

    open fun historyTrimmer(
        pods: List<ApplePods>
    ): List<ApplePods> {
        return pods.takeLast(KnownDevice.MAX_HISTORY)
    }

    internal open fun searchHistory(current: PodType): KnownDevice? {
        val scanResult = current.scanResult
        val payload = current.payload

        knownDevices.values.toList().forEach { knownDevice ->
            if (knownDevice.isOlderThan(Duration.ofSeconds(30))) {
                log(tag, VERBOSE) { "searchHistory1: Removing stale known device: $knownDevice" }
                knownDevices.remove(knownDevice.id)
            }
        }

        knownDevices.values
            .filter { it.history.size > KnownDevice.MAX_HISTORY }
            .toList()
            .forEach {
                knownDevices[it.id] = it.copy(history = historyTrimmer(it.history))
            }

        var recognizedDevice: KnownDevice? = knownDevices.values
            .firstOrNull { it.lastAddress == scanResult.address }
            ?.also { log(tag, VERBOSE) { "searchHistory1: Recovered previous ID via address: $it" } }

//        if()

        if (recognizedDevice == null) {
            val currentMarkers = payload.getFuzzyIdentifier()
            recognizedDevice = knownDevices.values
                .firstOrNull { it.lastPayload.getFuzzyIdentifier() == currentMarkers }
                ?.also { log(tag) { "searchHistory1: Close match based on similarity: $currentMarkers" } }
        }

        if (recognizedDevice == null) {
            log(tag, WARN) { "searchHistory1: Didn't recognize: $message" }
        }

        return recognizedDevice
    }

    fun updateHistory(device: PodType) {
        val existing = knownDevices[device.identifier]

        knownDevices[device.identifier] = when {
            existing != null -> {
                val history = existing.history.plus(device)
                existing.copy(
                    seenCounter = existing.seenCounter + 1,
                    history = history,
                    lastCaseBattery = history.determineLatestCaseBattery() ?: existing.lastCaseBattery
                )
            }

            else -> {
                log(tag) { "searchHistory1: Creating new history for $device" }
                val history = listOf(device)
                KnownDevice(
                    id = device.identifier,
                    seenFirstAt = device.seenFirstAt,
                    seenCounter = 1,
                    history = history,
                    lastCaseBattery = history.determineLatestCaseBattery()
                )
            }
        }
    }

    data class ModelInfo(
        val full: UShort,
        val dirty: UByte,
    )

    fun ProximityMessage.getModelInfo(): ModelInfo = ModelInfo(
        full = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
        dirty = data[1]
    )

    abstract fun isResponsible(message: ProximityMessage): Boolean

    abstract fun create(
        scanResult: BleScanResult,
        payload: ProximityPayload,
    ): ApplePods
}