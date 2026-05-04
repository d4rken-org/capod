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
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

        log(TAG) { "AAP connecting to $address (${profile.label})" }
        try {
            aapManager.connect(address, bonded.internal!!, profile.model)
            log(TAG) { "AAP connected to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "AAP initial connect failed for $address: ${e.message}" }

            for ((attempt, delayMs) in RETRY_DELAYS.withIndex()) {
                delay(delayMs)

                // Bail out if classic BT disconnected
                val currentConnected = bluetoothManager.connectedDevices.first().map { it.address }.toSet()
                if (address !in currentConnected) {
                    log(TAG) { "AAP initial retry: $address no longer classically connected, stopping" }
                    break
                }

                val retryState = aapManager.allStates.value[address]
                if (retryState != null && retryState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
                    log(TAG) { "AAP initial retry: $address already reconnected, stopping" }
                    break
                }

                try {
                    log(TAG) { "AAP initial retry ${attempt + 1} for $address after ${delayMs}ms" }
                    aapManager.connect(address, bonded.internal!!, profile.model)
                    log(TAG) { "AAP connected to $address on retry ${attempt + 1}" }
                    break
                } catch (retryException: Exception) {
                    log(TAG, WARN) { "AAP initial retry ${attempt + 1} failed for $address: ${retryException.message}" }
                }
            }
        }
    }

    private fun reconnectOnDisconnect(): Flow<Unit> = aapManager.disconnectEvents
        .onEach { address ->
            if (!activeReconnects.add(address)) {
                log(TAG, VERBOSE) { "AAP reconnect already in progress for $address, skipping" }
                return@onEach
            }

            try {
                for ((attempt, delayMs) in RETRY_DELAYS.withIndex()) {
                    delay(delayMs)

                    // Check if still profiled
                    val profile = profilesRepo.profiles.first()
                        .firstOrNull { it.address == address }
                    if (profile == null) {
                        log(TAG) { "AAP reconnect: $address no longer profiled, stopping" }
                        break
                    }

                    // Check if still bonded
                    val bonded = bluetoothManager.bondedDevices().first()
                        .firstOrNull { it.address == address }
                    if (bonded == null) {
                        log(TAG) { "AAP reconnect: $address no longer bonded, stopping" }
                        break
                    }

                    // Check if still classically connected
                    val currentConnected = bluetoothManager.connectedDevices.first().map { it.address }.toSet()
                    if (address !in currentConnected) {
                        log(TAG) { "AAP reconnect: $address no longer classically connected, stopping" }
                        break
                    }

                    // Check if still visible in BLE
                    val bleDevices = blePodMonitor.devices.first()
                    if (bleDevices.none { it.meta?.profile?.address == address }) {
                        log(TAG) { "AAP reconnect: $address no longer visible in BLE, stopping" }
                        break
                    }

                    // Check if already reconnected (e.g., by initialConnect)
                    val currentState = aapManager.allStates.value[address]
                    if (currentState != null && currentState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
                        log(TAG) { "AAP reconnect: $address already reconnected" }
                        break
                    }

                    try {
                        log(TAG) { "AAP reconnect attempt ${attempt + 1} for $address in ${delayMs}ms" }
                        aapManager.connect(address, bonded.internal!!, profile.model)
                        log(TAG) { "AAP reconnected to $address" }
                        break
                    } catch (e: Exception) {
                        log(TAG, WARN) { "AAP reconnect attempt ${attempt + 1} failed for $address: ${e.message}" }
                    }
                }
            } finally {
                activeReconnects.remove(address)
            }
        }
        .map { } // SharedFlow<BluetoothAddress> → Flow<Unit>
        .setupCommonEventHandlers(TAG) { "reconnect" }

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
    }
}
