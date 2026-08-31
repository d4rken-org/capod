package eu.darken.capod.pods.core.apple.ble.protocol

import android.annotation.SuppressLint
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.redactedForLogs
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class RPAChecker @Inject constructor() {

    enum class AddressOrder {
        STANDARD,
        REVERSED,
    }

    // Resolvable-Private-Address
    fun verify(address: BluetoothAddress, irk: IdentityResolvingKey): Boolean = resolve(address, irk) != null

    /**
     * Returns the octet order the [address] resolved in, or null if it resolves in neither.
     *
     * The reversed attempt is gated on the address being RPA-shaped in that order, the standard
     * attempt is not: a device that resolves today must keep resolving byte-for-byte the same way.
     */
    fun resolve(address: BluetoothAddress, irk: IdentityResolvingKey): AddressOrder? = try {
        val octets = address.parseOctets()
        when {
            octets == null -> null
            matchesHash(octets, irk) -> AddressOrder.STANDARD
            else -> octets.reversedArray()
                .takeIf { it.isRpaShaped() && matchesHash(it, irk) }
                ?.let { AddressOrder.REVERSED }
        }
    } catch (e: Exception) {
        log(
            TAG,
            Logging.Priority.ERROR
        ) { "Failed to resolve RPA\naddress=${address.redactedForLogs()}\nIRK=${irk.size}B\n${e.asLog()}" }
        null
    }

    /** Six octets, most-significant first, as printed. Null if [this] isn't a well-formed address. */
    private fun BluetoothAddress.parseOctets(): ByteArray? {
        val parts = split(":")
        if (parts.size != ADDRESS_OCTETS) return null
        val octets = ByteArray(ADDRESS_OCTETS)
        parts.forEachIndexed { index, part ->
            val value = part.toIntOrNull(16) ?: return null
            if (value !in 0..255) return null
            octets[index] = value.toByte()
        }
        return octets
    }

    private fun ByteArray.isRpaShaped(): Boolean = (this[0].toInt() and 0xC0) == 0x40

    private fun matchesHash(octets: ByteArray, irk: IdentityResolvingKey): Boolean {
        val rpa = octets.reversedArray()
        val prand = rpa.copyOfRange(3, 6)
        val hash = rpa.copyOfRange(0, 3)
        return hash.contentEquals(ah(irk, prand))
    }

    // E function (Encryption function):
    @SuppressLint("GetInstance")
    private fun e(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            val secretKey = SecretKeySpec(key.reversedArray(), "AES")
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
        return cipher.doFinal(data.reversedArray()).reversedArray()
    }

    // AH function (Address Hashing function):
    private fun ah(k: ByteArray, r: ByteArray): ByteArray {
        val rPadded = ByteArray(16).apply {
            r.copyInto(this, 0, 0, 3)
        }
        return e(k, rPadded).copyOfRange(0, 3)
    }

    companion object {
        private const val ADDRESS_OCTETS = 6
        private val TAG = logTag("Monitor", "BlePodMonitor", "RPAChecker")
    }
}