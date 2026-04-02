package eu.darken.capod.monitor.core.cache

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.pods.core.apple.PodModel
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
    @Serializable(with = InstantEpochMillisSerializer::class)
    @SerialName("lastSeenAt") val lastSeenAt: Instant,
) {
    @Serializable
    data class CachedBatterySlot(
        @SerialName("percent") val percent: Float,
        @Serializable(with = InstantEpochMillisSerializer::class)
        @SerialName("updatedAt") val updatedAt: Instant,
    )
}