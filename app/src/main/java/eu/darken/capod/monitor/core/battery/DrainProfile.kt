package eu.darken.capod.monitor.core.battery

import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.pods.core.apple.PodModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Persisted drain-rate knowledge for a single device profile, keyed per ANC-mode bucket AND per pod
 * — map key is `"<bucket>/<slot>"`, e.g. `"ON/LEFT"`, `"UNKNOWN/HEADSET"`. Drain differs sharply by
 * both mode and pod (the mic pod drains faster), so each is learned independently. Seeds the
 * time-remaining estimate immediately on reconnect, before the live session has enough samples.
 *
 * [chargeRates] is the charging counterpart, keyed per slot only (`"LEFT"` / `"RIGHT"` /
 * `"HEADSET"`) — the ANC mode is irrelevant while a pod sits in the case.
 *
 * [model] tags which [PodModel] the rates were learned on, so a profile re-assigned to different
 * hardware doesn't inherit the old device's rates (see [matchesModel]).
 */
@Serializable
data class DrainProfile(
    @SerialName("model") val model: String? = null,
    @SerialName("rates") val rates: Map<String, LearnedRate> = emptyMap(),
    @SerialName("chargeRates") val chargeRates: Map<String, LearnedRate> = emptyMap(),
) {
    @Serializable
    data class LearnedRate(
        /** Drain (or charge) rate in fraction/hour (e.g. 0.169 = 16.9 %/hr). */
        @SerialName("fractionPerHour") val fractionPerHour: Float,
        @SerialName("sampleCount") val sampleCount: Int,
        /**
         * How many distinct sessions have blended into this rate. [sampleCount] is only the window
         * size at the LAST save, so this is the actual accumulated-evidence signal (used e.g. to
         * gate the derived battery-health figure).
         */
        @SerialName("updateCount") val updateCount: Int = 1,
        @Serializable(with = InstantEpochMillisSerializer::class)
        @SerialName("updatedAt") val updatedAt: Instant,
    )

    /**
     * Whether these learned rates apply to [model]. An untagged profile or an UNKNOWN model on
     * either side is treated as matching — only a definite known-A vs known-B mismatch (the user
     * re-pointed the profile at different hardware) disqualifies the data.
     */
    fun matchesModel(model: PodModel): Boolean =
        this.model == null ||
            model == PodModel.UNKNOWN ||
            this.model == PodModel.UNKNOWN.name ||
            this.model == model.name

    companion object {
        /** Bucket key for rates learned while the ANC mode wasn't known (BLE-only sessions). */
        const val BUCKET_UNKNOWN = "UNKNOWN"
    }
}
