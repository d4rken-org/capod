package eu.darken.capod.common.upgrade.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
@JsonClass(generateAdapter = true)
data class FossUpgrade(
    @Serializable(with = InstantEpochMillisSerializer::class) val upgradedAt: Instant,
    val reason: Reason
) {
    @Serializable
    @JsonClass(generateAdapter = false)
    enum class Reason {
        @SerialName("foss.upgrade.reason.donated") @Json(name = "foss.upgrade.reason.donated") DONATED,
        @SerialName("foss.upgrade.reason.alreadydonated") @Json(name = "foss.upgrade.reason.alreadydonated") ALREADY_DONATED,
        @SerialName("foss.upgrade.reason.nomoney") @Json(name = "foss.upgrade.reason.nomoney") NO_MONEY;
    }
}