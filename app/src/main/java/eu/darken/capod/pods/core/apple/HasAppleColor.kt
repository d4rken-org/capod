package eu.darken.capod.pods.core.apple

import android.content.Context
import eu.darken.capod.pods.core.HasPodStyle

interface HasAppleColor : ApplePods, HasPodStyle {

    override val podStyle: HasPodStyle.PodStyle
        get() = DeviceColor.entries
            .firstOrNull { it.raw == pubDeviceColor }
            ?: DeviceColor.UNKNOWN

    enum class DeviceColor(val raw: UByte?) : HasPodStyle.PodStyle {
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

        override fun getLabel(context: Context): String = this.name

        override fun getColor(context: Context): Int = android.R.color.white

        override val identifier: String
            get() = name

        constructor(raw: Int) : this(raw.toUByte())
    }
}