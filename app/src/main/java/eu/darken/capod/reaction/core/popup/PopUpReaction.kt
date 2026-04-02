package eu.darken.capod.reaction.core.popup

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.flow.withPrevious
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.reaction.core.ReactionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val deviceMonitor: DeviceMonitor,
    private val reactionSettings: ReactionSettings,
    private val bluetoothManager: BluetoothManager2,
) {

    private val caseCoolDowns = mutableMapOf<String, Instant>()

    private fun monitorCase(): Flow<Event> = reactionSettings.showPopUpOnCaseOpen.flow
        .flatMapLatest { isEnabled ->
            if (isEnabled) {
                deviceMonitor.primaryDevice().distinctUntilChangedBy { it?.rawDataHex }
            } else {
                emptyFlow()
            }
        }
        .withPrevious()
        .setupCommonEventHandlers(TAG) { "popUpCase" }
        .mapNotNull { (previous, current) ->
            if (current?.caseLidState == null) {
                return@mapNotNull null
            }
            log(TAG, VERBOSE) {
                "previous=${previous?.caseLidState}, current=${current.caseLidState}"
            }
            log(TAG, VERBOSE) { "previous-id=${previous?.identifier}, current-id=${current.identifier}" }

            val isSameDeviceOrProfile = previous?.identifier == current.identifier ||
                    (previous?.profileId != null && previous.profileId == current.profileId)
            val isSameDeviceWithCaseNowOpen = isSameDeviceOrProfile && previous?.caseLidState != current.caseLidState
            val isNewDeviceWithJustOpenedCase = !isSameDeviceOrProfile && previous?.caseLidState != current.caseLidState

            if (!isSameDeviceWithCaseNowOpen && !isNewDeviceWithJustOpenedCase) {
                return@mapNotNull null
            }
            log(TAG) { "Case lid status changed for monitored device." }

            throttleCasePopUps(current)
        }

    private fun throttleCasePopUps(current: PodDevice): Event? {
        val cooldownKey = current.profileId ?: current.identifier.toString()
        val now = Instant.now()
        val lastShown = caseCoolDowns[cooldownKey]

        val decision = evaluateCasePopUp(
            currentLidState = current.caseLidState,
            lastShownTime = lastShown,
            now = now,
        )

        log(TAG) { "Case popup decision: ${decision.reason}" }

        if (decision.shouldResetCooldown) {
            caseCoolDowns.remove(cooldownKey)
        }

        return when {
            decision.shouldShow -> {
                caseCoolDowns[cooldownKey] = now
                Event.PopupShow(device = current)
            }

            decision.shouldHide -> {
                if (!decision.shouldResetCooldown) {
                    caseCoolDowns[cooldownKey] = now
                }
                Event.PopupHide()
            }

            else -> null
        }
    }

    private val connectionCoolDowns = mutableMapOf<BluetoothAddress, Instant>()

    private fun monitorConnection(): Flow<Event> = reactionSettings.showPopUpOnConnection.flow
        .flatMapLatest { isEnabled ->
            if (!isEnabled) return@flatMapLatest emptyFlow()

            combine(
                bluetoothManager.connectedDevices,
                deviceMonitor.primaryDevice().distinctUntilChangedBy { it?.rawDataHex },
            ) { devices, broadcast ->
                log(TAG) { "$broadcast $devices " }
                val primaryAddr = broadcast?.address
                val direct = devices.singleOrNull { it.address == primaryAddr }.also {
                    log(TAG, VERBOSE) { "Connected main device is $it" }
                }
                if (direct == null) {
                    connectionCoolDowns.remove(primaryAddr).also {
                        if (it != null) log(TAG) { "Cleared connection cooldown for $primaryAddr due to disconect" }
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
                return@mapNotNull null
            }

            val deviceSeenFirst = currentBroadcasted.seenFirstAt ?: return@mapNotNull null
            val deviceAge = Duration.between(deviceSeenFirst, Instant.now())
            val connectionAge = Duration.between(currentConnected.seenFirstAt, Instant.now())

            val decision = evaluateConnectionPopUp(
                hasConnectedDevice = true,
                hasPodDevice = true,
                hasAlreadyShown = connectionCoolDowns.containsKey(currentConnected.address),
                deviceAge = deviceAge,
                connectionAge = connectionAge,
            )

            log(TAG) { "Connection popup decision: ${decision.reason}" }

            if (decision.shouldShow) {
                connectionCoolDowns[currentConnected.address] = Instant.now()
                Event.PopupShow(device = currentBroadcasted)
            } else {
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

    internal fun evaluateCasePopUp(
        currentLidState: DualApplePods.LidState?,
        lastShownTime: Instant?,
        now: Instant,
        cooldownDuration: Duration = Duration.ofSeconds(10),
    ): CasePopUpDecision {
        if (currentLidState == null) {
            return CasePopUpDecision(
                shouldShow = false,
                shouldHide = false,
                shouldResetCooldown = false,
                reason = "Non-DualApplePods device",
            )
        }
        return when (currentLidState) {
            DualApplePods.LidState.OPEN -> {
                val sinceLastPop = lastShownTime?.let { Duration.between(it, now) }
                if (sinceLastPop == null || sinceLastPop >= cooldownDuration) {
                    CasePopUpDecision(
                        shouldShow = true,
                        shouldHide = false,
                        shouldResetCooldown = false,
                        reason = "Lid OPEN, cooldown expired or first show",
                    )
                } else {
                    CasePopUpDecision(
                        shouldShow = false,
                        shouldHide = false,
                        shouldResetCooldown = false,
                        reason = "Lid OPEN, still on cooldown ($sinceLastPop)",
                    )
                }
            }

            DualApplePods.LidState.CLOSED -> CasePopUpDecision(
                shouldShow = false,
                shouldHide = true,
                shouldResetCooldown = true,
                reason = "Lid CLOSED, resetting cooldown",
            )

            else -> CasePopUpDecision(
                shouldShow = false,
                shouldHide = true,
                shouldResetCooldown = false,
                reason = "Lid $currentLidState, refreshing cooldown",
            )
        }
    }

    data class CasePopUpDecision(
        val shouldShow: Boolean,
        val shouldHide: Boolean,
        val shouldResetCooldown: Boolean,
        val reason: String,
    )

    internal fun evaluateConnectionPopUp(
        hasConnectedDevice: Boolean,
        hasPodDevice: Boolean,
        hasAlreadyShown: Boolean,
        deviceAge: Duration,
        connectionAge: Duration,
        maxAgeDiff: Duration = Duration.ofSeconds(30),
    ): ConnectionPopUpDecision {
        if (!hasConnectedDevice) {
            return ConnectionPopUpDecision(false, "No connected device")
        }
        if (!hasPodDevice) {
            return ConnectionPopUpDecision(false, "No pod device found")
        }
        if (deviceAge.abs() > (connectionAge.abs() + maxAgeDiff)) {
            return ConnectionPopUpDecision(false, "Broadcast too old, likely false positive")
        }
        if (hasAlreadyShown) {
            return ConnectionPopUpDecision(false, "Already shown for this connection")
        }
        return ConnectionPopUpDecision(true, "New connection detected")
    }

    data class ConnectionPopUpDecision(
        val shouldShow: Boolean,
        val reason: String,
    )

    companion object {
        private val TAG = logTag("Reaction", "PopUp")
    }
}
