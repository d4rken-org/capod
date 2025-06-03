package eu.darken.capod.monitor.core

import android.annotation.SuppressLint
import eu.darken.capod.pods.core.PodDevice
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class RPAChecker @Inject constructor() {

    // Resolvable-Private-Address
    fun verify(dev: PodDevice, irk: ByteArray): Boolean {
        val rpa = dev.address.split(":").map { it.toInt(16).toByte() }.reversed().toByteArray()
        val prand = rpa.copyOfRange(3, 6)
        val hash = rpa.copyOfRange(0, 3)
        val computedHash = ah(irk, prand)
        return hash.contentEquals(computedHash)
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
}