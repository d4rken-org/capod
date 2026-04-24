package eu.darken.capod.pods.core.apple.aap.engine

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Diagnostic-only segmentation of a 0x1D INFORMATION payload, used for issue #173
 * engraving discovery logging.
 *
 * Schema matches the Wireshark AAP dissector's INFORMATION message:
 *  - Segments 0-10 are NUL-delimited UTF-8 strings.
 *  - Segments 11 and 12 are fixed 17-byte UUIDs (may contain 0x00 internally —
 *    splitting on NUL here would corrupt the offsets for every later segment).
 *  - Segments 13 and 14 are NUL-delimited ASCII-decimal timestamps.
 *
 * Payloads that truncate early just produce fewer segments — the helper stays
 * best-effort and never throws.
 */
internal object AapDeviceInfoDiagnostics {

    private const val UUID_LEN = 17
    private const val LAST_STRING_SEGMENT = 10 // after this the UUID blob starts

    fun describeSegments(payload: ByteArray): List<DeviceInfoSegment> {
        var offset = skipBinaryHeader(payload)
        if (offset >= payload.size) return emptyList()

        val segments = mutableListOf<DeviceInfoSegment>()
        var segIndex = 0

        // Segments 0..10 — NUL-delimited UTF-8 strings
        while (segIndex <= LAST_STRING_SEGMENT && offset < payload.size) {
            while (offset < payload.size && payload[offset] == 0x00.toByte()) offset++
            if (offset >= payload.size) break
            val segStart = offset
            while (offset < payload.size && payload[offset] != 0x00.toByte()) offset++
            segments += buildSegment(segIndex, segStart, payload.copyOfRange(segStart, offset))
            segIndex++
        }

        // Skip the NUL terminator of segment 10 before the UUID blob.
        while (offset < payload.size && payload[offset] == 0x00.toByte()) offset++

        // Segments 11 and 12 — fixed 17-byte UUIDs, read verbatim.
        for (targetIdx in 11..12) {
            if (offset + UUID_LEN > payload.size) break
            val segBytes = payload.copyOfRange(offset, offset + UUID_LEN)
            segments += buildSegment(targetIdx, offset, segBytes)
            offset += UUID_LEN
            segIndex = targetIdx + 1
        }

        // Segments 13+ — NUL-delimited timestamps / any trailing strings.
        while (offset < payload.size) {
            while (offset < payload.size && payload[offset] == 0x00.toByte()) offset++
            if (offset >= payload.size) break
            val segStart = offset
            while (offset < payload.size && payload[offset] != 0x00.toByte()) offset++
            segments += buildSegment(segIndex, segStart, payload.copyOfRange(segStart, offset))
            segIndex++
        }

        return segments
    }

    private fun skipBinaryHeader(payload: ByteArray): Int {
        var i = 0
        while (i < payload.size) {
            val b = payload[i].toInt() and 0xFF
            if (b in 0x20..0x7E) return i
            i++
        }
        return payload.size
    }

    private fun buildSegment(index: Int, offset: Int, bytes: ByteArray): DeviceInfoSegment {
        val utf8: String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
        val hex = bytes.joinToString("") { "%02X".format(it) }
        return DeviceInfoSegment(index, offset, bytes.size, utf8, hex)
    }
}

internal data class DeviceInfoSegment(
    val index: Int,
    val offset: Int,
    val length: Int,
    val utf8: String?,
    val hex: String,
)
