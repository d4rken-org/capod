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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
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
            // Re-emit on profile changes (eligibility), raw BLE changes (content), AND the derived
            // lid state. The latter is essential: caseLidState is recovered from history, so it can
            // flip OPEN<->CLOSED while the *selected* frame's raw bytes stay identical (e.g. a steady
            // out-of-case frame while a sibling in-case frame updates). Keying on rawDataHex alone
            // would swallow that transition and the popup would miss its show/hide.
            listOf(it?.profileId, it?.reactions?.showPopUpOnCaseOpen, it?.rawDataHex, it?.caseLidState)
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

    internal fun throttleCasePopUps(current: PodDevice): Event? {
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
                // Don't stamp the cooldown on a non-CLOSED hide (UNKNOWN/NOT_IN_CASE). Refreshing it
                // here would let a transient UNKNOWN (e.g. a brief out-of-case frame) suppress a
                // genuine OPEN for the whole cooldown window. CLOSED still resets it above.
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

            // Use the most recent BLE advertisement, not the first time this device was ever seen.
            // Connection popups are meant to verify that a connection coincides with a fresh broadcast.
            val broadcastSeenLast = currentBroadcasted.ble?.seenLastAt ?: return@mapNotNull null
            val now = timeSource.now()
            val broadcastAge = Duration.between(broadcastSeenLast, now)
            val connectionAge = Duration.between(currentConnected.seenFirstAt, now)

            val decision = evaluateConnectionPopUp(
                hasConnectedDevice = true,
                hasPodDevice = true,
                hasAlreadyShown = connectionCoolDowns.containsKey(currentConnected.address),
                broadcastAge = broadcastAge,
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

    /**
     * Backstop for case-open popups that never receive a CLOSED frame because the device left BLE
     * range while the lid was open — otherwise the overlay lingers until manually dismissed (one of
     * the symptoms in #598). A ticker re-checks the primary device's freshness; once a previously
     * fresh OPEN broadcast goes stale past [CASE_OPEN_STALE_TIMEOUT] (no newer advertisement, or the
     * device dropped to cache-only), a single Hide is emitted. The lid-driven [monitorCase] still
     * handles the normal close; a redundant Hide here is harmless ([PopUpWindow.close] is idempotent).
     */
    private fun monitorCaseStaleClose(): Flow<Event> = combine(
        deviceMonitor.primaryDevice(),
        staleCheckTicker(),
    ) { device, _ -> isCaseOpenBroadcastFresh(device) }
        .distinctUntilChanged()
        .withPrevious()
        .mapNotNull { (wasFresh, isFresh) ->
            if (wasFresh == true && !isFresh) {
                log(TAG) { "Case-open broadcast went stale, emitting Hide" }
                Event.PopupHide(timeSource.now())
            } else {
                null
            }
        }
        .setupCommonEventHandlers(TAG) { "popUpCaseStale" }

    /** True while the primary device is eligible and currently advertising a fresh OPEN lid. */
    internal fun isCaseOpenBroadcastFresh(device: PodDevice?): Boolean {
        if (device?.reactions?.showPopUpOnCaseOpen != true) return false
        if (device.caseLidState != DualApplePods.LidState.OPEN) return false
        // Track BLE freshness specifically, not PodDevice.seenLastAt (which also counts AAP/cache):
        // the lid is a BLE-only signal, so a live AAP socket must not keep a stale OPEN on screen.
        val bleSeenLastAt = device.ble?.seenLastAt ?: return false
        return Duration.between(bleSeenLastAt, timeSource.now()) <= CASE_OPEN_STALE_TIMEOUT
    }

    private fun staleCheckTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(STALE_CHECK_INTERVAL.toMillis())
        }
    }

    fun monitor(): Flow<Event> = merge(monitorCase(), monitorConnection(), monitorCaseStaleClose())

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
                shouldResetCooldown = false,
                reason = "Lid CLOSED, refreshing cooldown",
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
        broadcastAge: Duration,
        connectionAge: Duration,
        maxConnectionAge: Duration = Duration.ofSeconds(30),
        maxAgeDiff: Duration = Duration.ofSeconds(30),
    ): ConnectionPopUpDecision {
        if (!hasConnectedDevice) {
            return ConnectionPopUpDecision(false, "No connected device")
        }
        if (!hasPodDevice) {
            return ConnectionPopUpDecision(false, "No pod device found")
        }
        if (connectionAge.abs() > maxConnectionAge) {
            return ConnectionPopUpDecision(false, "Connection is no longer new")
        }
        if (broadcastAge.abs() > (connectionAge.abs() + maxAgeDiff)) {
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

        /** A case-open popup is force-dismissed once its OPEN broadcast hasn't refreshed for this long. */
        private val CASE_OPEN_STALE_TIMEOUT: Duration = Duration.ofSeconds(4)

        /** How often the stale-close backstop re-evaluates freshness. */
        private val STALE_CHECK_INTERVAL: Duration = Duration.ofSeconds(2)
    }
}
