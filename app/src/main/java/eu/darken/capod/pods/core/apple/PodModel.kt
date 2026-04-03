package eu.darken.capod.pods.core.apple

import androidx.annotation.DrawableRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PodModel(
    val label: String,
    @DrawableRes val iconRes: Int = R.drawable.device_earbuds_generic_both,
    val features: Features = Features(),
    val modelNumbers: Set<String> = emptySet(),
    @DrawableRes val leftPodIconRes: Int? = null,
    @DrawableRes val rightPodIconRes: Int? = null,
    @DrawableRes val caseIconRes: Int? = null,
) {
    @SerialName("airpods.gen1")
    AIRPODS_GEN1(
        "AirPods (Gen 1)",
        R.drawable.device_airpods_gen1_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        modelNumbers = setOf("A1523", "A1722"), // L/R earphones
        leftPodIconRes = R.drawable.device_airpods_gen1_left,
        rightPodIconRes = R.drawable.device_airpods_gen1_right,
        caseIconRes = R.drawable.device_airpods_gen1_case,
    ),

    @SerialName("airpods.gen2")
    AIRPODS_GEN2(
        "AirPods (Gen 2)",
        R.drawable.device_airpods_gen1_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        modelNumbers = setOf("A2031", "A2032"), // L/R earphones
        leftPodIconRes = R.drawable.device_airpods_gen1_left,
        rightPodIconRes = R.drawable.device_airpods_gen1_right,
        caseIconRes = R.drawable.device_airpods_gen1_case,
    ),

    @SerialName("airpods.gen3")
    AIRPODS_GEN3(
        "AirPods (Gen 3)",
        R.drawable.device_airpods_gen3_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        modelNumbers = setOf("A2564", "A2565"), // L/R earphones
        leftPodIconRes = R.drawable.device_airpods_gen3_left,
        rightPodIconRes = R.drawable.device_airpods_gen3_right,
        caseIconRes = R.drawable.device_airpods_gen3_case,
    ),

    @SerialName("airpods.gen4")
    AIRPODS_GEN4(
        "AirPods (Gen 4)",
        R.drawable.device_airpods_gen3_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
        ),
        modelNumbers = setOf("A3050", "A3053", "A3054"), // earphones
        leftPodIconRes = R.drawable.device_airpods_gen3_left,
        rightPodIconRes = R.drawable.device_airpods_gen3_right,
        caseIconRes = R.drawable.device_airpods_gen3_case,
    ),

    @SerialName("airpods.gen4.anc")
    AIRPODS_GEN4_ANC(
        "AirPods (Gen 4 ANC)",
        R.drawable.device_airpods_gen4anc_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
            hasAdaptiveAnc = true,
            hasConversationAwareness = true,
            hasNcOneAirpod = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasPersonalizedVolume = true,
            hasToneVolume = true,
            hasEndCallMuteMic = true,
            hasAdaptiveAudioNoise = true,
            needsInitExt = true,
        ),
        modelNumbers = setOf("A3055", "A3056", "A3057"), // earphones
        leftPodIconRes = R.drawable.device_airpods_gen4anc_left,
        rightPodIconRes = R.drawable.device_airpods_gen4anc_right,
        caseIconRes = R.drawable.device_airpods_gen4anc_case,
    ),

    @SerialName("airpods.pro")
    AIRPODS_PRO(
        "AirPods Pro",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
            hasNcOneAirpod = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasToneVolume = true,
            hasEndCallMuteMic = true,
        ),
        modelNumbers = setOf("A2083", "A2084"), // L/R earphones
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("airpods.pro2")
    AIRPODS_PRO2(
        "AirPods Pro 2",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
            hasAdaptiveAnc = true,
            hasConversationAwareness = true,
            hasNcOneAirpod = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasVolumeSwipe = true,
            hasVolumeSwipeLength = true,
            hasPersonalizedVolume = true,
            hasToneVolume = true,
            hasEndCallMuteMic = true,
            hasAdaptiveAudioNoise = true,
            needsInitExt = true,
        ),
        modelNumbers = setOf("A2698", "A2699", "A2931"), // earphones
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("airpods.pro2.usbc")
    AIRPODS_PRO2_USBC(
        "AirPods Pro 2 USB-C",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
            hasAdaptiveAnc = true,
            hasConversationAwareness = true,
            hasNcOneAirpod = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasVolumeSwipe = true,
            hasVolumeSwipeLength = true,
            hasPersonalizedVolume = true,
            hasToneVolume = true,
            hasEndCallMuteMic = true,
            hasAdaptiveAudioNoise = true,
            needsInitExt = true,
        ),
        modelNumbers = setOf("A3047", "A3048", "A3049"), // earphones
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("airpods.pro3")
    AIRPODS_PRO3(
        "AirPods Pro 3",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
            hasAdaptiveAnc = true,
            hasConversationAwareness = true,
            hasNcOneAirpod = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasVolumeSwipe = true,
            hasVolumeSwipeLength = true,
            hasPersonalizedVolume = true,
            hasToneVolume = true,
            hasEndCallMuteMic = true,
            hasAdaptiveAudioNoise = true,
            needsInitExt = true,
        ),
        modelNumbers = setOf("A3063", "A3064", "A3065"), // earphones
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("airpods.max")
    AIRPODS_MAX(
        "AirPods Max",
        R.drawable.device_airpods_max,
        Features(
            hasEarDetection = true,
            hasAncControl = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasToneVolume = true,
        ),
        modelNumbers = setOf("A2096"), // headphones
    ),

    @SerialName("airpods.max.usbc")
    AIRPODS_MAX_USBC(
        "AirPods Max USB-C",
        R.drawable.device_airpods_max,
        Features(
            hasEarDetection = true,
            hasAncControl = true,
            hasPressSpeed = true,
            hasPressHoldDuration = true,
            hasToneVolume = true,
        ),
        modelNumbers = setOf("A3184"), // headphones
    ),

    @SerialName("beats.flex")
    BEATS_FLEX(
        "Beats Flex",
        R.drawable.device_beats_earbuds,
        modelNumbers = setOf("A2295"),
    ),

    @SerialName("beats.solo.3")
    BEATS_SOLO_3(
        "Beats Solo 3",
        R.drawable.device_beats_headphones,
        modelNumbers = setOf("A1796"), // headphones
    ),

    @SerialName("beats.solo.pro")
    BEATS_SOLO_PRO(
        "Beats Solo Pro",
        R.drawable.device_beats_headphones,
        Features(
            hasEarDetection = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A1881"), // headphones
    ),

    @SerialName("beats.solo.4")
    BEATS_SOLO_4(
        "Beats Solo 4",
        R.drawable.device_beats_headphones,
        modelNumbers = setOf("A3140"), // headphones
    ),

    @SerialName("beats.solo.buds")
    BEATS_SOLO_BUDS(
        "Beats Solo Buds",
        R.drawable.device_beats_earbuds,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        modelNumbers = setOf("A3150", "A3151", "A3153"), // L/R earbuds + case
    ),

    @SerialName("beats.studio.3")
    BEATS_STUDIO_3(
        "Beats Studio 3",
        R.drawable.device_beats_studio3,
        Features(
            hasEarDetection = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A1914"), // headphones
    ),

    @SerialName("beats.studio.buds")
    BEATS_STUDIO_BUDS(
        "Beats Studio Buds",
        R.drawable.device_beats_earbuds,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A2512", "A2513", "A2514"), // L/R earbuds + case
    ),

    @SerialName("beats.studio.buds.plus")
    BEATS_STUDIO_BUDS_PLUS(
        "Beats Studio Buds+",
        R.drawable.device_beats_earbuds,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A2871", "A2872", "A2952"), // L/R earbuds + case
    ),

    @SerialName("beats.studio.pro")
    BEATS_STUDIO_PRO(
        "Beats Studio Pro",
        R.drawable.device_beats_headphones,
        Features(hasAncControl = true),
        modelNumbers = setOf("A2924"), // headphones
    ),

    @SerialName("beats.x")
    BEATS_X(
        "Beats X",
        R.drawable.device_beats_x,
        modelNumbers = setOf("A1763"),
    ),

    @SerialName("beats.powerbeats.3")
    POWERBEATS_3(
        "Power Beats 3",
        R.drawable.device_powerbeats_3,
        modelNumbers = setOf("A1747"),
    ),

    @SerialName("beats.powerbeats.4")
    POWERBEATS_4(
        "Power Beats 4",
        R.drawable.device_powerbeats_4,
        modelNumbers = setOf("A2015"),
    ),

    @SerialName("beats.powerbeats.pro")
    POWERBEATS_PRO(
        "Power Beats Pro",
        R.drawable.device_powerbeats_pro_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
        ),
        modelNumbers = setOf("A2047", "A2048", "A2453", "A2454"), // L/R earbuds, 2019 + 2020 revisions
        leftPodIconRes = R.drawable.device_powerbeats_pro_left,
        rightPodIconRes = R.drawable.device_powerbeats_pro_right,
        caseIconRes = R.drawable.device_powerbeats_pro_case,
    ),

    @SerialName("beats.powerbeats.pro2")
    POWERBEATS_PRO2(
        "Power Beats Pro 2",
        R.drawable.device_powerbeats_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A3157", "A3158", "A3159"), // L/R earbuds + case
        leftPodIconRes = R.drawable.device_powerbeats_pro2_left,
        rightPodIconRes = R.drawable.device_powerbeats_pro2_right,
        caseIconRes = R.drawable.device_powerbeats_pro2_case,
    ),

    @SerialName("beats.fit.pro")
    BEATS_FIT_PRO(
        "Beats Fit Pro",
        R.drawable.device_beats_fitpro_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
        ),
        modelNumbers = setOf("A2576", "A2577", "A2578"), // L/R earbuds + case
        leftPodIconRes = R.drawable.device_beats_fitpro_left,
        rightPodIconRes = R.drawable.device_beats_fitpro_right,
        caseIconRes = R.drawable.device_beats_fitpro_case,
    ),

    @SerialName("fakes.tws.i99999")
    FAKE_AIRPODS_GEN1(
        "AirPods (Gen 1)? \uD83C\uDFAD",
        R.drawable.device_airpods_gen1_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        leftPodIconRes = R.drawable.device_airpods_gen1_left,
        rightPodIconRes = R.drawable.device_airpods_gen1_right,
        caseIconRes = R.drawable.device_airpods_gen1_case,
    ),

    @SerialName("fakes.generic.airpods.gen2")
    FAKE_AIRPODS_GEN2(
        "AirPods (Gen 2)? \uD83C\uDFAD",
        R.drawable.device_airpods_gen1_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        leftPodIconRes = R.drawable.device_airpods_gen1_left,
        rightPodIconRes = R.drawable.device_airpods_gen1_right,
        caseIconRes = R.drawable.device_airpods_gen1_case,
    ),

    @SerialName("fakes.generic.airpods.gen3")
    FAKE_AIRPODS_GEN3(
        "AirPods (Gen 3)? \uD83C\uDFAD",
        R.drawable.device_airpods_gen3_both,
        Features(
            hasDualPods = true,
            hasCase = true,
        ),
        leftPodIconRes = R.drawable.device_airpods_gen3_left,
        rightPodIconRes = R.drawable.device_airpods_gen3_right,
        caseIconRes = R.drawable.device_airpods_gen3_case,
    ),

    @SerialName("fakes.varunr.airpodspro")
    FAKE_AIRPODS_PRO(
        "AirPods Pro? \uD83C\uDFAD",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
        ),
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("fakes.generic.airpods.pro2")
    FAKE_AIRPODS_PRO2(
        "AirPods Pro2? \uD83C\uDFAD",
        R.drawable.device_airpods_pro2_both,
        Features(
            hasDualPods = true,
            hasCase = true,
            hasEarDetection = true,
            hasAncControl = true,
        ),
        leftPodIconRes = R.drawable.device_airpods_pro2_left,
        rightPodIconRes = R.drawable.device_airpods_pro2_right,
        caseIconRes = R.drawable.device_airpods_pro2_case,
    ),

    @SerialName("unknown")
    UNKNOWN("Unknown"),
    ;

    companion object {
        fun fromModelNumber(modelNumber: String): PodModel? {
            val normalized = modelNumber.trim().uppercase()
            if (normalized.isBlank()) return null
            return entries.firstOrNull { entry ->
                entry != UNKNOWN && !entry.name.startsWith("FAKE_") && normalized in entry.modelNumbers
            }
        }
    }

    data class Features(
        // Physical form
        val hasDualPods: Boolean = false,
        val hasCase: Boolean = false,
        val hasEarDetection: Boolean = false,
        // ANC
        val hasAncControl: Boolean = false,
        val hasAdaptiveAnc: Boolean = false,
        // AAP settings
        val hasConversationAwareness: Boolean = false,
        val hasNcOneAirpod: Boolean = false,
        val hasPressSpeed: Boolean = false,
        val hasPressHoldDuration: Boolean = false,
        val hasVolumeSwipe: Boolean = false,
        val hasVolumeSwipeLength: Boolean = false,
        val hasPersonalizedVolume: Boolean = false,
        val hasToneVolume: Boolean = false,
        val hasEndCallMuteMic: Boolean = false,
        val hasAdaptiveAudioNoise: Boolean = false,
        // Protocol
        val needsInitExt: Boolean = false,
    )
}
