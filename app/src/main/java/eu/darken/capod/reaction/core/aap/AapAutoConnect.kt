package eu.darken.capod.reaction.core.aap

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.BlePodMonitor
import eu.darken.capod.pods.core.apple.protocol.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.protocol.aap.AapPodState
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AapAutoConnect @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
    private val blePodMonitor: BlePodMonitor,
) {
    private val activeReconnects = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun monitor(): Flow<Unit> = merge(
        initialConnect(),
        reconnectOnDisconnect(),
    )

    private fun initialConnect(): Flow<Unit> = profilesRepo.profiles
        .map { profiles ->
            val bondedDevices = bluetoothManager.bondedDevices().first()

            for (profile in profiles) {
                val address = profile.address ?: continue
                val bonded = bondedDevices.firstOrNull { it.address == address } ?: continue

                val currentState = aapManager.allStates.value[address]
                if (currentState != null && currentState.connectionState != AapPodState.ConnectionState.DISCONNECTED) {
                    log(TAG, VERBOSE) { "AAP already connected/connecting to $address" }
                    continue
                }

                try {
                    log(TAG) { "AAP connecting to $address (${profile.label})" }
                    aapManager.connect(address, bonded.internal, profile.model)
                    log(TAG) { "AAP connected to $address" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "AAP connect failed for $address: ${e.message}" }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "initialConnect" }

    private fun reconnectOnDisconnect(): Flow<Unit> = aapManager.disconnectEvents
        .onEach { address ->
            if (!activeReconnects.add(address)) {
                log(TAG, VERBOSE) { "AAP reconnect already in progress for $address, skipping" }
                return@onEach
            }

            val backoffDelays = longArrayOf(5_000, 10_000, 30_000, 60_000)

            for ((attempt, delayMs) in backoffDelays.withIndex()) {
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

                // Check if still visible in BLE
                val bleDevices = blePodMonitor.devices.first()
                if (bleDevices.none { it.address == address }) {
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
                    aapManager.connect(address, bonded.internal, profile.model)
                    log(TAG) { "AAP reconnected to $address" }
                    break
                } catch (e: Exception) {
                    log(TAG, WARN) { "AAP reconnect attempt ${attempt + 1} failed for $address: ${e.message}" }
                }
            }

            activeReconnects.remove(address)
        }
        .map { } // SharedFlow<BluetoothAddress> → Flow<Unit>
        .setupCommonEventHandlers(TAG) { "reconnect" }

    companion object {
        private val TAG = logTag("Reaction", "AapAutoConnect")
    }
}
