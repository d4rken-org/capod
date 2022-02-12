package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Duration
import java.time.Instant

abstract class ApplePodsFactory<PodType : ApplePods>(private val tag: String) {

    data class Markings(
        val vendor: UByte,
        val length: UByte,
        val device: UShort,
        val podBatteryData: Set<UShort>,
        val caseBatteryData: UShort,
        val deviceColor: UByte,
    )

    fun ProximityPairing.Message.getApplePodsMarkings(): Markings = Markings(
        vendor = ProximityPairing.CONTINUITY_PROTOCOL_MESSAGE_TYPE_PROXIMITY_PAIRING,
        length = ProximityPairing.PAIRING_MESSAGE_LENGTH.toUByte(),
        device = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
        // Make comparison order independent
        podBatteryData = setOf(data[4].upperNibble, data[4].lowerNibble),
        caseBatteryData = data[5].lowerNibble,
        deviceColor = data[7]
    )

    data class KnownDevice(
        val id: PodDevice.Id,
        val seenFirstAt: Instant,
        val seenCounter: Int,
        val history: List<ApplePods>
    ) {
        val lastMessage: ProximityPairing.Message
            get() = history.last().proximityMessage

        val lastAddress: String
            get() = history.last().address

        val confidence: Float
            get() = 0.75f + (history.size / (MAX_HISTORY * 4f))

        fun averageRssi(latest: Int): Int =
            history.map { it.rssi }.plus(latest).takeLast(10).median()

        fun isOlderThan(age: Duration): Boolean {
            val now = Instant.now()
            return Duration.between(history.last().seenLastAt, now) > age
        }

        private fun List<Int>.median(): Int = this.sorted().let {
            if (it.size % 2 == 0)
                (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
            else
                it[it.size / 2]
        }

        override fun toString(): String = "KnownDevice(history=${history.size}, last=${history.last()})"

        companion object {
            const val MAX_HISTORY = 10
        }
    }

    internal val knownDevices = mutableMapOf<PodDevice.Id, KnownDevice>()

    internal open fun searchHistory(current: PodType): KnownDevice? {
        val scanResult = current.scanResult
        val message = current.proximityMessage

        knownDevices.values.toList().forEach { knownDevice ->
            if (knownDevice.isOlderThan(Duration.ofSeconds(20))) {
                log(tag, VERBOSE) { "searchHistory1: Removing stale known device: $knownDevice" }
                knownDevices.remove(knownDevice.id)
            }
        }

        knownDevices.values
            .filter { it.history.size > KnownDevice.MAX_HISTORY }
            .toList()
            .forEach {
                knownDevices[it.id] = it.copy(history = it.history.takeLast(KnownDevice.MAX_HISTORY))
            }

        var recognizedDevice: KnownDevice? = knownDevices.values
            .firstOrNull { it.lastAddress == scanResult.address }
            ?.also { log(tag, VERBOSE) { "searchHistory1: Recovered previous ID via address: $it" } }

        if (recognizedDevice == null) {
            val currentMarkers = message.getApplePodsMarkings()
            recognizedDevice = knownDevices.values
                .firstOrNull { it.lastMessage.getApplePodsMarkings() == currentMarkers }
                ?.also { log(tag) { "searchHistory1: Close match based on similarity: $currentMarkers" } }
        }

        if (recognizedDevice == null) {
            log(tag) { "searchHistory1: Didn't recognize: $message" }
        }

        return recognizedDevice
    }

    fun updateHistory(device: PodType) {
        val existing = knownDevices[device.identifier]

        knownDevices[device.identifier] = when {
            existing != null -> {
                existing.copy(
                    seenCounter = existing.seenCounter + 1,
                    history = existing.history.plus(device)
                )
            }
            else -> {
                log(tag) { "searchHistory1: Creating new history for $device" }
                KnownDevice(
                    id = device.identifier,
                    seenFirstAt = device.seenFirstAt,
                    seenCounter = 1,
                    history = listOf(device)
                )
            }
        }
    }

    data class ModelInfo(
        val full: UShort,
        val dirty: UByte,
    )

    fun ProximityPairing.Message.getModelInfo(): ModelInfo = ModelInfo(
        full = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
        dirty = data[1]
    )

    abstract fun isResponsible(message: ProximityPairing.Message): Boolean

    abstract fun create(
        scanResult: BleScanResult,
        message: ProximityPairing.Message,
    ): ApplePods
}