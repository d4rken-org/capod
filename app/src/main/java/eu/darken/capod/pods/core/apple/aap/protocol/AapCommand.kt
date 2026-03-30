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
    data class SetEndCallMuteMic(val muteMic: AapSetting.EndCallMuteMic.MuteMicMode, val endCall: AapSetting.EndCallMuteMic.EndCallMode) : AapCommand()
    data class SetVolumeSwipe(val enabled: Boolean) : AapCommand()
    data class SetPersonalizedVolume(val enabled: Boolean) : AapCommand()
    data class SetAdaptiveAudioNoise(val level: Int) : AapCommand()
}
