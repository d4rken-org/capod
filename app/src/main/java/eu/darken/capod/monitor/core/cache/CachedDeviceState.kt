package eu.darken.capod.monitor.core.cache

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CachedDeviceState(
    @SerialName("profileId") val profileId: String,
    @SerialName("model") val model: PodModel,
    @SerialName("address") val address: String? = null,
    @SerialName("left") val left: CachedBatterySlot? = null,
    @SerialName("right") val right: CachedBatterySlot? = null,
    @SerialName("case") val case: CachedBatterySlot? = null,
    @SerialName("headset") val headset: CachedBatterySlot? = null,
    @SerialName("isLeftCharging") val isLeftCharging: Boolean? = null,
    @SerialName("isRightCharging") val isRightCharging: Boolean? = null,
    @SerialName("isCaseCharging") val isCaseCharging: Boolean? = null,
    @SerialName("isHeadsetCharging") val isHeadsetCharging: Boolean? = null,
    @SerialName("deviceName") val deviceName: String? = null,
    @SerialName("serialNumber") val serialNumber: String? = null,
    @SerialName("firmwareVersion") val firmwareVersion: String? = null,
    @SerialName("leftEarbudSerial") val leftEarbudSerial: String? = null,
    @SerialName("rightEarbudSerial") val rightEarbudSerial: String? = null,
    /**
     * Formerly known as `buildNumber` — the Wireshark AAP dissector calls this
     * "Marketing Version". The `@SerialName("buildNumber")` preserves the wire
     * key so cache entries from older CAPod versions keep deserializing.
     */
    @SerialName("buildNumber") val marketingVersion: String? = null,
    @Serializable(with = InstantEpochMillisSerializer::class)
    @SerialName("lastSeenAt") val lastSeenAt: Instant,
) {
    val deviceInfo: AapDeviceInfo?
        get() {
            if (deviceName == null && serialNumber == null && firmwareVersion == null
                && leftEarbudSerial == null && rightEarbudSerial == null && marketingVersion == null
            ) return null
            return AapDeviceInfo(
                name = deviceName ?: "",
                modelNumber = "",
                manufacturer = "",
                serialNumber = serialNumber ?: "",
                firmwareVersion = firmwareVersion ?: "",
                leftEarbudSerial = leftEarbudSerial,
                rightEarbudSerial = rightEarbudSerial,
                marketingVersion = marketingVersion,
            )
        }

    @Serializable
    data class CachedBatterySlot(
        @SerialName("percent") val percent: Float,
        @Serializable(with = InstantEpochMillisSerializer::class)
        @SerialName("updatedAt") val updatedAt: Instant,
    )
}