package eu.darken.capod.pods.core.apple.aap.protocol

import java.time.Instant

/**
 * Device identity parsed from the AAP Information message (type 0x1D).
 *
 * Field ordering and semantics are sourced from the Wireshark AAP dissector:
 * <https://github.com/pabloaul/apple-wireshark/blob/main/plugins/aacp.lua>
 *
 * Segments 0-10 are NUL-delimited UTF-8. Segments 11-12 are fixed 17-byte
 * UUIDs (may contain 0x00 — never treat as strings). Segments 13-14 are
 * NUL-delimited timestamps (Unix epoch seconds, ASCII).
 *
 * Any field past the system fields may be absent on older devices or
 * truncated payloads — all optional fields are nullable.
 */
data class AapDeviceInfo(
    /** Segment 0 — user-visible device name ("AirPods Pro"). */
    val name: String,
    /** Segment 1 — Apple model identifier ("A2084"). */
    val modelNumber: String,
    /** Segment 2 — manufacturer string ("Apple Inc."). */
    val manufacturer: String,
    /** Segment 3 — system (case) serial number. */
    val serialNumber: String,
    /** Segment 4 — currently-running firmware version. */
    val firmwareVersion: String,
    /** Segment 5 — pending firmware version after next reboot. Null if equal to active. */
    val firmwareVersionPending: String? = null,
    /** Segment 6 — hardware revision ("1.0.0"). */
    val hardwareVersion: String? = null,
    /** Segment 7 — External Accessory protocol name ("com.apple.accessory.updater.app.71"). */
    val eaProtocolName: String? = null,
    /** Segment 8 — left earbud serial. */
    val leftEarbudSerial: String? = null,
    /** Segment 9 — right earbud serial. */
    val rightEarbudSerial: String? = null,
    /**
     * Segment 10 — marketing/build version (e.g. "8454624"). Originally
     * mis-labeled `buildNumber` in CAPod; the Wireshark dissector calls it
     * "Marketing Version".
     */
    val marketingVersion: String? = null,
    /** Segment 11 — opaque 17-byte left-bud UUID. May contain arbitrary bytes. */
    val leftEarbudUuid: ByteArray? = null,
    /** Segment 12 — opaque 17-byte right-bud UUID. May contain arbitrary bytes. */
    val rightEarbudUuid: ByteArray? = null,
    /** Segment 13 — first-time-pairing timestamp for the left bud. */
    val leftEarbudFirstPaired: Instant? = null,
    /** Segment 14 — first-time-pairing timestamp for the right bud. */
    val rightEarbudFirstPaired: Instant? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AapDeviceInfo) return false
        if (name != other.name) return false
        if (modelNumber != other.modelNumber) return false
        if (manufacturer != other.manufacturer) return false
        if (serialNumber != other.serialNumber) return false
        if (firmwareVersion != other.firmwareVersion) return false
        if (firmwareVersionPending != other.firmwareVersionPending) return false
        if (hardwareVersion != other.hardwareVersion) return false
        if (eaProtocolName != other.eaProtocolName) return false
        if (leftEarbudSerial != other.leftEarbudSerial) return false
        if (rightEarbudSerial != other.rightEarbudSerial) return false
        if (marketingVersion != other.marketingVersion) return false
        if (!leftEarbudUuid.contentOptionalEquals(other.leftEarbudUuid)) return false
        if (!rightEarbudUuid.contentOptionalEquals(other.rightEarbudUuid)) return false
        if (leftEarbudFirstPaired != other.leftEarbudFirstPaired) return false
        if (rightEarbudFirstPaired != other.rightEarbudFirstPaired) return false
        return true
    }

    override fun hashCode(): Int {
        var r = name.hashCode()
        r = 31 * r + modelNumber.hashCode()
        r = 31 * r + manufacturer.hashCode()
        r = 31 * r + serialNumber.hashCode()
        r = 31 * r + firmwareVersion.hashCode()
        r = 31 * r + (firmwareVersionPending?.hashCode() ?: 0)
        r = 31 * r + (hardwareVersion?.hashCode() ?: 0)
        r = 31 * r + (eaProtocolName?.hashCode() ?: 0)
        r = 31 * r + (leftEarbudSerial?.hashCode() ?: 0)
        r = 31 * r + (rightEarbudSerial?.hashCode() ?: 0)
        r = 31 * r + (marketingVersion?.hashCode() ?: 0)
        r = 31 * r + (leftEarbudUuid?.contentHashCode() ?: 0)
        r = 31 * r + (rightEarbudUuid?.contentHashCode() ?: 0)
        r = 31 * r + (leftEarbudFirstPaired?.hashCode() ?: 0)
        r = 31 * r + (rightEarbudFirstPaired?.hashCode() ?: 0)
        return r
    }
}

private fun ByteArray?.contentOptionalEquals(other: ByteArray?): Boolean =
    if (this == null || other == null) this === other else contentEquals(other)
