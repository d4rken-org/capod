package eu.darken.capod.reaction.core.conversation

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.ConversationAwarenessEvent
import eu.darken.capod.profiles.core.ReactionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Reacts to Conversational Awareness speaking transitions (AAP `0x4B`) by either lowering media
 * volume or pausing, per the primary device's [ReactionConfig.conversationAction], and reverts when
 * speaking stops. On Android the pod firmware does not duck audio itself, so CAPod performs it.
 *
 * Disengage is driven by the pod's explicit end-of-speech frame ([ConversationAwarenessEvent.STOP]).
 * The pod does NOT reliably stream keep-alive frames while you talk — on some firmware it sends an
 * onset ([ConversationAwarenessEvent.START]) then nothing for many seconds while speech continues
 * (observed: CA held engaged 21s with zero `0x4B` frames). So frame-silence must NOT be read as
 * "speaking ended". [STALE_TIMEOUT] is only a long backstop for the rare case where every terminal
 * frame is lost while the link stays up; a link drop is handled by the owner-disconnect revert.
 * [ConversationAwarenessEvent.HOLD] frames (transitional/unknown statuses) keep the reaction engaged
 * and refresh the backstop.
 *
 * State is a single global slot (media volume / playback is system-wide, not per-device) guarded by
 * a [Mutex] — events, AAP-state-removal, the stale timer, and monitor completion all mutate it.
 * Volume ducks are additionally reverted on owner-disconnect and monitor completion so a dropped
 * session never strands the user at low volume.
 */
