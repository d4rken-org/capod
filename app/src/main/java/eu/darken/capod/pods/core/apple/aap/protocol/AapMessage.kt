package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * A parsed AAP protocol message.
 */
data class AapMessage(
    val raw: ByteArray,
    val commandType: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AapMessage) return false
        return raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int = raw.contentHashCode()

    companion object {
        /**
         * Parse a complete AAP message from raw bytes.
         * AAP messages have the format: [4-byte header] [2-byte command type] [payload…]
         * Minimum message size is 6 bytes (header + command type with no payload).
         */
        fun parse(raw: ByteArray): AapMessage? {
            if (raw.size < 6) return null
            val commandType = (raw[4].toInt() and 0xFF) or ((raw[5].toInt() and 0xFF) shl 8)
            val payload = if (raw.size > 6) raw.copyOfRange(6, raw.size) else ByteArray(0)
            return AapMessage(raw = raw.copyOf(), commandType = commandType, payload = payload)
        }
    }
}

/**
 * Accumulates bytes from a stream and emits complete [AapMessage] objects.
 *
 * Raw L2CAP reads can return partial or multiple messages in a single read.
 * The framer buffers partial data and splits multi-message reads.
 *
 * AAP message framing: first 4 bytes are header, bytes 2-3 (little-endian)
 * indicate total message length (excluding the first 4 header bytes).
 */
class AapFramer {

    private val buffer = mutableListOf<Byte>()

    /**
     * Feed raw bytes from a socket read. Returns any complete messages found.
     */
    fun consume(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<AapMessage> {
        for (i in offset until offset + length) {
            buffer.add(bytes[i])
        }

        val messages = mutableListOf<AapMessage>()

        while (buffer.size >= 4) {
            // Bytes 2-3 (little-endian) = payload length after the 4-byte header
            val payloadLength = (buffer[2].toInt() and 0xFF) or ((buffer[3].toInt() and 0xFF) shl 8)
            val totalLength = 4 + payloadLength

            if (buffer.size < totalLength) break // Need more data

            val messageBytes = ByteArray(totalLength)
            for (i in 0 until totalLength) {
                messageBytes[i] = buffer[i]
            }
            buffer.subList(0, totalLength).clear()

            AapMessage.parse(messageBytes)?.let { messages.add(it) }
        }

        return messages
    }

    fun reset() {
        buffer.clear()
    }
}
