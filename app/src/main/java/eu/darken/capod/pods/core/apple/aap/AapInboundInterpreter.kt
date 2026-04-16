package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapMessage
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.pods.core.apple.aap.protocol.StemPressEvent
import kotlin.reflect.KClass

internal sealed interface AapInboundUpdate {
    data class StemPress(val event: StemPressEvent) : AapInboundUpdate
    data class Battery(val batteries: Map<AapPodState.BatteryType, AapPodState.Battery>) : AapInboundUpdate
    data class PrivateKeys(val result: KeyExchangeResult) : AapInboundUpdate
    data class DeviceInfo(val info: AapDeviceInfo) : AapInboundUpdate
    data class Setting(val key: KClass<out AapSetting>, val value: AapSetting) : AapInboundUpdate
}

internal class AapInboundInterpreter(
    private val profile: AapDeviceProfile,
) {
    fun decode(message: AapMessage): AapInboundUpdate? {
        profile.decodeStemPress(message)?.let { return AapInboundUpdate.StemPress(it) }
        profile.decodeBattery(message)?.let { return AapInboundUpdate.Battery(it) }
        profile.decodePrivateKeyResponse(message)?.let { return AapInboundUpdate.PrivateKeys(it) }
        profile.decodeDeviceInfo(message)?.let { return AapInboundUpdate.DeviceInfo(it) }
        profile.decodeSetting(message)?.let { (key, value) ->
            return AapInboundUpdate.Setting(key, value)
        }
        return null
    }
}
