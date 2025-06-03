package eu.darken.capod.pods.core.apple.protocol

import android.annotation.SuppressLint
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import okio.ByteString.Companion.toByteString
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class MessageDecrypter @Inject constructor() {

    @SuppressLint("GetInstance")
    fun decrypt(message: ProximityPairing.Message, key: ByteArray): UByteArray? {
        val data = message.data.toByteArray()
        if (data.size < 16) return null

        val decryptedData = try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
                val secretKey = SecretKeySpec(key, "AES")
                init(Cipher.DECRYPT_MODE, secretKey)
            }
            cipher.doFinal(data.copyOfRange(data.size - 16, data.size))
        } catch (e: Exception) {
            log(TAG, Logging.Priority.ERROR) { "Failed to decrypt $message with ${key.toByteString()}\n${e.asLog()}" }
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