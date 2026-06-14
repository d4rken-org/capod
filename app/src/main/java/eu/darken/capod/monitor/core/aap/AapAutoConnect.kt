package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapDisconnectEvent
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class AapAutoConnect @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
    private val blePodMonitor: BlePodMonitor,
) {
    private val activeReconnects = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val processedModelCorrections = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Per-address count of consecutive connection attempts that never reached READY (failed socket
     * connects and handshake-timeout disconnects). Drives the escalating reconnect cooldown so a
     * device that keeps stalling its handshake in a crowded RF environment doesn't churn a tight
     * reconnect loop. Reset to 0 once a session works (a was-ready disconnect) or the device leaves.
     */
    private val preReadyFailures = ConcurrentHashMap<String, Int>()

    /** Cooldown (ms) to wait before the next attempt, given how many consecutive pre-READY failures we've seen. */
    private fun cooldownFor(address: String): Long {
        val failures = preReadyFailures[address] ?: 0
        if (failures <= 0) return 0L
        return HANDSHAKE_BACKOFF[minOf(failures - 1, HANDSHAKE_BACKOFF.lastIndex)]
    }

    fun monitor(): Flow<Unit> = merge(
        initialConnect(),
        reconnectOnDisconnect(),
        correctModelOnDeviceInfo(),
    )

    private fun initialConnect(): Flow<Unit> = combine(
        profilesRepo.profiles,
        bluetoothManager.connectedDevices,
    ) { profiles, connectedDevices ->
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        profiles.filter { it.address in connectedAddresses }
    }
        .mapLatest { profiles ->
            val bondedDevices = bluetoothManager.bondedDevices().first()

            coroutineScope {
                for (profile in profiles) {
                    launch { connectWithRetries(profile, bondedDevices) }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "initialConnect" }

    private suspend fun connectWithRetries(
        profile: eu.darken.capod.profiles.core.DeviceProfile,
        bondedDevices: Set<eu.darken.capod.common.bluetooth.BluetoothDevice2>,
    ) {
        val address = profile.address ?: return
        val bonded = bondedDevices.firstOrNull { it.address == address } ?: return

        val currentState = aapManager.allStates.value[address]
        if (currentState != null && currentState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
            log(TAG, VERBOSE) { "AAP already connected/connecting to $address" }
            return
        }

        // Escalating cooldown carried over from prior failed attempts (socket failures or handshake
        // stalls). 0 on the first attempt so a healthy device connects without delay.
        val cooldown = cooldownFor(address)
        if (cooldown > 0) {
            log(TAG) { "AAP initial connect cooldown ${cooldown}ms for $address (failures=${preReadyFailures[address]})" }
            delay(cooldown)
        }

        log(TAG) { "AAP connecting to $address (${profile.label})" }
        try {
            aapManager.connect(address, bonded.internal!!, profile.model)
            log(TAG) { "AAP connected to $address" }
            return
        } catch (e: Exception) {
            log(TAG, WARN) { "AAP initial connect failed for $address: ${e.message}" }

            for ((attempt, delayMs) in RETRY_DELAYS.withIndex()) {
                delay(delayMs)

                // Bail out if classic BT disconnected — device left, clear the penalty
                val currentConnected = bluetoothManager.connectedDevices.first().map { it.address }.toSet()
                if (address !in currentConnected) {
                    log(TAG) { "AAP initial retry: $address no longer classically connected, stopping" }
                    preReadyFailures.remove(address)
                    return
                }

                val retryState = aapManager.allStates.value[address]
                if (retryState != null && retryState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
                    log(TAG) { "AAP initial retry: $address already reconnected, stopping" }
                    return
                }

                try {
                    log(TAG) { "AAP initial retry ${attempt + 1} for $address after ${delayMs}ms" }
                    aapManager.connect(address, bonded.internal!!, profile.model)
                    log(TAG) { "AAP connected to $address on retry ${attempt + 1}" }
                    return
                } catch (retryException: Exception) {
                    log(TAG, WARN) { "AAP initial retry ${attempt + 1} failed for $address: ${retryException.message}" }
                }
            }

            // Every attempt this episode failed at the socket level — escalate the next round's cooldown.
            preReadyFailures.merge(address, 1, Int::plus)
        }
    }

    // Process each disconnect on its own child coroutine so a long per-address backoff never blocks
    // another device's reconnect (the collector would otherwise serialise events). channelFlow gives
    // us a scope to launch into; it intentionally emits nothing — it stays subscribed for its lifetime.
    private fun reconnectOnDisconnect(): Flow<Unit> = channelFlow<Unit> {
        aapManager.disconnectEvents.collect { event ->
            launch { handleReconnect(event) }
        }
    }.setupCommonEventHandlers(TAG) { "reconnect" }

    private suspend fun handleReconnect(event: AapDisconnectEvent) {
        val address = event.address

        // A session that actually worked resets the penalty (prompt reconnect). One that never reached
        // READY is a failed handshake/short session — escalate so a persistent stall backs off.
        if (event.wasEverReady) {
            preReadyFailures.remove(address)
        } else {
            preReadyFailures.merge(address, 1, Int::plus)
        }

        if (!activeReconnects.add(address)) {
            log(TAG, VERBOSE) { "AAP reconnect already in progress for $address, skipping" }
            return
        }

        try {
            // Escalating cooldown before the normal retry cadence when handshakes keep stalling.
            val cooldown = cooldownFor(address)
            if (cooldown > 0) {
                log(TAG) { "AAP reconnect cooldown ${cooldown}ms for $address (failures=${preReadyFailures[address]})" }
                delay(cooldown)
            }

            for ((attempt, delayMs) in RETRY_DELAYS.withIndex()) {
                delay(delayMs)

                // Check if still profiled
                val profile = profilesRepo.profiles.first()
                    .firstOrNull { it.address == address }
                if (profile == null) {
                    log(TAG) { "AAP reconnect: $address no longer profiled, stopping" }
                    preReadyFailures.remove(address)
                    return
                }

                // Check if still bonded
                val bonded = bluetoothManager.bondedDevices().first()
                    .firstOrNull { it.address == address }
                if (bonded == null) {
                    log(TAG) { "AAP reconnect: $address no longer bonded, stopping" }
                    preReadyFailures.remove(address)
                    return
                }

                // Check if still classically connected
                val currentConnected = bluetoothManager.connectedDevices.first().map { it.address }.toSet()
                if (address !in currentConnected) {
                    log(TAG) { "AAP reconnect: $address no longer classically connected, stopping" }
                    preReadyFailures.remove(address)
                    return
                }

                // Check if still visible in BLE
                val bleDevices = blePodMonitor.devices.first()
                if (bleDevices.none { it.meta?.profile?.address == address }) {
                    log(TAG) { "AAP reconnect: $address no longer visible in BLE, stopping" }
                    preReadyFailures.remove(address)
                    return
                }

                // Check if already reconnected (e.g., by initialConnect)
                val currentState = aapManager.allStates.value[address]
                if (currentState != null && currentState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
                    log(TAG) { "AAP reconnect: $address already reconnected" }
                    return
                }

                try {
                    log(TAG) { "AAP reconnect attempt ${attempt + 1} for $address in ${delayMs}ms" }
                    aapManager.connect(address, bonded.internal!!, profile.model)
                    log(TAG) { "AAP reconnected to $address" }
                    return
                } catch (e: Exception) {
                    log(TAG, WARN) { "AAP reconnect attempt ${attempt + 1} failed for $address: ${e.message}" }
                }
            }

            // Every attempt threw at the socket level (the device is still present but won't accept a
            // socket) — escalate so we don't hammer it. Mirrors connectWithRetries. Handshake stalls
            // don't reach here: their reconnect socket succeeds and we return above; they escalate via
            // the never-ready disconnect event instead.
            preReadyFailures.merge(address, 1, Int::plus)
        } finally {
            activeReconnects.remove(address)
        }
    }

    private fun correctModelOnDeviceInfo(): Flow<Unit> = aapManager.allStates
        .map { states -> states.mapValues { (_, state) -> state.deviceInfo?.modelNumber } }
        .distinctUntilChanged()
        .map { modelNumbers ->
            // Clean up tracking for addresses that disconnected
            processedModelCorrections.retainAll(modelNumbers.keys)

            for ((address, modelNumber) in modelNumbers) {
                if (modelNumber == null) continue
                if (address in processedModelCorrections) continue
                processedModelCorrections.add(address)

                try {
                    val detectedModel = PodModel.fromModelNumber(modelNumber) ?: continue

                    val profile = profilesRepo.profiles.first()
                        .filterIsInstance<AppleDeviceProfile>()
                        .firstOrNull { it.address == address }
                        ?: continue

                    if (profile.model == detectedModel) {
                        log(TAG, VERBOSE) { "AAP model confirmed for $address: $detectedModel" }
                        continue
                    }

                    log(TAG) { "AAP model mismatch for $address: profile=${profile.model}, detected=$detectedModel" }

                    // Allow key exchange (AapKeyPersister) to complete before disconnecting
                    delay(2.seconds)

                    aapManager.disconnect(address)
                    profilesRepo.updateProfile(profile.copy(model = detectedModel))
                    log(TAG) { "AAP model corrected for $address: ${profile.model} -> $detectedModel" }

                    // Reconnect explicitly
                    val bonded = bluetoothManager.bondedDevices().first()
                        .firstOrNull { it.address == address }
                    if (bonded != null) {
                        aapManager.connect(address, bonded.internal!!, detectedModel)
                        log(TAG) { "AAP reconnected $address with corrected model $detectedModel" }
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "AAP model correction failed for $address: ${e.message}" }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "correctModel" }

    companion object {
        private val TAG = logTag("Monitor", "AapAutoConnect")
        internal val RETRY_DELAYS = longArrayOf(3_000, 3_000, 3_000, 5_000, 5_000, 10_000, 10_000)

        /**
         * Escalating cooldown (ms) applied before a reconnect/connect attempt, indexed by
         * consecutive pre-READY failures minus one. Caps at 60s so a device that perpetually stalls
         * its handshake in crowded RF settles into a slow ~minute cadence instead of a tight loop.
         */
        internal val HANDSHAKE_BACKOFF = longArrayOf(5_000, 15_000, 30_000, 60_000)
    }
}
