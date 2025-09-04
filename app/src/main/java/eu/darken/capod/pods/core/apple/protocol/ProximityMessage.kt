package eu.darken.capod.pods.core.apple.protocol

import android.R.id.message
import android.annotation.SuppressLint
import dagger.Reusable
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import okio.ByteString.Companion.toByteString
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class ProximityMessage(
    val type: UByte,
    val length: Int,
    val data: UByteArray
) {

    override fun toString(): String {
        val dataHex = data.joinToString(separator = " ") { String.format("%02X", it.toByte()) }
        return "ProximityPairing.Message(type=$type, length=$length, data=$dataHex)"
    }

    @Reusable
    class Decrypter @Inject constructor() {
        @SuppressLint("GetInstance")
        fun decrypt(data: ByteArray, key: ProximityEncryptionKey): UByteArray? {
            val decryptedData = try {
                val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
                    val secretKey = SecretKeySpec(key, "AES")
                    init(Cipher.DECRYPT_MODE, secretKey)
                }
                cipher.doFinal(data.copyOfRange(data.size - 16, data.size))
            } catch (e: Exception) {
                log(
                    TAG,
                    Logging.Priority.ERROR
                ) { "Failed to decrypt $message with ${key.toByteString()}\n${e.asLog()}" }
                null
            }

            log(
                TAG,
                Logging.Priority.VERBOSE
            ) { "Decrypted $message with ${key.toByteString()} to ${decryptedData?.toByteString()}" }

            if (decryptedData == null || decryptedData.size != 16) return null

            return decryptedData.toUByteArray()
        }

        companion object {
            private val TAG = logTag("Pod", "Factory", "Decrypter")
        }
    }
}