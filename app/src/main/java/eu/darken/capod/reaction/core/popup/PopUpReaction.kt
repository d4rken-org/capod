package eu.darken.capod.reaction.core.popup

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothDevice2
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpReaction @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val bluetoothManager: BluetoothManager2,
    private val timeSource: TimeSource,
) {

    private val caseCoolDowns = java.util.concurrent.ConcurrentHashMap<String, Instant>()

    private fun monitorCase(): Flow<Event> = deviceMonitor.primaryDevice()
        .distinctUntilChangedBy {
            // Re-emit on profile changes (eligibility) AND on raw BLE state changes (lid).
            Triple(it?.profileId, it?.reactions?.showPopUpOnCaseOpen, it?.rawDataHex)
        }
        .withPrevious()
        .setupCommonEventHandlers(TAG) { "popUpCase" }
        .mapNotNull { (previous, current) ->
            val wasEligible = previous?.reactions?.showPopUpOnCaseOpen == true
            val isEligible = current?.reactions?.showPopUpOnCaseOpen == true

            // Eligibility transition: just lost it (toggled off, or switched to a profile
            // with the toggle off). Dismiss any visible overlay immediately.
            if (wasEligible && !isEligible) {
                log(TAG) { "Case popup eligibility lost, emitting Hide" }
                return@mapNotNull Event.PopupHide(timeSource.now())
            }

            if (!isEligible) return@mapNotNull null
            if (current.caseLidState == null) return@mapNotNull null

            log(TAG, VERBOSE) {
                "previous=${previous?.caseLidState}, current=${current.caseLidState}"
            }
            val isSameDeviceOrProfile = previous?.profileId != null && previous.profileId == current.profileId
            val isSameDeviceWithCaseNowOpen = isSameDeviceOrProfile && previous?.caseLidState != current.caseLidState
            val isNewDeviceWithJustOpenedCase = !isSameDeviceOrProfile
                && previous != null
                && previous.caseLidState != null
                && previous.caseLidState != current.caseLidState

            if (!isSameDeviceWithCaseNowOpen && !isNewDeviceWithJustOpenedCase) {
                return@mapNotNull null
            }
            log(TAG) { "Case lid status changed for monitored device." }

            throttleCasePopUps(current)
        }

    private fun throttleCasePopUps(current: PodDevice): Event? {
        val cooldownKey = current.profileId ?: current.identifier?.toString() ?: return null
        val now = timeSource.now()
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
                Event.PopupShow(eventAt = now, device = current)
            }

            decision.shouldHide -> {
                if (!decision.shouldResetCooldown) {
                    caseCoolDowns[cooldownKey] = now
                }
                Event.PopupHide(now)
            }

            else -> null
        }
    }

    private val connectionCoolDowns = java.util.concurrent.ConcurrentHashMap<BluetoothAddress, Instant>()

    private data class ConnectionWindow(
        val direct: BluetoothDevice2?,
        val broadcast: PodDevice?,
        val eligible: Boolean,
    )

    private fun monitorConnection(): Flow<Event> = combine(
        bluetoothManager.connectedDevices,
        deviceMonitor.primaryDevice().distinctUntilChangedBy {
            Triple(it?.profileId, it?.reactions?.showPopUpOnConnection, it?.rawDataHex)
        },
    ) { devices, broadcast ->
        val eligible = broadcast?.reactions?.showPopUpOnConnection == true
        if (!eligible) {
            ConnectionWindow(direct = null, broadcast = null, eligible = false)
        } else {
            val primaryAddr = broadcast.address
                ?: return@combine ConnectionWindow(direct = null, broadcast = null, eligible = false)
            val direct = devices.singleOrNull { it.address == primaryAddr }.also {
                log(TAG, VERBOSE) { "Connected main device is $it" }
            }
            if (direct == null) {
                connectionCoolDowns.remove(primaryAddr).also {
                    if (it != null) log(TAG) { "Cleared connection cooldown for $primaryAddr due to disconnect" }
                }
            }
            ConnectionWindow(direct = direct, broadcast = broadcast, eligible = true)
        }
    }
        .withPrevious()
        .mapNotNull { (previous, current) ->
            val wasEligible = previous?.eligible == true
            val isEligible = current.eligible

            // Eligibility transition: dismiss any visible overlay immediately.
            if (wasEligible && !isEligible) {
                log(TAG) { "Connection popup eligibility lost, emitting Hide" }
                return@mapNotNull Event.PopupHide(timeSource.now())
            }

            if (!isEligible) return@mapNotNull null

            val previousConnected = previous?.direct
            log(TAG, VERBOSE) { "previousConnected: $previousConnected" }
            val previousBroadcasted = previous?.broadcast
            log(TAG, VERBOSE) { "previousBroadcasted: $previousBroadcasted" }
            val currentConnected = current.direct
            log(TAG, VERBOSE) { "currentConnected: $currentConnected" }
            val currentBroadcasted = current.broadcast
            log(TAG, VERBOSE) { "currentBroadcasted: $currentBroadcasted" }

            if (previousConnected != null && previousBroadcasted != null && currentConnected == null) {
                return@mapNotNull Event.PopupHide(timeSource.now())
            }

            if (currentConnected == null || currentBroadcasted == null) {
                return@mapNotNull null
            }

            val deviceSeenFirst = currentBroadcasted.seenFirstAt ?: return@mapNotNull null
            val now = timeSource.now()
            val deviceAge = Duration.between(deviceSeenFirst, now)
            val connectionAge = Duration.between(currentConnected.seenFirstAt, now)

            val decision = evaluateConnectionPopUp(
                hasConnectedDevice = true,
                hasPodDevice = true,
                hasAlreadyShown = connectionCoolDowns.containsKey(currentConnected.address),
                deviceAge = deviceAge,
                connectionAge = connectionAge,
            )

            log(TAG) { "Connection popup decision: ${decision.reason}" }

            if (decision.shouldShow) {
                connectionCoolDowns[currentConnected.address] = now
                Event.PopupShow(eventAt = now, device = currentBroadcasted)
            } else {
                null
            }
        }
        .setupCommonEventHandlers(TAG) { "popUpConnection" }

    fun monitor(): Flow<Event> = merge(monitorCase(), monitorConnection())

    sealed class Event {
        data class PopupShow(
            val eventAt: Instant,
            val device: PodDevice,
        ) : Event()

        data class PopupHide(
            val eventAt: Instant,
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
