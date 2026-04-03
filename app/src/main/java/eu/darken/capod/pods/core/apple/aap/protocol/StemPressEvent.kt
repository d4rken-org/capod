package eu.darken.capod.pods.core.apple.aap.protocol

data class StemPressEvent(
    val pressType: PressType,
    val bud: Bud,
) {
    enum class PressType(val wireValue: Int) {
        SINGLE(0x05), DOUBLE(0x06), TRIPLE(0x07), LONG(0x08);

        companion object {
            fun fromWire(value: Int): PressType? = entries.firstOrNull { it.wireValue == value }
        }
    }

    enum class Bud(val wireValue: Int) {
        LEFT(0x01), RIGHT(0x02);

        companion object {
            fun fromWire(value: Int): Bud? = entries.firstOrNull { it.wireValue == value }
        }
    }
}
