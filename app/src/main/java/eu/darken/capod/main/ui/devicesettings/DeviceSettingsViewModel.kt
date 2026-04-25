package eu.darken.capod.main.ui.devicesettings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.resolvedAncCycleMask
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.reaction.core.stem.StemAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceMonitor: DeviceMonitor,
    private val aapManager: AapConnectionManager,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothManager: BluetoothManager2,
    private val profilesRepo: DeviceProfilesRepo,
    private val generalSettings: GeneralSettings,
    private val timeSource: TimeSource,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    private val targetProfileId = MutableStateFlow<ProfileId?>(null)
    private var initialized = false

    fun initialize(profileId: ProfileId) {
        if (initialized && targetProfileId.value == profileId) return
        initialized = true
        targetProfileId.value = profileId
    }

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    private val isForceConnecting = MutableStateFlow(false)

    sealed interface Event {
        data object OpenBluetoothSettings : Event
        data class SendFailed(val command: AapCommand, val message: String?) : Event
        data object SystemRenameUnavailable : Event
        data object OffModeRejectedByDevice : Event
        data object DynamicEndOfChargeRejectedByDevice : Event
    }

    val events = SingleEventFlow<Event>()

    init {
        launch {
            aapManager.offRejectedEvents.collect { address ->
                if (address == currentAddress()) {
                    events.tryEmit(Event.OffModeRejectedByDevice)
                }
            }
        }
        launch {
            aapManager.settingRejectedEvents.collect { (address, command) ->
                if (address != currentAddress()) return@collect
                when (command) {
                    is AapCommand.SetDynamicEndOfCharge ->
                        events.tryEmit(Event.DynamicEndOfChargeRejectedByDevice)
                    else -> Unit // Other rejected commands handled elsewhere (e.g. ANC OFF)
                }
            }
        }
    }

    val state = targetProfileId.flatMapLatest { profileId ->
        if (profileId == null) return@flatMapLatest flowOf(State(device = null))
        combine(
            updateTicker,
            deviceForProfile(profileId),
            upgradeRepo.upgradeInfo,
            isForceConnecting,
            // The Bluetooth HEADSET profile lookup can be slow to produce its first value.
            // Seed this branch so the screen can render immediately after navigation.
            bluetoothManager.connectedDevices.onStart { emit(emptyList()) },
            generalSettings.monitorMode.flow,
            profilesRepo.profiles,
        ) { args ->
            val device = args[1] as PodDevice?
            val upgrade = args[2] as UpgradeRepo.Info
            val forcing = args[3] as Boolean

            @Suppress("UNCHECKED_CAST")
            val connectedDevices = args[4] as Collection<eu.darken.capod.common.bluetooth.BluetoothDevice2>
            val monitorMode = args[5] as MonitorMode

            @Suppress("UNCHECKED_CAST")
            val profiles = args[6] as List<eu.darken.capod.profiles.core.DeviceProfile>
            val stemActions = profiles.filterIsInstance<AppleDeviceProfile>()
                .firstOrNull { it.id == profileId }
                ?.stemActions
            val connectedAddresses = connectedDevices.map { it.address }.toSet()
            val systemBtName = device?.address?.let { addr ->
                try {
                    bluetoothManager.bondedDevices().first().firstOrNull { it.address == addr }?.name
                } catch (_: Exception) {
                    null
                }
            }
            State(
                device = device,
                now = timeSource.now(),
                isPro = upgrade.isPro,
                isNudgeAvailable = bluetoothManager.isNudgeAvailable,
                isForceConnecting = forcing,
                isClassicallyConnected = device?.address?.let { it in connectedAddresses } == true,
                monitorMode = monitorMode,
                systemBluetoothName = systemBtName,
                hasCustomLongPressStemAction = stemActions?.let {
                    (it.leftLong !is StemAction.None && it.leftLong !is StemAction.CycleAnc) ||
                        (it.rightLong !is StemAction.None && it.rightLong !is StemAction.CycleAnc)
                } == true,
            )
        }
    }.asLiveState()

    /**
     * Returns the [PodDevice] for [profileId] reactively. Falls back to a synthesized bare
     * device when the profile exists but isn't currently visible (no BLE / cache / AAP),
     * so the Reactions section can still be edited for pristine profiles.
     */
    private fun deviceForProfile(profileId: ProfileId): kotlinx.coroutines.flow.Flow<PodDevice?> =
        deviceMonitor.devices.flatMapLatest { devices ->
            val live = devices.firstOrNull { it.profileId == profileId }
            flow<PodDevice?> { emit(live ?: deviceMonitor.getDeviceForProfile(profileId)) }
        }

    data class State(
        val device: PodDevice?,
        val now: Instant = SystemTimeSource.now(),
        val isPro: Boolean = false,
        val isNudgeAvailable: Boolean = true,
        val isForceConnecting: Boolean = false,
        val isClassicallyConnected: Boolean = false,
        val monitorMode: MonitorMode = MonitorMode.AUTOMATIC,
        val systemBluetoothName: String? = null,
        val hasCustomLongPressStemAction: Boolean = false,
    ) {
        val reactions: ReactionConfig get() = device?.reactions ?: ReactionConfig()
    }

    private suspend fun currentAddress(): String? {
        val profileId = targetProfileId.value ?: return null
        return deviceMonitor.getDeviceForProfile(profileId)?.address
    }

    fun forceConnect() = launch {
        if (!isForceConnecting.compareAndSet(expect = false, update = true)) {
            log(TAG) { "forceConnect already in progress" }
            return@launch
        }
        try {
            val address = currentAddress() ?: run {
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            val bonded = try {
                bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }
            } catch (e: Exception) {
                log(TAG, WARN) { "bondedDevices() failed: ${e.message}" }
                null
            }
            if (bonded == null) {
                log(TAG, WARN) { "No bonded device for $address — opening Bluetooth settings" }
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            if (!bluetoothManager.isNudgeAvailable) {
                events.tryEmit(Event.OpenBluetoothSettings)
                return@launch
            }
            val accepted = try {
                bluetoothManager.nudgeConnection(bonded)
            } catch (e: Exception) {
                log(TAG, WARN) { "nudgeConnection threw: ${e.message}" }
                false
            }
            log(TAG, INFO) { "nudgeConnection($bonded) accepted=$accepted" }
            if (!accepted) {
                events.tryEmit(Event.OpenBluetoothSettings)
            }
        } finally {
            isForceConnecting.value = false
        }
    }

    private suspend fun sendInternal(command: AapCommand): Boolean {
        val address = currentAddress() ?: return false
        return try {
            aapManager.sendCommand(address, command)
            log(TAG, INFO) { "Sent $command to $address" }
            true
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send $command: ${e.message}" }
            events.emit(Event.SendFailed(command, e.message))
            false
        }
    }

    private fun send(command: AapCommand) {
        launch { sendInternal(command) }
    }

    private fun sendProGated(command: AapCommand) = launch {
        if (upgradeRepo.isPro()) {
            sendInternal(command)
        } else {
            navTo(Nav.Main.Upgrade)
        }
    }

    fun setAncMode(mode: AapSetting.AncMode.Value) = send(AapCommand.SetAncMode(mode))

    fun setConversationalAwareness(enabled: Boolean) = send(AapCommand.SetConversationalAwareness(enabled))

    fun setNcWithOneAirPod(enabled: Boolean) = send(AapCommand.SetNcWithOneAirPod(enabled))

    fun setPersonalizedVolume(enabled: Boolean) = send(AapCommand.SetPersonalizedVolume(enabled))

    fun setToneVolume(level: Int) = sendProGated(AapCommand.SetToneVolume(level))

    fun setAdaptiveAudioNoise(level: Int) = send(AapCommand.SetAdaptiveAudioNoise(level))

    fun setVolumeSwipe(enabled: Boolean) = send(AapCommand.SetVolumeSwipe(enabled))

    fun setVolumeSwipeLength(value: AapSetting.VolumeSwipeLength.Value) = send(AapCommand.SetVolumeSwipeLength(value))

    fun setMicrophoneMode(mode: AapSetting.MicrophoneMode.Mode) = sendProGated(AapCommand.SetMicrophoneMode(mode))

    fun setEarDetectionEnabled(enabled: Boolean) = send(AapCommand.SetEarDetectionEnabled(enabled))

    fun setListeningModeCycle(modeMask: Int) = sendProGated(AapCommand.SetListeningModeCycle(modeMask))

    fun setAllowOffOption(enabled: Boolean) = launch {
        if (!upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        if (enabled) {
            sendInternal(AapCommand.SetAllowOffOption(enabled = true))
        } else {
            val profileId = targetProfileId.value
            val currentMask = profileId
                ?.let { deviceMonitor.getDeviceForProfile(it) }
                ?.resolvedAncCycleMask
                ?: DEFAULT_CYCLE_MASK_WITH_OFF
            // Always send the cycle-mask update first — local state can diverge from the
            // device's actual mask since 0x1A is never echoed. Stripping OFF unconditionally
            // keeps the stem cycle consistent with the disabled capability.
            val newMask = currentMask and OFF_BIT.inv()
            sendInternal(AapCommand.SetListeningModeCycle(newMask))
            sendInternal(AapCommand.SetAllowOffOption(enabled = false))
        }
    }

    fun setSleepDetection(enabled: Boolean) = launch {
        log(TAG, INFO) { "setSleepDetection($enabled)" }
        if (enabled && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        sendInternal(AapCommand.SetSleepDetection(enabled))
    }

    fun setDynamicEndOfCharge(enabled: Boolean) = send(AapCommand.SetDynamicEndOfCharge(enabled))

    fun setDeviceName(name: String) = launch {
        val address = currentAddress() ?: return@launch
        try {
            aapManager.sendCommand(address, AapCommand.SetDeviceName(name))
            log(TAG, INFO) { "Sent SetDeviceName to $address" }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to send SetDeviceName: ${e.message}" }
            events.emit(Event.SendFailed(AapCommand.SetDeviceName(name), e.message))
        }

        val bonded = try {
            bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }
        } catch (e: Exception) {
            log(TAG, WARN) { "bondedDevices() failed while renaming: ${e.message}" }
            null
        }
        val aliasOk = bonded?.let { bluetoothManager.setDeviceAlias(it, name) } ?: false
        if (!aliasOk) {
            log(
                TAG,
                WARN
            ) { "System bond alias rename failed for $address — user must rename in system settings or re-pair" }
            events.emit(Event.SystemRenameUnavailable)
        }
    }

    // ── Reaction toggles (per-profile) ───────────────────────────────────────

    private suspend fun updateProfileNow(transform: (AppleDeviceProfile) -> AppleDeviceProfile) {
        val profileId = targetProfileId.value ?: return
        try {
            profilesRepo.updateAppleProfile(profileId, transform)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to update profile $profileId: ${e.message}" }
            errorEvents.emit(e)
        }
    }

    private fun proGatedReaction(
        enabled: Boolean,
        transform: (AppleDeviceProfile) -> AppleDeviceProfile,
    ) = launch {
        // Disabling never requires pro; enabling does.
        if (enabled && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        updateProfileNow(transform)
    }

    fun setOnePodMode(enabled: Boolean) {
        log(TAG, INFO) { "setOnePodMode($enabled)" }
        launch {
            updateProfileNow { it.copy(onePodMode = enabled) }
        }
    }

    fun setAutoPlay(enabled: Boolean) = launch {
        log(TAG, INFO) { "setAutoPlay($enabled)" }
        if (enabled && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        updateProfileNow { it.copy(autoPlay = enabled) }
        syncEarDetection(autoPlay = enabled)
    }

    fun setAutoPause(enabled: Boolean) = launch {
        log(TAG, INFO) { "setAutoPause($enabled)" }
        if (enabled && !upgradeRepo.isPro()) {
            navTo(Nav.Main.Upgrade)
            return@launch
        }
        updateProfileNow { it.copy(autoPause = enabled) }
        syncEarDetection(autoPause = enabled)
    }

    /**
     * Keeps the device-side Automatic Ear Detection setting in sync with the
     * auto-play / auto-pause reaction toggles.  When either reaction is active
     * the device must report ear-in / ear-out events; when both are off the
     * setting is disabled to match the user's intent.
     *
     * Only sends when the model supports the setting and AAP is ready —
     * silent no-op otherwise (reactions are per-profile and work offline).
     */
    private suspend fun syncEarDetection(autoPlay: Boolean? = null, autoPause: Boolean? = null) {
        val profileId = targetProfileId.value ?: return
        val device = deviceMonitor.getDeviceForProfile(profileId) ?: return
        if (device.model?.features?.hasEarDetectionToggle != true) return
        if (!device.isAapReady) return
        val reactions = device.reactions
        val effectiveAutoPlay = autoPlay ?: reactions.autoPlay
        val effectiveAutoPause = autoPause ?: reactions.autoPause
        sendInternal(AapCommand.SetEarDetectionEnabled(effectiveAutoPlay || effectiveAutoPause))
    }

    fun setAutoConnect(enabled: Boolean) = launch {
        log(TAG, INFO) { "setAutoConnect($enabled)" }
        updateProfileNow { it.copy(autoConnect = enabled) }
        if (enabled) {
            // Auto-connect requires ALWAYS mode to react to BLE/case/ear events when
            // nothing is connected. We intentionally do NOT revert on disable — the user
            // may have set ALWAYS manually, or another profile may still need it.
            if (generalSettings.monitorMode.value() != MonitorMode.ALWAYS) {
                log(TAG, INFO) { "Forcing monitorMode to ALWAYS because autoConnect was enabled" }
                generalSettings.monitorMode.value(MonitorMode.ALWAYS)
            }
        }
    }

    fun setAutoConnectCondition(condition: AutoConnectCondition) = launch {
        log(TAG, INFO) { "setAutoConnectCondition($condition)" }
        updateProfileNow { it.copy(autoConnectCondition = condition) }
    }

    fun setShowPopUpOnCaseOpen(enabled: Boolean) {
        log(TAG, INFO) { "setShowPopUpOnCaseOpen($enabled)" }
        proGatedReaction(enabled) { it.copy(showPopUpOnCaseOpen = enabled) }
    }

    fun setShowPopUpOnConnection(enabled: Boolean) {
        log(TAG, INFO) { "setShowPopUpOnConnection($enabled)" }
        proGatedReaction(enabled) { it.copy(showPopUpOnConnection = enabled) }
    }

    fun setMonitorModeAutomatic() = launch {
        log(TAG, INFO) { "setMonitorModeAutomatic()" }
        generalSettings.monitorMode.value(MonitorMode.AUTOMATIC)
    }

    fun navToPressControls() = launch {
        log(TAG, INFO) { "navToPressControls()" }
        val profileId = targetProfileId.value ?: return@launch
        navTo(Nav.Main.PressControls(profileId = profileId))
    }

    fun navToEditProfile() = launch {
        log(TAG, INFO) { "navToEditProfile()" }
        val profileId = targetProfileId.value ?: return@launch
        navTo(Nav.Main.DeviceProfileCreation(profileId = profileId))
    }

    fun launchUpgrade() {
        log(TAG, INFO) { "launchUpgrade()" }
        navTo(Nav.Main.Upgrade)
    }

    fun openIssueTracker() {
        webpageTool.open("https://github.com/d4rken-org/capod/issues")
    }

    fun openAapCompatibilityTracker() {
        webpageTool.open("https://github.com/d4rken-org/capod/issues/538")
    }

    companion object {
        private val TAG = logTag("DeviceSettings", "VM")
        private const val OFF_BIT = 0x01

        // Apple's factory-default listening-mode cycle mask: ON | TRANSPARENCY | ADAPTIVE.
        // Used as a conservative fallback when disabling Allow Off and we don't have a
        // live/persisted mask to mutate.
        private const val DEFAULT_CYCLE_MASK_WITH_OFF = 0x0F
    }
}
