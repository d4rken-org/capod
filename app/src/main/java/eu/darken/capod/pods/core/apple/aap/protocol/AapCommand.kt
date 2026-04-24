package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Outbound commands to change device settings. Pure domain — no wire protocol bytes.
 * The [AapDeviceProfile] encodes these into the device-specific wire format.
 */
sealed class AapCommand {
    data class SetAncMode(val mode: AapSetting.AncMode.Value) : AapCommand()
    data class SetConversationalAwareness(val enabled: Boolean) : AapCommand()
    data class SetPressSpeed(val value: AapSetting.PressSpeed.Value) : AapCommand()
    data class SetPressHoldDuration(val value: AapSetting.PressHoldDuration.Value) : AapCommand()
    data class SetNcWithOneAirPod(val enabled: Boolean) : AapCommand()
    data class SetToneVolume(val level: Int) : AapCommand()
    data class SetVolumeSwipeLength(val value: AapSetting.VolumeSwipeLength.Value) : AapCommand()
    data class SetEndCallMuteMic(
        val muteMic: AapSetting.EndCallMuteMic.MuteMicMode,
        val endCall: AapSetting.EndCallMuteMic.EndCallMode,
    ) : AapCommand() {
        init {
            require(
                (muteMic == AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS &&
                    endCall == AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS) ||
                    (muteMic == AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS &&
                        endCall == AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS)
            ) { "muteMic and endCall must be complementary press actions" }
        }
    }
    data class SetVolumeSwipe(val enabled: Boolean) : AapCommand()
    data class SetPersonalizedVolume(val enabled: Boolean) : AapCommand()
    data class SetAdaptiveAudioNoise(val level: Int) : AapCommand()
    data class SetMicrophoneMode(val mode: AapSetting.MicrophoneMode.Mode) : AapCommand()
    data class SetEarDetectionEnabled(val enabled: Boolean) : AapCommand()
    data class SetListeningModeCycle(val modeMask: Int) : AapCommand()
    data class SetAllowOffOption(val enabled: Boolean) : AapCommand()
    data class SetStemConfig(val claimedPressMask: Int) : AapCommand()
    data class SetSleepDetection(val enabled: Boolean) : AapCommand()
    data class SetDynamicEndOfCharge(val enabled: Boolean) : AapCommand()
    data class SetDeviceName(val name: String) : AapCommand()
}
