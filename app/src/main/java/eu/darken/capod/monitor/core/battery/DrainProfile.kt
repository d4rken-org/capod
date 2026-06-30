package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Persisted drain-rate knowledge for a single device profile, keyed per ANC-mode bucket AND per pod
 * — map key is `"<bucket>/<slot>"`, e.g. `"ON/LEFT"`, `"UNKNOWN/HEADSET"`. Drain differs sharply by
 * both mode and pod (the mic pod drains faster), so each is learned independently. Seeds the
 * time-remaining estimate immediately on reconnect, before the live session has enough samples.
 */
@Serializable
data class DrainProfile(
    @SerialName("rates") val rates: Map<String, LearnedRate> = emptyMap(),
) {
    @Serializable
    data class LearnedRate(
        /** Drain rate in fraction/hour (e.g. 0.169 = 16.9 %/hr). */
        @SerialName("fractionPerHour") val fractionPerHour: Float,
        @SerialName("sampleCount") val sampleCount: Int,
        @Serializable(with = InstantEpochMillisSerializer::class)
        @SerialName("updatedAt") val updatedAt: Instant,
    )
}
