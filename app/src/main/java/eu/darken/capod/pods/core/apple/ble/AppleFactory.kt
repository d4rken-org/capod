package eu.darken.capod.pods.core.apple.ble

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.logSummary
import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.misc.UnknownAppleSnapshotBle
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import eu.darken.capod.pods.core.apple.ble.protocol.RPAChecker
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
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
    private val unknownAppleFactory: UnknownAppleSnapshotBle.Factory,
    private val rpaChecker: RPAChecker,
    private val profilesRepo: DeviceProfilesRepo,
) {

    private val lock = Mutex()

    private fun getMessage(scanResult: BleScanResult): ProximityMessage? {
        val messages = try {
            continuityProtocolDecoder.decode(scanResult)
        } catch (e: Exception) {
            log(TAG, VERBOSE) { "Not a continuity payload: ${scanResult.logSummary()} (${e.javaClass.simpleName})" }
            return null
        }
        if (messages.isEmpty()) {
            log(TAG, VERBOSE) { "No continuity messages in ${scanResult.logSummary()}" }
            return null
        }

        if (messages.size > 1) {
            log(TAG, DEBUG) {
                "Decoded ${messages.size} continuity messages, picking first for ${scanResult.logSummary()}"
            }
        }

        val proximityMessage = proximityPairingDecoder.decode(messages.first())
        if (proximityMessage == null) {
            log(TAG, VERBOSE) { "Not a proximity pairing message for ${scanResult.logSummary()}" }
            return null
        }

        return proximityMessage
    }

    suspend fun create(scanResult: BleScanResult): BlePodSnapshot? = lock.withLock {
        val proximityMessage = getMessage(scanResult) ?: return@withLock null
        val factory = podFactories.firstOrNull { it.isResponsible(proximityMessage) } ?: unknownAppleFactory

        val profiles = profilesRepo.currentProfiles().filterIsInstance<AppleDeviceProfile>()
        var profile = profiles.firstOrNull {
            it.identityKey != null && rpaChecker.verify(scanResult.address, it.identityKey)
        }

        val isIrkMatch = profile != null
        if (isIrkMatch) {
            log(TAG, VERBOSE) { "IRK match for ${scanResult.logSummary()} -> ${profile?.logSummary()}" }
        }

        var payload = ProximityPayload(
            public = ProximityPayload.Public(
                proximityMessage.data.take(9).toUByteArray()
            ),
            private = null,
        )

        if (profile != null && profile.encryptionKey != null) {
            payload = payload.copy(
                private = run {
                    if (proximityMessage.data.size != ProximityPairing.PAIRING_MESSAGE_LENGTH) return@run null
                    val encKey = profile.encryptionKey
                    val encrypted = proximityMessage.data.takeLast(16).toUByteArray().toByteArray()
                    proximityMessageDecrypter.decrypt(encrypted, encKey)?.let {
                        ProximityPayload.Private(data = it)
                    }
                },
            )
        }

        if (profile == null) {
            val tempDevice = factory.create(
                scanResult = scanResult,
                payload = payload,
                meta = ApplePods.AppleMeta(),
            )
            profile = profiles
                .filter { it.identityKey == null }
                .filter { it.model == PodModel.UNKNOWN || it.model == tempDevice.model }
                .firstOrNull { it.minimumSignalQuality <= tempDevice.signalQuality }

            if (profile == null) {
                val legacyCandidate = profiles
                    .filter { it.identityKey != null && (it.model == PodModel.UNKNOWN || it.model == tempDevice.model) }
                    .firstOrNull { it.minimumSignalQuality <= tempDevice.signalQuality }
                if (legacyCandidate != null) {
                    log(TAG, WARN) { "Keyed profile ${legacyCandidate.id} would match via old fallback (IRK failed) — stale key?" }
                }
            }
        }

        factory.create(
            scanResult = scanResult,
            payload = payload,
            meta = ApplePods.AppleMeta(
                isIRKMatch = isIrkMatch,
                profile = profile,
            ),
        )
    }

    companion object {
        private val TAG = logTag("Pod", "Apple", "Factory")
    }
}
