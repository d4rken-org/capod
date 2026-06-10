package eu.darken.capod.reaction.core.charged

import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.devicesWithProfiles
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Input
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Output
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Slot
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.SlotData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargedReaction @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val profilesRepo: DeviceProfilesRepo,
) {

    sealed class Event {
        data class ShowNotification(
            val profileId: String,
            val deviceLabel: String,
            val thresholdPercent: Int,
        ) : Event()

        data class CancelNotification(val profileId: String) : Event()
    }

    private data class ChargedConfig(val scope: ChargedSlotScope, val thresholdPercent: Int)

    fun monitor(): Flow<Event> = flow {
        // Machines live in the collection scope: a monitor (service) restart starts fresh sessions.
        val machines = mutableMapOf<String, ChargingSessionStateMachine>()
        val configs = mutableMapOf<String, ChargedConfig>()
        combine(
            profilesRepo.profiles,
            deviceMonitor.devicesWithProfiles(),
        ) { profiles, devices -> profiles.filterIsInstance<AppleDeviceProfile>() to devices }
            .collect { (profiles, devices) ->
                val enabled = profiles.filter { it.notifyWhenCharged }.associateBy { it.id }

                // Profile deleted or toggle disabled → reset; a missing device snapshot is
                // handled below as staleness, never as a reset.
                machines.keys.filter { it !in enabled }.forEach { profileId ->
                    val output = machines.remove(profileId)!!.process(Input.Reset)
                    configs.remove(profileId)
                    if (output == Output.CANCEL) emit(Event.CancelNotification(profileId))
                }

                for (profile in enabled.values) {
                    val machine = machines.getOrPut(profile.id) { ChargingSessionStateMachine() }
                    val thresholdPercent = profile.chargedThreshold.coerceIn(
                        ReactionConfig.MIN_CHARGED_THRESHOLD,
                        ReactionConfig.MAX_CHARGED_THRESHOLD,
                    )
                    // A persisted CASE scope on a caseless model (restored backup, model change)
                    // would otherwise produce an eternally-empty slot set.
                    val scope = if (profile.model.features.hasCase) profile.chargedSlotScope else ChargedSlotScope.PODS
                    val config = ChargedConfig(scope, thresholdPercent)
                    val previousConfig = configs.put(profile.id, config)
                    if (previousConfig != null && previousConfig != config) {
                        // Scope/threshold changed: stale highwater slots and an already-shown
                        // notification don't fit the new settings — start evaluation over.
                        log(TAG, INFO) { "Settings changed for ${profile.id} ($previousConfig -> $config), resetting" }
                        if (machine.process(Input.Reset) == Output.CANCEL) {
                            emit(Event.CancelNotification(profile.id))
                        }
                    }
                    val device = devices.firstOrNull { it.profileId == profile.id }
                    val slots = device?.liveChargingSlots(scope).orEmpty()
                    // No live slot data (device gone, lid closed, or AAP hasn't pushed battery
                    // yet) is treated as staleness — the session suspends instead of resetting.
                    val input = if (device == null || slots.isEmpty()) {
                        Input.StaleUpdate
                    } else {
                        Input.LiveUpdate(
                            slots = slots,
                            threshold = thresholdPercent / 100f,
                            activity = device.chargingActivity(),
                        )
                    }
                    val phaseBefore = machine.phase
                    val output = machine.process(input)
                    if (machine.phase != phaseBefore) {
                        log(TAG, VERBOSE) {
                            "${profile.id}: $phaseBefore -> ${machine.phase} (output=$output, input=$input)"
                        }
                    }
                    when (output) {
                        Output.SHOW -> {
                            val label = device?.label ?: profile.label
                            log(TAG, INFO) { "Charge complete for ${profile.id} ($label) at $thresholdPercent%" }
                            emit(Event.ShowNotification(profile.id, label, thresholdPercent))
                        }

                        Output.CANCEL -> {
                            log(TAG, INFO) { "Charging session over for ${profile.id}, cancelling notification" }
                            emit(Event.CancelNotification(profile.id))
                        }

                        Output.NONE -> Unit
                    }
                }
            }
    }
        .setupCommonEventHandlers(TAG) { "chargedReaction" }

    companion object {
        private val TAG = logTag("Reaction", "Charged")
    }
}

