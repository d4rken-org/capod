package eu.darken.capod.pods.core.apple.aap.engine

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

            is HidFrameType.ServiceInfo -> {
                flush()
                val tokens = type.asciiTokens.joinToString(", ")
                log("HID: service info tokens=[$tokens] (${type.payloadSize}B)")
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

        /**
         * 0x0017 "service info" frames — a TLV-ish metadata dump that carries
         * ASCII key/value pairs like `VendorID`, `SerialNumber`, `CFG`,
         * `ReportDescriptor`, etc.
         *
         * We don't decode the TLV structure yet (the framing is not fully
         * documented). Instead we extract all printable ASCII runs of length
         * ≥ 3 so the log tells you which keys/values the frame contains.
         */
        data class ServiceInfo(val asciiTokens: List<String>, val payloadSize: Int) : HidFrameType()

        data object Other : HidFrameType()
    }

    companion object {
        /** Minimum length for an ASCII run to count as a token in a ServiceInfo frame. */
        private const val MIN_ASCII_TOKEN_LEN = 3

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

            // "Service info" frame — 4-byte magic 00 00 10 00 followed by a TLV-ish
            // payload with ASCII keys and mixed binary values. Observed on AirPods Pro 2
            // USB-C after the descriptor batch.
            if (payload.size >= 8 &&
                payload[0] == 0x00.toByte() &&
                payload[1] == 0x00.toByte() &&
                payload[2] == 0x10.toByte() &&
                payload[3] == 0x00.toByte()
            ) {
                return HidFrameType.ServiceInfo(
                    asciiTokens = extractAsciiTokens(payload),
                    payloadSize = payload.size,
                )
            }

            return HidFrameType.Other
        }

        /** Extract printable ASCII runs ≥ 3 chars. Skips all non-printable bytes. */
        private fun extractAsciiTokens(payload: ByteArray): List<String> {
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < payload.size) {
                val startByte = payload[i].toInt() and 0xFF
                if (startByte in 0x20..0x7E) {
                    val start = i
                    while (i < payload.size && (payload[i].toInt() and 0xFF) in 0x20..0x7E) i++
                    val run = String(payload, start, i - start, Charsets.US_ASCII)
                    if (run.length >= MIN_ASCII_TOKEN_LEN) tokens += run
                } else {
                    i++
                }
            }
            return tokens
        }
    }
}