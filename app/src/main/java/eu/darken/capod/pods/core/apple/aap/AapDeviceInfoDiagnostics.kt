package eu.darken.capod.pods.core.apple.aap

/**
 * Diagnostic-only NUL-delimited segmentation of a 0x1D INFORMATION payload, used for
 * issue #173 engraving discovery logging.
 */
internal object AapDeviceInfoDiagnostics {

    fun describeSegments(payload: ByteArray): List<DeviceInfoSegment> {
        var start = 0
        while (start < payload.size) {
            val b = payload[start].toInt() and 0xFF
            if (b in 0x20..0x7E) break
            start++
        }
        if (start >= payload.size) return emptyList()

        val segments = mutableListOf<DeviceInfoSegment>()
        var segIndex = 0
        var i = start
        while (i < payload.size) {
            while (i < payload.size && payload[i] == 0x00.toByte()) i++
            if (i >= payload.size) break

            val segStart = i
            while (i < payload.size && payload[i] != 0x00.toByte()) i++
            val segBytes = payload.copyOfRange(segStart, i)

            val utf8: String? = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(segBytes))
                    .toString()
            } catch (_: java.nio.charset.CharacterCodingException) {
                null
            }
            val hex = segBytes.joinToString("") { "%02X".format(it) }

            segments += DeviceInfoSegment(segIndex, segStart, segBytes.size, utf8, hex)
            segIndex++
        }
        return segments
    }
}

internal data class DeviceInfoSegment(
    val index: Int,
    val offset: Int,
    val length: Int,
    val utf8: String?,
    val hex: String,
)
