package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload

interface ApplePods : PodDevice {

    val payload: ProximityPayload

    // We start counting at the airpods prefix byte
    val pubPrefix: UByte
        get() = payload.public.data[0]

    val pubDeviceModel: UShort
        get() = (((payload.public.data[1].toInt() and 255) shl 8) or (payload.public.data[2].toInt() and 255)).toUShort()

    val pubStatus: UByte
        get() = payload.public.data[3]

    val pubPodsBattery: UByte
        get() = payload.public.data[4]

    val pubFlags: UShort
        get() = payload.public.data[5].upperNibble

    val pubCaseBattery: UShort
        get() = payload.public.data[5].lowerNibble

    val pubCaseLidState: UByte
        get() = payload.public.data[6]

    val pubDeviceColor: UByte
        get() = payload.public.data[7]

    val pubSuffix: UByte
        get() = payload.public.data[8]
}