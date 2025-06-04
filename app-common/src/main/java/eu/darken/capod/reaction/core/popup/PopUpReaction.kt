package eu.darken.capod.reaction.core.popup

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.reaction.core.ReactionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpReaction @Inject constructor(
    private val podMonitor: PodMonitor,
    private val reactionSettings: ReactionSettings,
    private val generalSettings: GeneralSettings,
    private val bluetoothManager: BluetoothManager2,
) {

    private val caseCoolDowns = mutableMapOf<PodDevice.Id, Instant>()

    private fun monitorCase(): Flow<Event> = reactionSettings.showPopUpOnCaseOpen.flow
        .flatMapLatest { isEnabled ->
            if (isEnabled) {
                podMonitor.mainDevice.distinctUntilChangedBy { it?.rawDataHex }
            } else {
                emptyFlow()
            }
        }
        .withPrevious()
        .setupCommonEventHandlers(TAG) { "popUpCase" }
        .mapNotNull { (previous, current) ->
            if (previous !is DualApplePods? || current !is DualApplePods) {
                return@mapNotNull null
            }
            log(TAG, VERBOSE) {
                val prev = previous?.pubCaseLidState?.let { String.format("%02X", it.toByte()) }
                val cur = current.pubCaseLidState.let { String.format("%02X", it.toByte()) }
                "previous=$prev (${previous?.caseLidState}), current=$cur (${current.caseLidState})"
            }
            log(TAG, VERBOSE) { "previous-id=${previous?.identifier}, current-id=${current.identifier}" }

            val isSameDeviceWithCaseNowOpen =
                previous?.identifier == current.identifier && previous.caseLidState != current.caseLidState
            val isNewDeviceWithJustOpenedCase =
                previous?.identifier != current.identifier && previous?.caseLidState != current.caseLidState

            if (!isSameDeviceWithCaseNowOpen && !isNewDeviceWithJustOpenedCase) {
                return@mapNotNull null
            }
            log(TAG) { "Case lid status changed for monitored device." }

            throttleCasePopUps(current)
        }

    private fun throttleCasePopUps(current: DualApplePods): Event? = when {
        current.caseLidState == DualApplePods.LidState.OPEN -> {
            log(TAG, INFO) { "Show popup" }

            val now = Instant.now()
            val lastShown = caseCoolDowns[current.identifier] ?: Instant.MIN
            val sinceLastPop = Duration.between(lastShown, now)
            log(TAG) { "Time since last case popup: $sinceLastPop" }

            if (sinceLastPop >= Duration.ofSeconds(10)) {
                caseCoolDowns[current.identifier] = Instant.now()
                Event.PopupShow(device = current)
            } else {
                log(TAG, INFO) { "Case popup is still on cooldown: $sinceLastPop" }
                null
            }
        }

        current.caseLidState != DualApplePods.LidState.OPEN -> {
            when (current.caseLidState) {
                DualApplePods.LidState.CLOSED -> {
                    log(TAG, INFO) { "Lid was actively closed, resetting cooldown." }
                    caseCoolDowns.remove(current.identifier)
                }

                else -> {
                    log(TAG, WARN) { "Lid was was not actively closed, refreshing cooldown." }
                    caseCoolDowns[current.identifier] = Instant.now()
                }
            }

            log(TAG, INFO) { "Hide popup" }

            Event.PopupHide()
        }

        else -> null
    }

    private val connectionCoolDowns = mutableMapOf<String, Instant>()

    private fun monitorConnection(): Flow<Event> = reactionSettings.showPopUpOnConnection.flow
        .flatMapLatest { isEnabled ->
            if (!isEnabled) return@flatMapLatest emptyFlow()

            combine(
                generalSettings.mainDeviceAddress.flow,
                bluetoothManager.connectedDevices().distinctUntilChanged(),
                podMonitor.mainDevice.distinctUntilChangedBy { it?.rawDataHex },
            ) { targetAddress, devices, broadcast ->
                log(TAG) { "$targetAddress $broadcast $devices " }
                val direct = devices.singleOrNull { it.address == targetAddress }.also {
                    log(TAG, VERBOSE) { "Connected main device is $it" }
                }
                if (direct == null) {
                    connectionCoolDowns.remove(targetAddress).also {
                        if (it != null) log(TAG) { "Cleared connection cooldown for $targetAddress due to disconect" }
                    }
                }
                if (direct != null && broadcast != null) direct to broadcast else null
            }
        }
        .withPrevious()
        .mapNotNull { (previouss, currents) ->
            val previousConnected = previouss?.first
            log(TAG, VERBOSE) { "previousConnected: $previousConnected" }
            val previousBroadcasted = previouss?.second
            log(TAG, VERBOSE) { "previousBroadcasted: $previousBroadcasted" }
            val currentConnected = currents?.first
            log(TAG, VERBOSE) { "currentConnected: $currentConnected" }
            val currentBroadcasted = currents?.second
            log(TAG, VERBOSE) { "currentBroadcasted: $currentBroadcasted" }

            if (previousConnected != null && previousBroadcasted != null && currentConnected == null) {
                return@mapNotNull Event.PopupHide()
            }

            if (currentConnected == null || currentBroadcasted == null) {
                // We need an active connection
                return@mapNotNull null
            }

            val ageOfBroadcastedDevice = Duration.between(Instant.now(), currentBroadcasted.seenFirstAt)
            val ageOfConnectedDevice = Duration.between(Instant.now(), currentConnected.seenFirstAt)
            if (ageOfBroadcastedDevice > (ageOfConnectedDevice + Duration.ofSeconds(30))) {
                // This is likely a false positive, some random nearby device
                // We expect the first broadcasts to not be much older than the first connection
                log(TAG, VERBOSE) { "Current broadcasted main device is probably a false-positive" }
                return@mapNotNull null
            }

            val now = Instant.now()
            val lastShown = connectionCoolDowns[currentConnected.address]
            val sinceLastPop = lastShown?.let { Duration.between(it, now) }
            log(TAG) { "Time since last connection popup: ${sinceLastPop?.seconds}s" }

            if (lastShown == null) {
                connectionCoolDowns[currentConnected.address] = Instant.now()
                Event.PopupShow(device = currentBroadcasted)
            } else {
                log(TAG) { "Connection popup is still on cooldown: $sinceLastPop" }
                null
            }
        }
        .setupCommonEventHandlers(TAG) { "popUpConnection" }

    fun monitor(): Flow<Event> = merge(monitorCase(), monitorConnection())

    sealed class Event {
        data class PopupShow(
            val eventAt: Instant = Instant.now(),
            val device: PodDevice,
        ) : Event()

        data class PopupHide(
            val eventAt: Instant = Instant.now(),
        ) : Event()
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp")
    }
}