package eu.darken.capod.pods.core

import androidx.annotation.DrawableRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PodModel(
    val label: String,
    @DrawableRes val iconRes: Int = R.drawable.device_earbuds_generic_both,
    val features: Features = Features(),
) {
    @SerialName("airpods.gen1") AIRPODS_GEN1(
        "AirPods (Gen 1)", R.drawable.device_airpods_gen1_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("airpods.gen2") AIRPODS_GEN2(
        "AirPods (Gen 2)", R.drawable.device_airpods_gen1_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("airpods.gen3") AIRPODS_GEN3(
        "AirPods (Gen 3)", R.drawable.device_airpods_gen3_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("airpods.gen4") AIRPODS_GEN4(
        "AirPods (Gen 4)", R.drawable.device_airpods_gen3_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("airpods.gen4.anc") AIRPODS_GEN4_ANC(
        "AirPods (Gen 4 ANC)", R.drawable.device_airpods_gen4anc_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("airpods.pro") AIRPODS_PRO(
        "AirPods Pro", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("airpods.pro2") AIRPODS_PRO2(
        "AirPods Pro 2", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("airpods.pro2.usbc") AIRPODS_PRO2_USBC(
        "AirPods Pro 2 USB-C", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("airpods.pro3") AIRPODS_PRO3(
        "AirPods Pro 3", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("airpods.max") AIRPODS_MAX(
        "AirPods Max", R.drawable.device_airpods_max,
        Features(hasAncControl = true),
    ),
    @SerialName("airpods.max.usbc") AIRPODS_MAX_USBC(
        "AirPods Max USB-C", R.drawable.device_airpods_max,
        Features(hasAncControl = true),
    ),
    @SerialName("beats.flex") BEATS_FLEX(
        "Beats Flex", R.drawable.device_beats_earbuds,
    ),
    @SerialName("beats.solo.3") BEATS_SOLO_3(
        "Beats Solo 3", R.drawable.device_beats_headphones,
    ),
    @SerialName("beats.studio.3") BEATS_STUDIO_3(
        "Beats Studio 3", R.drawable.device_beats_studio3,
        Features(hasAncControl = true),
    ),
    @SerialName("beats.x") BEATS_X(
        "Beats X", R.drawable.device_beats_x,
    ),
    @SerialName("beats.powerbeats.3") POWERBEATS_3(
        "Power Beats 3", R.drawable.device_powerbeats_3,
    ),
    @SerialName("beats.powerbeats.4") POWERBEATS_4(
        "Power Beats 4", R.drawable.device_powerbeats_4,
    ),
    @SerialName("beats.powerbeats.pro") POWERBEATS_PRO(
        "Power Beats Pro", R.drawable.device_powerbeats_pro_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true),
    ),
    @SerialName("beats.powerbeats.pro2") POWERBEATS_PRO2(
        "Power Beats Pro 2", R.drawable.device_powerbeats_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("beats.fit.pro") BEATS_FIT_PRO(
        "Beats Fit Pro", R.drawable.device_beats_fitpro_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("fakes.tws.i99999") FAKE_AIRPODS_GEN1(
        "AirPods (Gen 1)? \uD83C\uDFAD", R.drawable.device_airpods_gen1_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("fakes.generic.airpods.gen2") FAKE_AIRPODS_GEN2(
        "AirPods (Gen 2)? \uD83C\uDFAD", R.drawable.device_airpods_gen1_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("fakes.generic.airpods.gen3") FAKE_AIRPODS_GEN3(
        "AirPods (Gen 3)? \uD83C\uDFAD", R.drawable.device_airpods_gen3_both,
        Features(hasDualPods = true, hasCase = true),
    ),
    @SerialName("fakes.varunr.airpodspro") FAKE_AIRPODS_PRO(
        "AirPods Pro? \uD83C\uDFAD", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("fakes.generic.airpods.pro2") FAKE_AIRPODS_PRO2(
        "AirPods Pro2? \uD83C\uDFAD", R.drawable.device_airpods_pro2_both,
        Features(hasDualPods = true, hasCase = true, hasEarDetection = true, hasAncControl = true),
    ),
    @SerialName("unknown") UNKNOWN(
        "Unknown"
    );

    data class Features(
        val hasDualPods: Boolean = false,
        val hasCase: Boolean = false,
        val hasEarDetection: Boolean = false,
        val hasAncControl: Boolean = false,
    )
}