package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.misc.UnknownAppleDevice
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import eu.darken.capod.pods.core.apple.protocol.RPAChecker
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleFactory @Inject constructor(
    private val continuityProtocolDecoder: ContinuityProtocol.Decoder,
    private val proximityPairingDecoder: ProximityPairing.Decoder,
    private val proximityMessageDecrypter: ProximityMessage.Decrypter,
    private val podFactories: @JvmSuppressWildcards Set<ApplePodsFactory>,
    private val unknownAppleFactory: UnknownAppleDevice.Factory,
    private val rpaChecker: RPAChecker,
    private val profilesRepo: DeviceProfilesRepo,
) {

    private val lock = Mutex()

    private fun getMessage(scanResult: BleScanResult): ProximityMessage? {
        val messages = try {
            continuityProtocolDecoder.decode(scanResult)
        } catch (e: Exception) {
            log(TAG, WARN) { "Data wasn't continuity protocol conform:\n${e.asLog()}" }
            return null
        }
        if (messages.isEmpty()) {
            log(TAG, WARN) { "Data contained no continuity messages: $scanResult" }
            return null
        }

        if (messages.size > 1) {
            log(TAG, WARN) { "Decoded multiple continuity messages, picking first: $messages" }
        }

        val proximityMessage = proximityPairingDecoder.decode(messages.first())
        if (proximityMessage == null) {
            log(TAG) { "Not a proximity pairing message: $messages" }
            return null
        }

        return proximityMessage
    }

    suspend fun create(scanResult: BleScanResult): PodDevice? = lock.withLock {
        val proximityMessage = getMessage(scanResult) ?: return@withLock null

        var isIRKMatch = false
        val candidates = profilesRepo.profiles.first().filterIsInstance<AppleDeviceProfile>()
        var profile = candidates.firstOrNull {
            it.identityKey != null && rpaChecker.verify(scanResult.address, it.identityKey)
        }
        if (profile != null) {
            isIRKMatch = true
            log(TAG, VERBOSE) { "Got IRK match for from $profile for $scanResult" }
        }

        val factory = podFactories.firstOrNull { it.isResponsible(proximityMessage) } ?: unknownAppleFactory

        // TODO more checks heuristicbased
        if (profile == null) {
            profile = candidates.firstOrNull { it.model == factory.deviceModel }
            log(TAG, VERBOSE) { "Got no IRK match for from $profile for $scanResult" }
        }

        val payload = ProximityPayload(
            public = ProximityPayload.Public(
                proximityMessage.data.take(9).toUByteArray()
            ),
            private = run {
                if (!isIRKMatch || profile == null) return@run null
                if (proximityMessage.data.size != ProximityPairing.PAIRING_MESSAGE_LENGTH) return@run null

                val encKey = profile.encryptionKey?.takeIf { it.isNotEmpty() }
                if (encKey == null) return@run null

                val encrypted = proximityMessage.data.takeLast(16).toUByteArray().toByteArray()
                proximityMessageDecrypter.decrypt(encrypted, encKey)?.let {
                    ProximityPayload.Private(data = it)
                }
            },
        )


        factory.create(
            scanResult = scanResult,
            payload = payload,
            meta = ApplePods.AppleMeta(
                isIRKMatch = isIRKMatch,
                profile = profile,
            ),
        )
    }

    companion object {
        private val TAG = logTag("Pod", "Apple", "Factory")
    }
}