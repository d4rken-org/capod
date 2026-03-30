package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Result of parsing an AAP private key response (command 0x31).
 * Contains the Identity Resolving Key (IRK) for BLE RPA verification
 * and the Encryption Key (ENC) for BLE encrypted battery decryption.
 */
data class KeyExchangeResult(
    val irk: ByteArray?,
    val encKey: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyExchangeResult) return false
        return irk.contentEquals(other.irk) && encKey.contentEquals(other.encKey)
    }

    override fun hashCode(): Int {
        var result = irk?.contentHashCode() ?: 0
        result = 31 * result + (encKey?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this != null && other != null -> this.contentEquals(other)
    else -> false
}
