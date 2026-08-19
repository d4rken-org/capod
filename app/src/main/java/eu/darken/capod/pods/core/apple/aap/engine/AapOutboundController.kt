package eu.darken.capod.pods.core.apple.aap.engine

import eu.darken.capod.common.TimeSource
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

internal data class VerificationState(
    val command: AapCommand,
    val attempt: Int = 0,
    /**
     * The listening mode the device was in when the write went out. Used to tell a refusal (the
     * device echoes the mode it is staying in) apart from an unusable report (a third mode). Null
     * for non-ANC commands.
     */
    val previousAncMode: AapSetting.AncMode.Value? = null,
    /** Monotonic-ish timestamp of the write, used to measure how long the device took to answer. */
    val sentAtMs: Long = 0L,
    /** The first listening mode report seen since the write, with how long it took to arrive. */
    val observedAncEcho: AncEcho? = null,
    /** True once another listening mode write was issued while this one was still outstanding. */
    val superseded: Boolean = false,
)

/** A listening mode report attributed to an outstanding write. */
internal data class AncEcho(
    val mode: AapSetting.AncMode.Value,
    val latencyMs: Long,
)

internal data class OutboundRuntimeState(
    val pendingCommands: List<AapCommand> = emptyList(),
    val verification: VerificationState? = null,
)

internal data class OutboundDecision(
    val podState: AapPodState,
    val runtimeState: OutboundRuntimeState,
    val commandsToSend: List<AapCommand> = emptyList(),
    val timerActions: List<EngineTimerAction> = emptyList(),
    val logs: List<String> = emptyList(),
    val rejectedCommand: AapCommand? = null,
)

