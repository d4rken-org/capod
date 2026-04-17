package eu.darken.capod.main.ui.presscontrols

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class PressControlsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceMonitor: DeviceMonitor,
    private val aapManager: AapConnectionManager,
    private val upgradeRepo: UpgradeRepo,
    private val profilesRepo: DeviceProfilesRepo,
) : ViewModel4(dispatcherProvider) {

    private val targetProfileId = MutableStateFlow<ProfileId?>(null)
    private var initialized = false

    fun initialize(profileId: ProfileId) {
        if (initialized && targetProfileId.value == profileId) return
        initialized = true
        targetProfileId.value = profileId
    }

    sealed interface Event {
        data class SendFailed(val command: AapCommand, val message: String?) : Event
    }

    val events = SingleEventFlow<Event>()

    val state = targetProfileId.flatMapLatest { profileId ->
        if (profileId == null) return@flatMapLatest flowOf(State())
        combine(
            deviceForProfile(profileId),
            profilesRepo.profiles,
            upgradeRepo.upgradeInfo,
        ) { device, profiles, upgrade ->
            val profile = profiles.filterIsInstance<AppleDeviceProfile>().firstOrNull { it.id == profileId }
            val stemActions = profile?.stemActions ?: StemActionsConfig()
            State(
                device = device,
                profile = profile,
                stemActions = stemActions,
                isPro = upgrade.isPro,
                isAapReady = device?.isAapReady == true,
            )
        }
    }.asLiveState()

    private fun deviceForProfile(profileId: ProfileId): Flow<PodDevice?> =
        deviceMonitor.devices.flatMapLatest { devices ->
            val live = devices.firstOrNull { it.profileId == profileId }
            flow<PodDevice?> { emit(live ?: deviceMonitor.getDeviceForProfile(profileId)) }
        }

    data class State(
        val device: PodDevice? = null,
        val profile: AppleDeviceProfile? = null,
        val stemActions: StemActionsConfig = StemActionsConfig(),
        val isPro: Boolean = false,
        val isAapReady: Boolean = false,
    )

    // ── Stem-action mapping setters (profile-scoped, Pro-gated on non-NONE assignment) ───────

    private fun StemActionsConfig.getSide(bud: Side): StemAction = when (bud) {
        Side.LEFT_SINGLE -> leftSingle
        Side.LEFT_DOUBLE -> leftDouble
        Side.LEFT_TRIPLE -> leftTriple
        Side.LEFT_LONG -> leftLong
        Side.RIGHT_SINGLE -> rightSingle
        Side.RIGHT_DOUBLE -> rightDouble
        Side.RIGHT_TRIPLE -> rightTriple
        Side.RIGHT_LONG -> rightLong
    }

    private fun StemActionsConfig.withSide(bud: Side, action: StemAction): StemActionsConfig = when (bud) {
        Side.LEFT_SINGLE -> copy(leftSingle = action)
        Side.LEFT_DOUBLE -> copy(leftDouble = action)
        Side.LEFT_TRIPLE -> copy(leftTriple = action)
        Side.LEFT_LONG -> copy(leftLong = action)
        Side.RIGHT_SINGLE -> copy(rightSingle = action)
        Side.RIGHT_DOUBLE -> copy(rightDouble = action)
        Side.RIGHT_TRIPLE -> copy(rightTriple = action)
        Side.RIGHT_LONG -> copy(rightLong = action)
    }

    private enum class Side {
        LEFT_SINGLE, LEFT_DOUBLE, LEFT_TRIPLE, LEFT_LONG,
        RIGHT_SINGLE, RIGHT_DOUBLE, RIGHT_TRIPLE, RIGHT_LONG,
    }

    private fun otherSideFor(bud: Side): Side = when (bud) {
        Side.LEFT_SINGLE -> Side.RIGHT_SINGLE
        Side.LEFT_DOUBLE -> Side.RIGHT_DOUBLE
        Side.LEFT_TRIPLE -> Side.RIGHT_TRIPLE
        Side.LEFT_LONG -> Side.RIGHT_LONG
        Side.RIGHT_SINGLE -> Side.LEFT_SINGLE
        Side.RIGHT_DOUBLE -> Side.LEFT_DOUBLE
        Side.RIGHT_TRIPLE -> Side.LEFT_TRIPLE
        Side.RIGHT_LONG -> Side.LEFT_LONG
    }

    private fun setSide(bud: Side, action: StemAction) = launch {
        log(TAG, INFO) { "setSide($bud, $action)" }
        val profileId = targetProfileId.value ?: return@launch
        val currentProfile = profilesRepo.profiles.first()
            .filterIsInstance<AppleDeviceProfile>()
            .firstOrNull { it.id == profileId } ?: return@launch
        val current = currentProfile.stemActions.getSide(bud)
        // 1. No-op short-circuit.
        if (action == current) return@launch
        // 2. Free-clear allowance — always allow clearing to NONE.
        // 3. Otherwise Pro is required.
        if (action != StemAction.NONE && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        // 4. Mutate + cross-side effect.
        profilesRepo.updateAppleProfile(profileId) { profile ->
            val otherSide = otherSideFor(bud)
            val otherCurrent = profile.stemActions.getSide(otherSide)
            val newConfig = profile.stemActions
                .withSide(bud, action)
                .applyCrossSideEffect(action, otherSide, otherCurrent)
            profile.copy(stemActions = newConfig)
        }
    }

    private fun StemActionsConfig.applyCrossSideEffect(
        selected: StemAction,
        otherBud: Side,
        otherCurrent: StemAction,
    ): StemActionsConfig = when (selected) {
        StemAction.NONE -> withSide(otherBud, StemAction.NONE)
        StemAction.NO_ACTION -> this
        else -> if (otherCurrent == StemAction.NONE) withSide(otherBud, StemAction.NO_ACTION) else this
    }

    fun setLeftSingle(action: StemAction) = setSide(Side.LEFT_SINGLE, action)
    fun setLeftDouble(action: StemAction) = setSide(Side.LEFT_DOUBLE, action)
    fun setLeftTriple(action: StemAction) = setSide(Side.LEFT_TRIPLE, action)
    fun setLeftLong(action: StemAction) = setSide(Side.LEFT_LONG, action)
    fun setRightSingle(action: StemAction) = setSide(Side.RIGHT_SINGLE, action)
    fun setRightDouble(action: StemAction) = setSide(Side.RIGHT_DOUBLE, action)
    fun setRightTriple(action: StemAction) = setSide(Side.RIGHT_TRIPLE, action)
    fun setRightLong(action: StemAction) = setSide(Side.RIGHT_LONG, action)

    fun resetAll() = launch {
        log(TAG, INFO) { "resetAll()" }
        val profileId = targetProfileId.value ?: return@launch
        profilesRepo.updateAppleProfile(profileId) { it.copy(stemActions = StemActionsConfig()) }
    }

    // ── AAP setters (device-scoped, not Pro-gated) ────────────────────────────────────────────

    private suspend fun currentAddress(): String? {
        val profileId = targetProfileId.value ?: return null
        return deviceMonitor.getDeviceForProfile(profileId)?.address
    }

    private fun send(command: AapCommand) = launch {
        val address = currentAddress() ?: return@launch
        try {
            aapManager.sendCommand(address, command)
            log(TAG, INFO) { "Sent $command to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $command: ${e.message}" }
            events.emit(Event.SendFailed(command, e.message))
        }
    }

    fun setPressSpeed(value: AapSetting.PressSpeed.Value) = send(AapCommand.SetPressSpeed(value))

    fun setPressHoldDuration(value: AapSetting.PressHoldDuration.Value) = send(AapCommand.SetPressHoldDuration(value))

    fun setEndCallMuteMic(
        muteMic: AapSetting.EndCallMuteMic.MuteMicMode,
        endCall: AapSetting.EndCallMuteMic.EndCallMode,
    ) = send(AapCommand.SetEndCallMuteMic(muteMic, endCall))

    companion object {
        private val TAG = logTag("PressControls", "VM")
    }
}
