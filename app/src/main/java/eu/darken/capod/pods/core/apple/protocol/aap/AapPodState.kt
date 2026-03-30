package eu.darken.capod.pods.core.apple.protocol.aap

import kotlin.reflect.KClass

/**
 * Pure data representing the current state of an AAP connection. No connection handle.
 */
data class AapPodState(
    val connectionState: AapConnectionState = AapConnectionState.DISCONNECTED,
    val deviceInfo: AapDeviceInfo? = null,
    val settings: Map<KClass<out AapSetting>, AapSetting> = emptyMap(),
) {
    inline fun <reified T : AapSetting> setting(): T? = settings[T::class] as? T

    fun withSetting(key: KClass<out AapSetting>, value: AapSetting): AapPodState =
        copy(settings = settings + (key to value))
}

enum class AapConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    READY,
}
