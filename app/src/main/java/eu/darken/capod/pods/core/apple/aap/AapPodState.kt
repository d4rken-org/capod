package eu.darken.capod.pods.core.apple.aap

import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Pure data representing the current state of an AAP connection. No connection handle.
 */
data class AapPodState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceInfo: AapDeviceInfo? = null,
    val settings: Map<KClass<out AapSetting>, AapSetting> = emptyMap(),
    val batteries: Map<BatteryType, Battery> = emptyMap(),
    val lastMessageAt: Instant? = null,
) {
    inline fun <reified T : AapSetting> setting(): T? = settings[T::class] as? T

    fun withSetting(key: KClass<out AapSetting>, value: AapSetting): AapPodState =
        copy(settings = settings + (key to value))

    // Battery — from AAP command 0x04, 1% granularity
    val batteryLeft: Float? get() = batteries[BatteryType.LEFT]?.percent
    val batteryRight: Float? get() = batteries[BatteryType.RIGHT]?.percent
    val batteryCase: Float? get() = batteries[BatteryType.CASE]?.percent
    val batteryHeadset: Float? get() = batteries[BatteryType.SINGLE]?.percent

    // Charging state from AAP battery
    val isLeftCharging: Boolean?
        get() = batteries[BatteryType.LEFT]?.let { it.charging == ChargingState.CHARGING || it.charging == ChargingState.CHARGING_OPTIMIZED }
    val isRightCharging: Boolean?
        get() = batteries[BatteryType.RIGHT]?.let { it.charging == ChargingState.CHARGING || it.charging == ChargingState.CHARGING_OPTIMIZED }
    val isCaseCharging: Boolean?
        get() = batteries[BatteryType.CASE]?.let { it.charging == ChargingState.CHARGING || it.charging == ChargingState.CHARGING_OPTIMIZED }
    val isHeadsetCharging: Boolean?
        get() = batteries[BatteryType.SINGLE]?.let { it.charging == ChargingState.CHARGING || it.charging == ChargingState.CHARGING_OPTIMIZED }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        HANDSHAKING,
        READY,
    }

    /** Battery entry from an AAP battery notification (command 0x04). */
    data class Battery(
        val type: BatteryType,
        val percent: Float,
        val charging: ChargingState,
    )

    enum class BatteryType(val wireValue: Int) {
        SINGLE(0x01),
        RIGHT(0x02),
        LEFT(0x04),
        CASE(0x08),
        ;

        companion object {
            fun fromWire(value: Int): BatteryType? = entries.firstOrNull { it.wireValue == value }
        }
    }

    enum class ChargingState(val wireValue: Int) {
        UNDEFINED(0x00),
        CHARGING(0x01),
        NOT_CHARGING(0x02),
        DISCONNECTED(0x04),
        // TODO: Observed on AirPods Pro 3 in charging case. Possibly Apple's "Optimized Battery Charging" limit.
        //  Verify behavior with Pro 2 and Pro 1 to confirm whether this is model-specific or firmware-specific.
        CHARGING_OPTIMIZED(0x05),
        ;

        companion object {
            fun fromWire(value: Int): ChargingState = entries.firstOrNull { it.wireValue == value } ?: UNDEFINED
        }
    }
}
