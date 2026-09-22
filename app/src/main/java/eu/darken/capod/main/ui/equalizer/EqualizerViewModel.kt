package eu.darken.capod.main.ui.equalizer

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
import eu.darken.capod.common.upgrade.isProForUi
import eu.darken.capod.main.ui.devicesettings.cards.CUSTOM_EQ_NEUTRAL
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceMonitor: DeviceMonitor,
    private val aapManager: AapConnectionManager,
    private val upgradeRepo: UpgradeRepo,
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

    /**
     * The equalizer the user is editing. Every `SetCustomEq` is built from this, never from the
     * device snapshot at callback time: a single write carries all four fields, so a command
     * assembled from a stale snapshot would revert the bands that were moved just before it.
     */
    private val draft = MutableStateFlow<AapSetting.CustomEq?>(null)

    val state = targetProfileId.flatMapLatest { profileId ->
        if (profileId == null) return@flatMapLatest flowOf(State())
        combine(
            deviceForProfile(profileId),
            draft,
            upgradeRepo.upgradeInfo,
        ) { device, currentDraft, upgrade ->
            val observed = device?.customEq
            State(
                device = device,
                isPro = upgrade.isPro,
                isAapReady = device?.isAapReady == true,
                hasPendingWrite = device?.hasPendingSettings == true,
                observed = observed,
                draft = reconcile(currentDraft, observed),
            )
        }
    }.asLiveState()

    /**
     * An inbound equalizer is adopted only while the draft is still empty, or when it matches the
     * draft (an echo). A differing inbound value belongs to [State.observed] alone — adopting it
     * would drop the user's in-progress edit on the floor.
     */
    private fun reconcile(
        currentDraft: AapSetting.CustomEq?,
        observed: AapSetting.CustomEq?,
    ): AapSetting.CustomEq? {
        if (currentDraft != null || observed == null) return currentDraft
        draft.compareAndSet(null, observed)
        return draft.value
    }

    private fun deviceForProfile(profileId: ProfileId): Flow<PodDevice?> =
        deviceMonitor.devices.flatMapLatest { devices ->
            val live = devices.firstOrNull { it.profileId == profileId }
            flow<PodDevice?> { emit(live ?: deviceMonitor.getDeviceForProfile(profileId)) }
        }

    data class State(
        val device: PodDevice? = null,
        val isPro: Boolean = false,
        val isAapReady: Boolean = false,
        /** A write is queued behind the ear-detection gate and hasn't reached the device yet. */
        val hasPendingWrite: Boolean = false,
        /** What the device reported. Null while it never reported an equalizer. */
        val observed: AapSetting.CustomEq? = null,
        /** What the user is editing. Null while there is nothing to show. */
        val draft: AapSetting.CustomEq? = null,
    )

    /** Applies [transform] to the draft and sends the result. Pro-gated, like every send here. */
    private fun edit(transform: (AapSetting.CustomEq) -> AapSetting.CustomEq) = launch {
        if (!upgradeRepo.isProForUi()) {
            navTo(Nav.Main.Upgrade())
            return@launch
        }
        // Atomic: two edits landing together must not each build a command from the pre-edit draft.
        val next = checkNotNull(draft.updateAndGet { transform(it ?: NEUTRAL_EQ) })
        sendEq(next)
    }

    /**
     * Offered when nothing is known: there is no read primitive, so this applies a fresh neutral
     * equalizer rather than continuing whatever the device currently holds.
     */
    fun setUpNeutral() {
        log(TAG, INFO) { "setUpNeutral()" }
        edit { NEUTRAL_EQ }
    }

    /** Disabling keeps the bands, so switching back restores the user's curve. */
    fun setEnabled(enabled: Boolean) {
        log(TAG, INFO) { "setEnabled($enabled)" }
        edit { it.copy(enabled = enabled) }
    }

    fun setLow(value: Int) = edit { it.copy(low = value.coerceIn(BAND_RANGE)) }

    fun setMid(value: Int) = edit { it.copy(mid = value.coerceIn(BAND_RANGE)) }

    fun setHigh(value: Int) = edit { it.copy(high = value.coerceIn(BAND_RANGE)) }

    /** Flattens the bands while preserving whether the equalizer is on. */
    fun resetToNeutral() {
        log(TAG, INFO) { "resetToNeutral()" }
        edit { it.copy(low = CUSTOM_EQ_NEUTRAL, mid = CUSTOM_EQ_NEUTRAL, high = CUSTOM_EQ_NEUTRAL) }
    }

    private suspend fun sendEq(eq: AapSetting.CustomEq) {
        val command = AapCommand.SetCustomEq(
            enabled = eq.enabled,
            low = eq.low,
            mid = eq.mid,
            high = eq.high,
        )
        val profileId = targetProfileId.value ?: return
        val address = deviceMonitor.getDeviceForProfile(profileId)?.address ?: return
        try {
            aapManager.sendCommand(address, command)
            log(TAG, INFO) { "Sent $command to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $command: ${e.message}" }
            events.emit(Event.SendFailed(command, e.message))
        }
    }

    companion object {
        private val BAND_RANGE = 0..100
        private val NEUTRAL_EQ = AapSetting.CustomEq(
            enabled = true,
            low = CUSTOM_EQ_NEUTRAL,
            mid = CUSTOM_EQ_NEUTRAL,
            high = CUSTOM_EQ_NEUTRAL,
        )
        private val TAG = logTag("Equalizer", "VM")
    }
}
