package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.toHex
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import java.time.Duration

interface ApplePods : PodDevice {

    val proximityMessage: ProximityPairing.Message

    override val rawData: ByteArray
        get() = scanResult.getManufacturerSpecificData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER)!!

    // We start counting at the airpods prefix byte
    val rawPrefix: UByte
        get() = proximityMessage.data[0]

    val rawDeviceModel: UShort
        get() = (((proximityMessage.data[1].toInt() and 255) shl 8) or (proximityMessage.data[2].toInt() and 255)).toUShort()

    val rawStatus: UByte
        get() = proximityMessage.data[3]

    val rawStatusHex: String
        get() = rawStatus.toHex()

    val rawPodsBattery: UByte
        get() = proximityMessage.data[4]

    val rawPodsBatteryHex: String
        get() = rawPodsBattery.toHex()

    val rawFlags: UShort
        get() = proximityMessage.data[5].upperNibble

    val rawCaseBattery: UShort
        get() = proximityMessage.data[5].lowerNibble

    val rawCaseLidState: UByte
        get() = proximityMessage.data[6]

    val rawDeviceColor: UByte
        get() = proximityMessage.data[7]

    val rawSuffix: UByte
        get() = proximityMessage.data[8]


    abstract class Factory(private val tag: String) {

        internal data class ValueCache(
            val caseBatteryPercentage: Float?
        )

        internal val cachedValues = mutableMapOf<PodDevice.Id, ValueCache>()

        internal data class KnownDevice(
            val identifier: PodDevice.Id,
            val scanResult: BleScanResult,
            val message: ProximityPairing.Message,
        ) {
            val address: String
                get() = scanResult.address

            val rssi: Int
                get() = scanResult.rssi

            val timestampNanos: Duration
                get() = Duration.ofNanos(scanResult.generatedAtNanos)

            fun isOlderThan(age: Duration): Boolean {
                val now = Duration.ofNanos(SystemClockWrap.elapsedRealtimeNanos)
                return now - timestampNanos > age
            }
        }

        private val knownDevs = mutableMapOf<PodDevice.Id, KnownDevice>()

        internal fun recognizeDevice(scanResult: BleScanResult, message: ProximityPairing.Message): PodDevice.Id {
            val address = scanResult.address

            var identifier: PodDevice.Id? = null

            knownDevs.values.firstOrNull { it.address == address }?.let {
                log(tag, VERBOSE) { "recognizeDevice: Recovered previous ID via address: $it" }
                knownDevs[it.identifier] = it.copy(
                    scanResult = scanResult,
                    message = message,
                )
                identifier = it.identifier
            }

            if (identifier == null) {
                val currentMarkers = message.getRecogMarkers()
                knownDevs.values
                    .firstOrNull { it.message.getRecogMarkers() == currentMarkers }
                    ?.let {
                        log(tag, DEBUG) { "recognizeDevice: Close match based similarity markers." }
                        log(tag, DEBUG) { "recognizeDevice: Old marker: ${it.message.getRecogMarkers()}" }
                        log(tag, DEBUG) { "recognizeDevice: New marker: $currentMarkers" }
                        knownDevs[it.identifier] = it.copy(
                            scanResult = scanResult,
                            message = message,
                        )
                        identifier = it.identifier
                    }
            }

            if (identifier == null) {
                log(tag, VERBOSE) { "recognizeDevice: Mapping as new device" }
                identifier = PodDevice.Id()
            }

            knownDevs[identifier!!] = KnownDevice(
                identifier = identifier!!,
                scanResult = scanResult,
                message = message
            )

            knownDevs.values.toList().forEach { knownDevice ->
                if (knownDevice.isOlderThan(Duration.ofSeconds(20))) {
                    log(tag, VERBOSE) { "recognizeDevice: Removing stale known device: $knownDevice" }
                    knownDevs.remove(knownDevice.identifier)
                }
            }

            return identifier!!
        }

        data class ModelInfo(
            val full: UShort,
            val dirty: UByte,
        )

        fun ProximityPairing.Message.getModelInfo(): ModelInfo = ModelInfo(
            full = (((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)).toUShort(),
            dirty = data[1]
        )

        abstract fun isResponsible(proximityMessage: ProximityPairing.Message): Boolean

        abstract fun create(
            scanResult: BleScanResult,
            proximityMessage: ProximityPairing.Message,
        ): ApplePods
    }
}