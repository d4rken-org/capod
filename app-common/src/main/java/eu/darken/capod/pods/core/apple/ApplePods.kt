package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.toHex
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing

interface ApplePods : PodDevice {

    val proximityMessage: ProximityPairing.Message

    val decryptedPayload: UByteArray?
        get() = null

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


}