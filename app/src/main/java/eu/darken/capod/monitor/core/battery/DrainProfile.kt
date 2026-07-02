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
    /**
     * Per-band charge rates, `"<slot>" -> "<band>" -> rate` with band names from
     * [DrainModel.ChargeBand]. Charging is nonlinear (fast bulk, slow taper/trickle), so each
     * regime learns its own rate; [chargeRates] stays as the whole-session scalar fallback.
     */
    @SerialName("chargeBands") val chargeBands: Map<String, Map<String, LearnedRate>> = emptyMap(),
    /**
     * Drain rates learned ONLY while the pod was worn and audio was actually playing on this
     * device — same `"<bucket>/<slot>"` keys as [rates]. Apple's battery ratings are listening
     * figures, so the battery-health estimate compares against these; the general [rates]
     * (which include idle wear) keep powering the time-remaining estimate.
     */
    @SerialName("listeningRates") val listeningRates: Map<String, LearnedRate> = emptyMap(),
    /**
     * Learned case transfer efficiency: summed pod battery-fraction gained per case fraction spent
     * while docked and unplugged, corrected for current pod health (degraded pods gain % faster).
     * Compared against the nominal ratio from Apple's "with charging case" totals to derive the
     * case battery health.
     */
    @SerialName("caseTransfer") val caseTransfer: TransferRatio? = null,
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

    @Serializable
    data class TransferRatio(
        /** Summed pod fraction gained per case fraction spent (health-corrected). */
        @SerialName("ratio") val ratio: Float,
        /** How many observed docked-discharge sessions have blended into this ratio. */
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