internal class AapOutboundController(
    private val timeSource: TimeSource,
) {

    private val coordinator = AapSettingsCoordinator(timeSource)

    companion object {
        /**
         * How long to wait for the device to confirm a setting write before treating it as diverged.
         *
         * AirPods Pro 3 answer a listening mode write in roughly 0.8-1.1s (measured: 833ms, 887ms,
         * 956ms, 1008ms). A 1000ms deadline sits inside that spread, so a perfectly healthy reply
         * could land just after the timer and trigger a bogus "Divergence detected" plus a
         * redundant re-send. The deadline is only a backstop now: [onStateObserved] resolves the
         * verification as soon as a matching report arrives, so raising this does not slow the
         * success path, only how long a genuinely unanswered write waits.
         *
         * Kept at roughly 2x the worst measured reply rather than higher, because a real rejection
         * still costs two full deadlines before the user is told about it.
         */
        const val VERIFICATION_TIMEOUT_MS = 2000L

        /**
         * Below this, a listening mode report is a refusal rather than the result of a change.
         *
         * Captured refusals answer in 25-267ms; a real mode change answers in 815-1010ms. Only an
         * answer slow enough to be a change is eligible to be read as an unusable report.
         */
        const val ANC_CHANGE_LATENCY_MIN_MS = 500L
    }

    fun onCommandRequested(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
        command: AapCommand,
    ): OutboundDecision {
        // SetDeviceName and SetDynamicEndOfCharge bypass ear-gating:
        //  - Rename is a user-initiated metadata change, independent of wear state.
        //  - Charge cap (setting 0x3B) is toggled while pods sit in the closed case; queueing
        //    it until worn would make the toggle look broken for its main use case.
        if (command !is AapCommand.SetDeviceName && command !is AapCommand.SetDynamicEndOfCharge) {
            val earDetection = podState.setting<AapSetting.EarDetection>()
            if (earDetection != null && !earDetection.isEitherPodInEar) {
                val result = coordinator.enqueue(runtimeState.pendingCommands, command, podState)
                return OutboundDecision(
                    podState = applyPendingSnapshot(
                        result.optimisticState ?: podState,
                        result.snapshot,
                        if (command is AapCommand.SetAncMode) command.mode else result.snapshot.pendingAncMode,
                    ),
                    runtimeState = runtimeState.copy(pendingCommands = result.pendingCommands),
                    logs = listOf("No pod in ear, queuing: ${command::class.simpleName}"),
                )
            }
        }

        var updatedPodState = podState
        var updatedRuntimeState = runtimeState
        if (command is AapCommand.SetAncMode) {
            val result = coordinator.removeFromQueue(runtimeState.pendingCommands, AapCommand.SetAncMode::class)
            updatedPodState = applyPendingSnapshot(updatedPodState, result.snapshot, command.mode)
            updatedRuntimeState = updatedRuntimeState.copy(pendingCommands = result.pendingCommands)
        }

        coordinator.optimisticUpdate(updatedPodState, command)?.let { updatedPodState = it }

        val verificationCheck = coordinator.verificationFor(command)
        return OutboundDecision(
            podState = updatedPodState,
            runtimeState = updatedRuntimeState.copy(
                verification = verificationCheck?.let {
                    VerificationState(
                        command = command,
                        attempt = 0,
                        previousAncMode = podState.setting<AapSetting.AncMode>()?.current,
                        sentAtMs = timeSource.elapsedRealtime(),
                        // A second listening mode write while one is outstanding makes the echoes
                        // ambiguous: we can no longer say which write any given report answers.
                        superseded = command is AapCommand.SetAncMode &&
                                updatedRuntimeState.verification?.command is AapCommand.SetAncMode,
                    )
                }
                    ?: updatedRuntimeState.verification,
            ),
            commandsToSend = listOf(command),
            timerActions = if (verificationCheck != null) {
                listOf(EngineTimerAction.Start(EngineTimerKey.Verification, VERIFICATION_TIMEOUT_MS))
            } else {
                emptyList()
            },
        )
    }

    fun onEarDetectionInEar(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
    ): OutboundDecision {
        val result = coordinator.flush(runtimeState.pendingCommands)
        if (result.commands.isEmpty()) return OutboundDecision(podState, runtimeState.copy(pendingCommands = result.pendingCommands))

        val ancCommand = result.commands.firstOrNull { it is AapCommand.SetAncMode } as? AapCommand.SetAncMode
        val updatedPodState = applyPendingSnapshot(
            podState,
            result.snapshot,
            ancCommand?.mode ?: podState.pendingAncMode,
        )
        val toVerify = result.commands.firstOrNull { it is AapCommand.SetAncMode } ?: result.commands.lastOrNull()
        val verificationCheck = toVerify?.let { coordinator.verificationFor(it) }
        return OutboundDecision(
            podState = updatedPodState,
            runtimeState = runtimeState.copy(
                pendingCommands = result.pendingCommands,
                verification = if (verificationCheck != null) {
                    VerificationState(
                        command = checkNotNull(toVerify),
                        attempt = 0,
                        previousAncMode = podState.setting<AapSetting.AncMode>()?.current,
                        sentAtMs = timeSource.elapsedRealtime(),
                        superseded = runtimeState.verification?.command is AapCommand.SetAncMode,
                    )
                } else {
                    runtimeState.verification
                },
            ),
            commandsToSend = result.commands,
            timerActions = if (verificationCheck != null) {
                listOf(EngineTimerAction.Start(EngineTimerKey.Verification, VERIFICATION_TIMEOUT_MS))
            } else {
                emptyList()
            },
            logs = listOf("Pod in ear, flushing ${result.commands.size} queued commands"),
        )
    }

    /**
     * Attribute a listening mode report to the outstanding write and remember how long it took.
     *
     * Only the first report after the write is kept: that is the one the device sent in answer.
     * Classification later uses this recorded frame rather than whatever happens to be current at
     * the deadline, so an unrelated concurrent report cannot be mistaken for our echo.
     */
    fun onAncReportObserved(
        runtimeState: OutboundRuntimeState,
        mode: AapSetting.AncMode.Value,
        nowMs: Long,
    ): OutboundRuntimeState {
        val verification = runtimeState.verification ?: return runtimeState
        if (verification.command !is AapCommand.SetAncMode) return runtimeState
        if (verification.observedAncEcho != null) return runtimeState
        return runtimeState.copy(
            verification = verification.copy(
                observedAncEcho = AncEcho(mode = mode, latencyMs = nowMs - verification.sentAtMs),
            ),
        )
    }

    /**
     * Mark an outstanding listening mode write ambiguous because the user changed the mode on the
     * device itself. Any report arriving now could answer either, and the two cannot be told apart.
     *
     * This only covers changes CAPod can see. A switch made from iOS or another paired phone is
     * invisible here, which is why a misattributed echo is only ever allowed to affect the current
     * reading and is never learned from.
     */
    fun onExternalAncChange(runtimeState: OutboundRuntimeState): OutboundRuntimeState {
        val verification = runtimeState.verification ?: return runtimeState
        if (verification.command !is AapCommand.SetAncMode) return runtimeState
        if (verification.superseded) return runtimeState
        return runtimeState.copy(verification = verification.copy(superseded = true))
    }

    /**
     * Re-check the outstanding verification against freshly applied device state, so a confirmation
     * is honoured the moment it arrives instead of waiting out [VERIFICATION_TIMEOUT_MS] and racing
     * it.
     *
     * Deliberately limited to [AapCommand.SetAncMode]. Every other verified command gets an
     * optimistic write into state when it is queued (see AapSettingsCoordinator.optimisticUpdate),
     * which satisfies its own verification predicate straight away - only the device's contradicting
     * echo later makes it fail. Reconciling those on arbitrary inbound frames would cancel the
     * verification before that echo lands and silently swallow the rejection. SetAncMode is exempt
     * from the optimistic write, so its predicate only becomes true once the device really confirms.
     */
    fun onStateObserved(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
    ): OutboundDecision {
        val verification = runtimeState.verification ?: return OutboundDecision(podState, runtimeState)
        if (verification.command !is AapCommand.SetAncMode) return OutboundDecision(podState, runtimeState)
        val check = coordinator.verificationFor(verification.command)
            ?: return OutboundDecision(podState, runtimeState)
        if (!check(podState)) return OutboundDecision(podState, runtimeState)
        return OutboundDecision(
            podState = clearPendingForCommand(podState, verification.command),
            runtimeState = runtimeState.copy(verification = null),
            timerActions = listOf(EngineTimerAction.Cancel(EngineTimerKey.Verification)),
        )
    }

    fun onVerificationTimerFired(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
    ): OutboundDecision {
        val verification = runtimeState.verification ?: return OutboundDecision(podState, runtimeState)
        val check = coordinator.verificationFor(verification.command)
            ?: return OutboundDecision(
                podState = podState,
                runtimeState = runtimeState.copy(verification = null),
            )

        if (check(podState)) {
            return OutboundDecision(
                podState = clearPendingForCommand(podState, verification.command),
                runtimeState = runtimeState.copy(verification = null),
            )
        }

        unusableAncReport(podState, runtimeState, verification)?.let { return it }

        val ear = podState.setting<AapSetting.EarDetection>()
        if (ear != null && !ear.isEitherPodInEar) {
            // Drop the pending mode too: nothing is going to confirm it now, and leaving it set
            // would keep the UI showing a mode the device never reached.
            return OutboundDecision(
                podState = clearPendingForCommand(podState, verification.command),
                runtimeState = runtimeState.copy(verification = null),
                logs = listOf("Verification aborted for ${verification.command::class.simpleName}: no pod in ear"),
            )
        }

        if (verification.attempt == 0) {
            return OutboundDecision(
                podState = podState,
                runtimeState = runtimeState.copy(
                    // A re-send is a fresh question: the previous attempt's echo and send time
                    // must not be carried over, or the retry gets judged on stale evidence.
                    verification = verification.copy(
                        attempt = 1,
                        sentAtMs = timeSource.elapsedRealtime(),
                        observedAncEcho = null,
                    ),
                ),
                commandsToSend = listOf(verification.command),
                timerActions = listOf(EngineTimerAction.Start(EngineTimerKey.Verification, VERIFICATION_TIMEOUT_MS)),
                logs = listOf("Divergence detected for ${verification.command::class.simpleName}, re-sending"),
            )
        }

        return OutboundDecision(
            podState = clearPendingForCommand(podState, verification.command),
            runtimeState = runtimeState.copy(verification = null),
            logs = listOf("Rejected after retry: ${verification.command::class.simpleName}"),
            rejectedCommand = verification.command,
        )
    }

    /**
     * Distinguish a refusal from a report we cannot act on.
     *
     * A device that refuses a listening mode write echoes the mode it is staying in, and answers
     * quickly (25-267ms in captures). AirPods Pro 3 have instead been seen answering an ADAPTIVE
     * write with OFF at normal change latency (815-1010ms) while audibly switching to Adaptive:
     * a third mode, neither the one requested nor the one it was in.
     *
     * Retrying that write is pointless (it already took effect) and reporting it as rejected is
     * wrong. Treat the echo as noise, record the mode we asked for as current, and stop verifying.
     * The raw frame is still logged upstream; nothing is suppressed at the protocol layer.
     *
     * Every condition below exists to keep a wrong conclusion out of the session:
     * - the recorded echo is used, never whatever is current at the deadline, so an unrelated
     *   concurrent report cannot be mistaken for the answer to our write
     * - a write that was superseded by another listening mode write is never classified, because
     *   its echoes can no longer be attributed
     * - an answer fast enough to be a refusal is never read as a change
     *
     * Deliberately engine-local: it uses only the requested mode, the previous mode, and the echo
     * we recorded. Which modes a device permits is app-level knowledge and stays out of the engine.
     */
    private fun unusableAncReport(
        podState: AapPodState,
        runtimeState: OutboundRuntimeState,
        verification: VerificationState,
    ): OutboundDecision? {
        val command = verification.command as? AapCommand.SetAncMode ?: return null
        if (verification.superseded) return null
        val previous = verification.previousAncMode ?: return null
        val echo = verification.observedAncEcho ?: return null
        if (echo.mode == command.mode || echo.mode == previous) return null
        if (echo.latencyMs < ANC_CHANGE_LATENCY_MIN_MS) return null
        val ancMode = podState.setting<AapSetting.AncMode>() ?: return null

        return OutboundDecision(
            podState = clearPendingForCommand(
                podState.withSetting(AapSetting.AncMode::class, ancMode.copy(current = command.mode)),
                command,
            ),
            runtimeState = runtimeState.copy(verification = null),
            logs = listOf(
                "Unusable ANC echo for ${command.mode} (reported=${echo.mode}, was=$previous, " +
                        "after ${echo.latencyMs}ms), not a refusal: keeping ${command.mode}"
            ),
        )
    }

    private fun clearPendingForCommand(
        podState: AapPodState,
        command: AapCommand,
    ): AapPodState = if (command is AapCommand.SetAncMode && podState.pendingAncMode == command.mode) {
        podState.copy(pendingAncMode = null)
    } else {
        podState
    }

    private fun applyPendingSnapshot(
        podState: AapPodState,
        snapshot: AapSettingsCoordinator.PendingSnapshot,
        ancPendingMode: AapSetting.AncMode.Value?,
    ): AapPodState = podState.copy(
        pendingAncMode = ancPendingMode,
        pendingSettingsCount = snapshot.count,
    )
}