/**
 * Per-slot live readings only — AAP first, then live BLE. The [PodDevice] facade getters are
 * deliberately bypassed because they silently fall back to the disk cache; stale cached values
 * must never start, complete, or end a charging session.
 */
/**
 * Signals that the user is handling the device, used to dismiss an already-shown notification.
 * All sources are live (ear detection: AAP/BLE; lid: BLE; DISCONNECTED slots: AAP) — the cache
 * never feeds in here.
 */
internal fun PodDevice.chargingActivity(): ChargingSessionStateMachine.Activity =
    ChargingSessionStateMachine.Activity(
        // Only trust AAP ear detection — BLE ear bits are phantom for in-case pods (see
        // PodDevice.hasAapEarDetection). Unknown when there's no AAP session, which is the
        // normal state while charging in the case, so phantom "worn" never dismisses.
        wornSlots = when {
            !hasAapEarDetection -> null
            hasDualPods -> buildSet {
                if (isLeftInEar == true) add(Slot.LEFT)
                if (isRightInEar == true) add(Slot.RIGHT)
            }
            else -> if (isBeingWorn == true) setOf(Slot.HEADSET) else emptySet()
        },
        lid = when (caseLidState) {
            DualApplePods.LidState.OPEN -> ChargingSessionStateMachine.Lid.OPEN
            DualApplePods.LidState.CLOSED -> ChargingSessionStateMachine.Lid.CLOSED
            DualApplePods.LidState.NOT_IN_CASE -> ChargingSessionStateMachine.Lid.NOT_IN_CASE
            else -> null
        },
        disconnectedSlots = buildSet {
            if (leftPodChargingState == AapPodState.ChargingState.DISCONNECTED) add(Slot.LEFT)
            if (rightPodChargingState == AapPodState.ChargingState.DISCONNECTED) add(Slot.RIGHT)
            if (caseChargingState == AapPodState.ChargingState.DISCONNECTED) add(Slot.CASE)
            if (headsetChargingState == AapPodState.ChargingState.DISCONNECTED) add(Slot.HEADSET)
        },
    )

internal fun PodDevice.liveChargingSlots(scope: ChargedSlotScope): Map<Slot, SlotData> {
    val slots = mutableMapOf<Slot, SlotData>()
    fun add(slot: Slot, battery: Float?, charging: Boolean?) {
        if (battery == null || battery < 0f || charging == null) return
        slots[slot] = SlotData(battery = battery, isCharging = charging)
    }
    if (scope != ChargedSlotScope.CASE) {
        add(
            Slot.LEFT,
            aap?.batteryLeft ?: (ble as? DualBlePodSnapshot)?.batteryLeftPodPercent,
            aap?.isLeftCharging ?: (ble as? HasChargeDetectionDual)?.isLeftPodCharging,
        )
        add(
            Slot.RIGHT,
            aap?.batteryRight ?: (ble as? DualBlePodSnapshot)?.batteryRightPodPercent,
            aap?.isRightCharging ?: (ble as? HasChargeDetectionDual)?.isRightPodCharging,
        )
        add(
            Slot.HEADSET,
            aap?.batteryHeadset ?: (ble as? SingleBlePodSnapshot)?.batteryHeadsetPercent,
            aap?.isHeadsetCharging ?: (ble as? HasChargeDetection)?.isHeadsetBeingCharged,
        )
    }
    if (scope != ChargedSlotScope.PODS) {
        add(
            Slot.CASE,
            aap?.batteryCase ?: (ble as? HasCase)?.batteryCasePercent,
            aap?.isCaseCharging ?: (ble as? HasCase)?.isCaseCharging,
        )
    }
    return slots
}
