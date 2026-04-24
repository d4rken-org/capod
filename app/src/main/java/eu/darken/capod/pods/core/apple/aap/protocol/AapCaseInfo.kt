package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Information about the case itself (message type 0x23, requested via 0x22).
 *
 * Fields defined by the Wireshark AAP dissector:
 *  - CaseInfoMessageVersion
 *  - CaseInfoVID / CaseInfoPID / CaseInfoVIDSource
 *  - CaseInfoColor
 *  - CaseInfoVersion (case firmware)
 *  - CaseInfoName
 *
 * The Wireshark dissector does not yet decode the per-field byte offsets
 * reliably — the payload length and presence of each field varies across
 * models. This class holds the raw bytes for future iteration once more
 * captures are available, and a best-effort named-field view.
 */
data class AapCaseInfo(
    /** The complete payload (after the 6-byte header) for future analysis. */
    val rawPayload: ByteArray,
    val messageVersion: Int? = null,
    val vid: Int? = null,
    val pid: Int? = null,
    val vidSource: Int? = null,
    val color: Int? = null,
    val version: String? = null,
    val name: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AapCaseInfo) return false
        if (!rawPayload.contentEquals(other.rawPayload)) return false
        if (messageVersion != other.messageVersion) return false
        if (vid != other.vid) return false
        if (pid != other.pid) return false
        if (vidSource != other.vidSource) return false
        if (color != other.color) return false
        if (version != other.version) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        var r = rawPayload.contentHashCode()
        r = 31 * r + (messageVersion ?: 0)
        r = 31 * r + (vid ?: 0)
        r = 31 * r + (pid ?: 0)
        r = 31 * r + (vidSource ?: 0)
        r = 31 * r + (color ?: 0)
        r = 31 * r + (version?.hashCode() ?: 0)
        r = 31 * r + (name?.hashCode() ?: 0)
        return r
    }
}
