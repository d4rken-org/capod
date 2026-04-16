package eu.darken.capod.pods.core.apple.aap

/**
 * Batches cmd 0x0017 HID descriptor frames and emits structured summaries.
 *
 * During case transitions AirPods send 800+ descriptor frames in ~20 seconds.
 * Instead of logging each one, this tracker classifies each frame and batches
 * consecutive bulk descriptor frames by (phase, fill), emitting a single summary
 * line per batch.
 */
internal class HidTracker(private val log: (String) -> Unit) {

    private var bulkCount = 0
    private var bulkPhase: Int = -1
    private var bulkFill: Int = -1

    fun consume(payload: ByteArray) {
        when (val type = classify(payload)) {
            is HidFrameType.ServiceDirectory -> {
                flush()
                val names = type.services.joinToString(", ")
                log("HID: services=[$names] (${payload.size}B)")
            }
            is HidFrameType.Descriptor -> {
                if (bulkCount == 0) {
                    bulkPhase = type.phase
                    bulkFill = type.fill
                    bulkCount = 1
                } else if (bulkPhase == type.phase && bulkFill == type.fill) {
                    bulkCount++
                } else {
                    flush()
                    bulkPhase = type.phase
                    bulkFill = type.fill
                    bulkCount = 1
                }
            }
            is HidFrameType.Terminator -> {
                flush()
                log("HID: terminator (${type.payloadSize}B)")
            }
            is HidFrameType.Other -> {
                flush()
                val hex = payload.joinToString(" ") { "%02X".format(it) }
                log("HID: unknown (${payload.size}B) [$hex]")
            }
        }
    }

    fun flush() {
        if (bulkCount == 0) return
        log(
            "HID: $bulkCount descriptor frames phase=0x${"%02X".format(bulkPhase)} fill=0x${"%02X".format(bulkFill)}"
        )
        bulkCount = 0
        bulkPhase = -1
        bulkFill = -1
    }

    fun reset() {
        bulkCount = 0
        bulkPhase = -1
        bulkFill = -1
    }

    sealed class HidFrameType {
        data class ServiceDirectory(val services: List<String>) : HidFrameType()
        data class Descriptor(val phase: Int, val fill: Int) : HidFrameType()
        data class Terminator(val payloadSize: Int) : HidFrameType()
        data object Other : HidFrameType()
    }

    companion object {
        internal fun classify(payload: ByteArray): HidFrameType {
            // Service directory frame — starts with FE 00 00 and contains repeated
            // [len=4? ascii service name + 4B flags] blocks. For logging, extract names.
            if (payload.size >= 4 && (payload[0].toInt() and 0xFF) == 0xFE) {
                val services = mutableListOf<String>()
                var i = 4
                while (i + 7 < payload.size) {
                    val rawName = payload.copyOfRange(i, i + 4)
                    val name = rawName
                        .takeWhile { it != 0.toByte() }
                        .toByteArray()
                        .toString(Charsets.US_ASCII)
                    if (name.isNotBlank()) services += name
                    i += 8
                }
                return HidFrameType.ServiceDirectory(services)
            }

            // Bulk descriptor frames observed as:
            // 00 04 00 00 44 00 01 A1 81 FF FF ...
            // 00 04 00 00 44 00 01 C3 02 EF EF ...
            if (payload.size >= 10 &&
                payload[0] == 0x00.toByte() &&
                payload[1] == 0x04.toByte() &&
                payload[4] == 0x44.toByte() &&
                payload[5] == 0x00.toByte()
            ) {
                val phase = payload[8].toInt() and 0xFF
                val fill = payload[9].toInt() and 0xFF
                return HidFrameType.Descriptor(phase = phase, fill = fill)
            }

            // Small terminator frame observed as exactly 7 bytes ending in FF.
            if (payload.size == 7 && payload.last() == 0xFF.toByte()) {
                return HidFrameType.Terminator(payload.size)
            }

            return HidFrameType.Other
        }
    }
}
