package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FossUpgrade(
    @SerialName("upgradedAt") @Serializable(with = InstantEpochMillisSerializer::class) val upgradedAt: Instant,
    @SerialName("reason") val reason: Reason
) {
    @Serializable
    enum class Reason {
        @SerialName("foss.upgrade.reason.donated") DONATED,
        @SerialName("foss.upgrade.reason.alreadydonated") ALREADY_DONATED,
        @SerialName("foss.upgrade.reason.nomoney") NO_MONEY;
    }
}