package eu.darken.capod.pods.core.apple

import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing

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

    val rawPodsBattery: UByte
        get() = proximityMessage.data[4]

    val rawCaseBattery: UByte
        get() = proximityMessage.data[5]

    val rawCaseLidState: UByte
        get() = proximityMessage.data[6]

    val rawDeviceColor: UByte
        get() = proximityMessage.data[7]

    val rawSuffix: UByte
        get() = proximityMessage.data[8]
}