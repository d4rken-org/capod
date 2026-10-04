package eu.darken.capod.reaction.core.caselow

import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.devicesWithProfiles
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.caselow.CaseLowReminderStateMachine.Input
import eu.darken.capod.reaction.core.caselow.CaseLowReminderStateMachine.Output
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseLowReminderReaction @Inject constructor(
    private val deviceMonitor: DeviceMonitor,
    private val profilesRepo: DeviceProfilesRepo,
) {

    sealed class Event {
        data class ShowNotification(
            val profileId: String,
            val deviceLabel: String,
            val casePercent: Int,
        ) : Event()

        data class CancelNotification(val profileId: String) : Event()

        /** Reminders for profiles outside [enabledProfileIds] are stale and should be removed. */
        data class Reconcile(val enabledProfileIds: Set<String>) : Event()
    }

    fun monitor(): Flow<Event> = flow {
        // Trackers live in the collection scope: a monitor (service) restart starts fresh.
        val trackers = mutableMapOf<String, CaseLowReminderStateMachine>()
        val thresholds = mutableMapOf<String, Int>()
        var reconciledIds: Set<String>? = null
        combine(
            profilesRepo.profiles,
            deviceMonitor.devicesWithProfiles(),
        ) { profiles, devices -> profiles.filterIsInstance<AppleDeviceProfile>() to devices }
            .collect { (profiles, devices) ->
                val enabled = profiles
                    .filter { it.notifyWhenCaseLow && it.model.features.hasCase }
                    .associateBy { it.id }

                if (reconciledIds != enabled.keys) {
                    reconciledIds = enabled.keys
                    emit(Event.Reconcile(enabled.keys))
                }

                // Cancel unconditionally: the reminder may have been posted by an earlier monitor run.
                trackers.keys.filter { it !in enabled }.forEach { profileId ->
                    trackers.remove(profileId)
                    thresholds.remove(profileId)
                    log(TAG, INFO) { "Reminder disabled for $profileId, cancelling" }
                    emit(Event.CancelNotification(profileId))
                }

                for (profile in enabled.values) {
                    val tracker = trackers.getOrPut(profile.id) { CaseLowReminderStateMachine() }
                    val threshold = profile.caseLowThreshold.coerceIn(
                        ReactionConfig.MIN_CASE_LOW_THRESHOLD,
                        ReactionConfig.MAX_CASE_LOW_THRESHOLD,
                    )
                    val previousThreshold = thresholds.put(profile.id, threshold)
                    if (previousThreshold != null && previousThreshold != threshold) {
                        log(TAG, INFO) { "Threshold changed for ${profile.id} ($previousThreshold -> $threshold)" }
                        tracker.process(Input.Reset)
                        emit(Event.CancelNotification(profile.id))
                    }

                    // Several live devices for one profile are ambiguous; an out-to-in transition
                    // must not be stitched together from two physical devices.
                    val device = devices.filter { it.profileId == profile.id }.singleOrNull()
                    val observation = device?.caseLowObservation()
                    val input = if (observation == null) {
                        Input.NoObservation
                    } else {
                        Input.Update(observation, threshold)
                    }

                    val armedBefore = tracker.isArmed
                    val output = tracker.process(input)
                    if (tracker.isArmed != armedBefore) {
                        log(TAG, VERBOSE) { "${profile.id}: armed=${tracker.isArmed} (output=$output, input=$input)" }
                    }
                    when (output) {
                        is Output.Show -> {
                            val label = device?.label ?: profile.label
                            log(TAG, INFO) { "Case low for ${profile.id} ($label) at ${output.casePercent}%" }
                            emit(Event.ShowNotification(profile.id, label, output.casePercent))
                        }

                        Output.Cancel -> {
                            log(TAG, INFO) { "Case charging or above threshold for ${profile.id}, cancelling" }
                            emit(Event.CancelNotification(profile.id))
                        }

                        Output.None -> Unit
                    }
                }
            }
    }
        .setupCommonEventHandlers(TAG) { "caseLowReminder" }

    companion object {
        private val TAG = logTag("Reaction", "CaseLow")
    }
}
