package eu.darken.capod.pods.core.airpods

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.isBitSet
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.DualPods
import eu.darken.capod.pods.core.DualPods.Pod

interface SingleApplePods : ApplePods, DualPods {

    val tag: String

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

    override val microPhonePod: Pod
        get() = when (rawStatus.isBitSet(5)) {
            true -> Pod.LEFT
            false -> Pod.RIGHT
        }

    override val batteryLeftPodPercent: Float?
        get() {
            val value = when (microPhonePod) {
                Pod.LEFT -> rawPodsBattery.lowerNibble.toInt()
                Pod.RIGHT -> rawPodsBattery.upperNibble.toInt()
            }
            return when (value) {
                15 -> null
                else -> if (value > 10) {
                    log(tag) { "Left pod: Above 100% battery: $value" }
                    1.0f
                } else {
                    (value / 10f)
                }
            }
        }

    override val batteryRightPodPercent: Float?
        get() {
            val value = when (microPhonePod) {
                Pod.LEFT -> rawPodsBattery.upperNibble.toInt()
                Pod.RIGHT -> rawPodsBattery.lowerNibble.toInt()
            }
            return when (value) {
                15 -> null
                else -> if (value > 10) {
                    log(tag) { "Right pod: Above 100% battery: $value" }
                    1.0f
                } else {
                    value / 10f
                }
            }
        }

    override val batteryCasePercent: Float?
        get() = when (val value = rawCaseBattery.lowerNibble.toInt()) {
            15 -> null
            else -> if (value > 10) {
                log(tag) { "Case: Above 100% battery: $value" }
                1.0f
            } else {
                value / 10f
            }
        }

    override val isLeftPodInEar: Boolean
        get() = when (microPhonePod) {
            Pod.LEFT -> rawStatus.isBitSet(1)
            Pod.RIGHT -> rawStatus.isBitSet(3)
        }

    override val isRightPodInEar: Boolean
        get() = when (microPhonePod) {
            Pod.LEFT -> rawStatus.isBitSet(3)
            Pod.RIGHT -> rawStatus.isBitSet(1)
        }

    val status: Status
        get() = Status.values().firstOrNull { it.raw == rawStatus } ?: Status.UNKNOWN

    enum class Status(val raw: UByte?) {
        BOTH_AIRPODS_IN_CASE(0x55),
        LEFT_IN_EAR(0x33),
        AIRPLANE(0x02),
        UNKNOWN(null);

        constructor(raw: Int) : this(raw.toUByte())
    }

    // 1010101 0x55 85 Both In Case
    // 0101011 0x2b 43 Both In Ear
    // 0101001 0x29 41 Right in Ear
    // 0100011 0x23 35 Left In Ear
    // 0100001 0x21 33 Neither in Ear or In Case
    // 1110001 0x71 113 Left in Case, Right on Desk
    // 0010001 0x11 17 Left in Case, Right on Desk
    // 0010011 0x13 19 Left in Case, Right in Ear
    // 1110011 0x73 115 Left in Case, Right in Ear


    override val isCaseCharging: Boolean
        get() = rawCaseBattery.upperNibble.isBitSet(2)

    override val isLeftPodCharging: Boolean
        get() = when (microPhonePod) {
            Pod.LEFT -> rawCaseBattery.upperNibble.isBitSet(0)
            Pod.RIGHT -> rawCaseBattery.upperNibble.isBitSet(1)
        }

    override val isRightPodCharging: Boolean
        get() = when (microPhonePod) {
            Pod.LEFT -> rawCaseBattery.upperNibble.isBitSet(1)
            Pod.RIGHT -> rawCaseBattery.upperNibble.isBitSet(0)
        }

    val caseLidState: LidState
        get() = LidState.values().firstOrNull { it.raw == rawCaseLidState } ?: LidState.UNKNOWN

    enum class LidState(val raw: UByte?) {
        OPEN(0x31),
        CLOSED(0x38),
        NOT_IN_CASE(0x01),
        UNKNOWN(null);

        constructor(raw: Int) : this(raw.toUByte())
    }

    val deviceColor: DeviceColor
        get() = DeviceColor.values().firstOrNull { it.raw == rawDeviceColor } ?: DeviceColor.UNKNOWN

    enum class DeviceColor(val raw: UByte?) {
        WHITE(0x00),
        BLACK(0x01),
        RED(0x02),
        BLUE(0x03),
        PINK(0x04),
        GRAY(0x05),
        SILVER(0x06),
        GOLD(0x07),
        ROSE_GOLD(0x08),
        SPACE_GRAY(0x09),
        DARK_BLUE(0x0a),
        LIGHT_BLUE(0x0b),
        YELLOW(0x0c),
        UNKNOWN(null);

        constructor(raw: Int) : this(raw.toUByte())
    }

    val connectionState: ConnectionState
        get() = ConnectionState.values().firstOrNull { rawSuffix == it.raw } ?: ConnectionState.UNKNOWN

    enum class ConnectionState(val raw: UByte?) {
        DISCONNECTED(0x00),
        IDLE(0x04),
        MUSIC(0x05),
        CALL(0x06),
        RINGING(0x07),
        HANGING_UP(0x09),
        UNKNOWN(null);

        constructor(raw: Int) : this(raw.toUByte())
    }
}