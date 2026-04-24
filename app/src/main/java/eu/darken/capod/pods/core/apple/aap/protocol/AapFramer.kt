package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Accumulates bytes from a stream and emits complete [AapMessage] objects.
 *
 * Raw L2CAP reads can return partial or multiple messages in a single read.
 * The framer buffers partial data and splits multi-message reads.
 *
 * AAP message framing: first 4 bytes are header, bytes 2-3 (little-endian)
 * indicate total message length (excluding the first 4 header bytes).
 *
 * Note: production code bypasses this framer — [AapConnection.readLoop]
 * parses whole reads directly because L2CAP SEQPACKET already delivers
 * per-frame boundaries. The framer is retained for future stream-mode
 * paths but its splitting rule doesn't match every real capture
 * (see `AapFramerTest` for the shape it expects).
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
