package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * A parsed AAP (AACP) frame. Packet type lives at bytes 0-1 (little-endian)
 * and selects which variant we're looking at.
 *
 * Per the Wireshark dissector, packet types are:
 * - 0x0000 — Connect (session open request; source → pods)
 * - 0x0001 — Connect Response (pods → source)
 * - 0x0002 — Disconnect
 * - 0x0003 — Disconnect Response
 * - 0x0004 — Message (the large Message type with a 16-bit message/command ID)
 *
 * Only `Message` packets carry the `(commandType, payload)` pair that the
 * existing decoder pipeline consumes; the other variants have their own
 * field schema and bypass the decoder.
 */
sealed class AapPacket(val raw: ByteArray) {

    /** Source → pods session open. We emit this as our handshake. */
    class Connect(
        raw: ByteArray,
        val service: Int,
        val major: Int,
        val minor: Int,
        val features: ULong,
    ) : AapPacket(raw)

    /**
     * Pods → source response to a Connect. The `features` bitmask is opaque
     * for now (no public bit-to-feature mapping); CAPod logs it and stores
     * it in `AapPodState` for future correlation work.
     */
    class ConnectResponse(
        raw: ByteArray,
        val service: Int,
        val status: Int,
        val major: Int,
        val minor: Int,
        val features: ULong,
    ) : AapPacket(raw)

    class Disconnect(
        raw: ByteArray,
        val service: Int,
        val status: Int,
    ) : AapPacket(raw)

    class DisconnectResponse(
        raw: ByteArray,
        val service: Int,
    ) : AapPacket(raw)

    /**
     * Message packet — bytes 4-5 are the message/command ID. This is what
     * [AapDeviceProfile.decodeSetting] / `decodeBattery` / etc. consume.
     *
     * Typealiased to `AapMessage` for backward compatibility.
     */
    class Message(
        raw: ByteArray,
        val commandType: Int,
        val payload: ByteArray,
    ) : AapPacket(raw) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Message) return false
            return raw.contentEquals(other.raw)
        }

        override fun hashCode(): Int = raw.contentHashCode()

        override fun toString(): String =
            "AapPacket.Message(cmd=0x${"%04X".format(commandType)}, payload=${payload.size}B)"

        companion object {
            /**
             * Parse a Message-type AAP frame. Returns null if the bytes aren't
             * a complete Message packet (packet type != 0x0004, or payload too short).
             * Non-Message packets (Connect Response etc.) return null here —
             * use [AapPacket.parse] if you need to handle them.
             */
            fun parse(raw: ByteArray): Message? {
                val packet = AapPacket.parse(raw) ?: return null
                return packet as? Message
            }
        }
    }

    /** Unknown / unrecognised packet type. Preserved for logging. */
    class Unknown(raw: ByteArray, val packetType: Int) : AapPacket(raw)

    companion object {
        private const val PACKET_TYPE_CONNECT = 0x0000
        private const val PACKET_TYPE_CONNECT_RESPONSE = 0x0001
        private const val PACKET_TYPE_DISCONNECT = 0x0002
        private const val PACKET_TYPE_DISCONNECT_RESPONSE = 0x0003
        private const val PACKET_TYPE_MESSAGE = 0x0004

        /**
         * Parse a raw AAP frame. Returns:
         *  - `null` if the buffer is too short to even read the packet type.
         *  - An [Unknown] if the packet type isn't one we recognise.
         *  - The appropriate subclass otherwise.
         *
         * Note: this assumes one frame per call — L2CAP SEQPACKET delivers
         * complete frames per read, matching this expectation. The
         * (unused-in-prod) [AapFramer] uses a different splitting approach.
         */
        fun parse(raw: ByteArray): AapPacket? {
            if (raw.size < 4) return null
            val packetType = readLe16(raw, 0)
            val service = readLe16(raw, 2)
            return when (packetType) {
                PACKET_TYPE_CONNECT -> parseConnect(raw, service)
                PACKET_TYPE_CONNECT_RESPONSE -> parseConnectResponse(raw, service)
                PACKET_TYPE_DISCONNECT -> parseDisconnect(raw, service)
                PACKET_TYPE_DISCONNECT_RESPONSE -> DisconnectResponse(raw.copyOf(), service)
                PACKET_TYPE_MESSAGE -> parseMessage(raw)
                else -> Unknown(raw.copyOf(), packetType)
            }
        }

        private fun parseConnect(raw: ByteArray, service: Int): Connect? {
            if (raw.size < 16) return null
            val major = readLe16(raw, 4)
            val minor = readLe16(raw, 6)
            val features = readLe64(raw, 8)
            return Connect(raw.copyOf(), service, major, minor, features)
        }

        private fun parseConnectResponse(raw: ByteArray, service: Int): ConnectResponse? {
            if (raw.size < 18) return null
            val status = readLe16(raw, 4)
            val major = readLe16(raw, 6)
            val minor = readLe16(raw, 8)
            val features = readLe64(raw, 10)
            return ConnectResponse(raw.copyOf(), service, status, major, minor, features)
        }

        private fun parseDisconnect(raw: ByteArray, service: Int): Disconnect? {
            if (raw.size < 6) return null
            val status = readLe16(raw, 4)
            return Disconnect(raw.copyOf(), service, status)
        }

        private fun parseMessage(raw: ByteArray): Message? {
            if (raw.size < 6) return null
            val commandType = readLe16(raw, 4)
            val payload = if (raw.size > 6) raw.copyOfRange(6, raw.size) else ByteArray(0)
            return Message(raw = raw.copyOf(), commandType = commandType, payload = payload)
        }

        private fun readLe16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun readLe64(data: ByteArray, offset: Int): ULong {
            var result = 0UL
            for (i in 0 until 8) {
                result = result or ((data[offset + i].toLong() and 0xFFL).toULong() shl (i * 8))
            }
            return result
        }
    }
}

/**
 * Backward-compat alias — CAPod historically named the Message packet just
 * `AapMessage`. Kept as a type alias so decoder signatures don't churn.
 */
typealias AapMessage = AapPacket.Message