@Singleton
class ConversationReaction @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val deviceMonitor: DeviceMonitor,
    private val mediaControl: MediaControl,
    @AppScope private val appScope: CoroutineScope,
    private val timeSource: TimeSource,
) {

    private sealed interface Kind {
        data object Paused : Kind
        data class Ducked(val priorVolume: Int, val appliedVolume: Int) : Kind
    }

    private data class Active(
        val id: Long,
        val owner: BluetoothAddress,
        val kind: Kind,
        val at: Long,
    )

    private val mutex = Mutex()
    private var active: Active? = null
    private var staleJob: Job? = null
    private var idCounter = 0L

    fun monitor(): Flow<Unit> = merge(
        aapManager.conversationalAwarenessEvents.onEach { (address, event) -> onEvent(address, event) },
        // Reverts a stranded duck when the owning device leaves the AAP state map for any reason
        // (intentional or not) — disconnectEvents only fires for unintentional drops.
        aapManager.allStates.onEach { states -> onActiveDevicesChanged(states.keys) },
    )
        .map { }
        // Service stop / scope cancellation: undo any active duck so we don't leave volume lowered.
        .onCompletion { withContext(NonCancellable) { onMonitorCompleted() } }
        .setupCommonEventHandlers(TAG) { "conversationReaction" }

    private suspend fun onEvent(address: BluetoothAddress, event: ConversationAwarenessEvent) = when (event) {
        ConversationAwarenessEvent.START -> onSpeakingStart(address)
        ConversationAwarenessEvent.HOLD -> onSpeakingHold(address)
        ConversationAwarenessEvent.STOP -> onSpeakingStop(address)
    }

    private suspend fun onSpeakingStart(address: BluetoothAddress) {
        val primary = deviceMonitor.primaryDevice().first()
        if (primary?.address != address) {
            log(TAG) { "START from $address ignored — not primary device (primary=${primary?.address})" }
            return
        }
        val action = primary.reactions.conversationAction
        if (action == ConversationAction.NOTHING) return

        mutex.withLock {
            val current = active
            if (current != null && current.owner == address) {
                // Duplicate START for the same speaker — don't re-act, just keep the session alive.
                restartStaleTimer(current)
                log(TAG) { "START from $address — already active ($action), keep-alive" }
                return
            }
            // A different device started speaking while we were active — undo the old one first.
            if (current != null) revert(current, "superseded by $address")

            when (action) {
                ConversationAction.PAUSE -> {
                    val paused = mediaControl.sendPause(rememberForResume = false)
                    if (paused) {
                        val record = Active(nextId(), address, Kind.Paused, timeSource.elapsedRealtime())
                        active = record
                        restartStaleTimer(record)
                        log(TAG, INFO) { "START on $address → paused media" }
                    } else {
                        active = null
                        log(TAG) { "START on $address → nothing playing, no pause" }
                    }
                }

                ConversationAction.LOWER_VOLUME -> {
                    val reduction = primary.reactions.conversationVolumeReduction
                        .coerceIn(
                            ReactionConfig.MIN_CONVERSATION_VOLUME_REDUCTION,
                            ReactionConfig.MAX_CONVERSATION_VOLUME_REDUCTION,
                        )
                    val duck = mediaControl.duckMusicVolume(reduction)
                    if (duck != null) {
                        val record = Active(
                            nextId(),
                            address,
                            Kind.Ducked(duck.priorVolume, duck.appliedVolume),
                            timeSource.elapsedRealtime(),
                        )
                        active = record
                        restartStaleTimer(record)
                        log(TAG, INFO) { "START on $address → ducked volume ${duck.priorVolume}→${duck.appliedVolume}" }
                    } else {
                        active = null
                        log(TAG) { "START on $address → duck no-op" }
                    }
                }

                ConversationAction.NOTHING -> Unit
            }
        }
    }

    /** Intermediate keep-alive frame: the wearer is still speaking — refresh the stale timer. */
    private suspend fun onSpeakingHold(address: BluetoothAddress) = mutex.withLock {
        val current = active ?: return
        if (current.owner != address) return
        restartStaleTimer(current)
    }

    private suspend fun onSpeakingStop(address: BluetoothAddress) {
        val primary = deviceMonitor.primaryDevice().first()
        mutex.withLock {
            val current = active ?: return
            if (current.owner != address) {
                log(TAG) { "STOP from $address ignored — owner is ${current.owner}" }
                return
            }
            clearActive()
            disengage(current, primary, "STOP on $address")
        }
    }

    /** Graceful disengage (STOP event or stale timeout). Must be called under [mutex]. */
    private suspend fun disengage(record: Active, primary: PodDevice?, reason: String) {
        when (val kind = record.kind) {
            is Kind.Paused -> {
                // Resume the pause WE caused, regardless of the current action setting. Gating on
                // "action still == PAUSE" would strand media paused if the user switched the action
                // (or set NOTHING) mid-conversation — undoing our own side effect is the least
                // surprising behaviour. The remaining guards are about real device/playback state.
                val age = timeSource.elapsedRealtime() - record.at
                when {
                    age.milliseconds > PAUSE_RESUME_WINDOW ->
                        log(TAG) { "$reason — resume skipped (stale, ${age}ms)" }
                    primary?.address != record.owner ->
                        log(TAG) { "$reason — resume skipped (primary switched)" }
                    primary.isBeingWorn == false ->
                        log(TAG) { "$reason — resume skipped (not worn)" }
                    mediaControl.isPlaying ->
                        log(TAG) { "$reason — resume skipped (already playing)" }
                    else -> {
                        mediaControl.sendPlay()
                        log(TAG, INFO) { "$reason → resumed media" }
                    }
                }
            }

            is Kind.Ducked -> revertDuck(kind, reason)
        }
    }

    private suspend fun onActiveDevicesChanged(addresses: Set<BluetoothAddress>) = mutex.withLock {
        val current = active ?: return
        if (current.owner !in addresses) {
            clearActive()
            revert(current, "owner ${current.owner} gone")
        }
    }

    private suspend fun onMonitorCompleted() = mutex.withLock {
        val current = active ?: return
        clearActive()
        revert(current, "monitor completed")
    }

    /** Forced revert (disconnect / supersede / shutdown): undo volume ducks; leave pauses as-is. */
    private fun revert(record: Active, reason: String) {
        when (val kind = record.kind) {
            is Kind.Ducked -> revertDuck(kind, reason)
            is Kind.Paused -> log(TAG) { "Clearing pause ($reason) — leaving playback as-is" }
        }
    }

    private fun revertDuck(kind: Kind.Ducked, reason: String) {
        // Restore to the pre-duck volume unconditionally — we do NOT gate on
        // currentMusicVolume() == appliedVolume. Such a guard skips the restore whenever anything
        // else moved the volume meanwhile (e.g. a concurrent volume-manager app), which lets the
        // baseline ratchet down across conversations. Restoring deterministically to the level we
        // saved at engage avoids that; the trade-off is overriding a deliberate mid-conversation
        // volume change (rare). Matches librepods' unconditional restore.
        log(TAG, INFO) { "Restoring volume to ${kind.priorVolume} (ducked to ${kind.appliedVolume}, now ${mediaControl.currentMusicVolume()}, $reason)" }
        mediaControl.restoreMusicVolume(kind.priorVolume)
    }

    /** Must be called under [mutex]. Clears the active slot and cancels its stale timer. */
    private fun clearActive() {
        staleJob?.cancel()
        staleJob = null
        active = null
    }

    /**
     * Must be called under [mutex]. (Re)starts the backstop timer for [record], reset on every frame
     * (START/HOLD). If no frame arrives for [STALE_TIMEOUT] the session is force-ended — a last
     * resort for a fully-dropped terminal frame, not the normal disengage. Identity-checked so a
     * late timer can't disengage a newer session.
     */
    private fun restartStaleTimer(record: Active) {
        staleJob?.cancel()
        staleJob = appScope.launch {
            delay(STALE_TIMEOUT)
            val primary = deviceMonitor.primaryDevice().first()
            mutex.withLock {
                if (active?.id == record.id) {
                    val current = active!!
                    clearActive()
                    disengage(current, primary, "stale timeout (frames ceased)")
                }
            }
        }
    }

    private fun nextId(): Long = ++idCounter

    companion object {
        private val TAG = logTag("Reaction", "Conversation")

        /**
         * Pure backstop: disengage if no `0x4B` frame arrives for this long while engaged. This is
         * NOT the normal disengage path — the pod does not stream keep-alives during speech, so a
         * short timeout would fire mid-conversation (the original 12s value did exactly that).
         * Normal disengage is the explicit terminal [ConversationAwarenessEvent.STOP] frame; this
         * only recovers a fully-dropped terminal frame while the link stays up. Kept longer than
         * [PAUSE_RESUME_WINDOW] so a back-stopped pause is never auto-resumed (only a duck is
         * restored on stale — a stranded low volume is the worse failure).
         */
        private val STALE_TIMEOUT = 5.minutes

        /** A pause older than this no longer auto-resumes — unexpected late playback is worse. */
        private val PAUSE_RESUME_WINDOW = 2.minutes
    }
}
