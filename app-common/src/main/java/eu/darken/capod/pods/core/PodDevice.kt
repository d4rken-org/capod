package eu.darken.capod.pods.core

import android.content.Context
import androidx.annotation.DrawableRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.common.R
import eu.darken.capod.common.bluetooth.BleScanResult
import java.time.Instant
import java.util.*
import kotlin.math.abs
import kotlin.math.max

interface PodDevice {

    val identifier: Id

    val model: Model

    val address: String
        get() = scanResult.address

    val seenLastAt: Instant

    val seenFirstAt: Instant

    val seenCounter: Int

    val scanResult: BleScanResult

    val rssi: Int
        get() = scanResult.rssi

    val confidence: Float

    /**
     * This is not correct but it works ¯\_(ツ)_/¯
     * The range of the RSSI is device specific (ROMs).
     */
    val signalQuality: Float
        get() = ((100 - abs(rssi)) / 100f) * (max(BASE_CONFIDENCE, confidence))

    val rawData: Map<Int, ByteArray>
        get() = scanResult.manufacturerSpecificData

    val rawDataHex: List<String>
        get() = rawData.entries.map { entry ->
            "${entry.key}: ${entry.value.joinToString(separator = " ") { String.format("%02X", it) }}"
        }

    fun getLabel(context: Context): String = model.label

    @get:DrawableRes
    val iconRes: Int
        get() = model.iconRes

    @JvmInline
    value class Id(private val id: UUID = UUID.randomUUID())

    @JsonClass(generateAdapter = false)
    enum class Model(
        val label: String,
        @DrawableRes val iconRes: Int = R.drawable.devic_earbuds_generic_both,
    ) {
        @Json(name = "airpods.gen1") AIRPODS_GEN1(
            label = "AirPods (Gen 1)",
            iconRes = R.drawable.devic_airpods_gen1_both,
        ),
        @Json(name = "airpods.gen2") AIRPODS_GEN2(
            "AirPods (Gen 2)",
            R.drawable.devic_airpods_gen2_both,
        ),
        @Json(name = "airpods.gen3") AIRPODS_GEN3(
            "AirPods (Gen 3)",
            R.drawable.devic_airpods_gen2_both,
        ),
        @Json(name = "airpods.pro") AIRPODS_PRO(
            "AirPods Pro",
            R.drawable.devic_airpods_pro2_both
        ),
        @Json(name = "airpods.pro2") AIRPODS_PRO2(
            "AirPods Pro 2",
            R.drawable.devic_airpods_pro2_both
        ),
        @Json(name = "airpods.max") AIRPODS_MAX(
            "AirPods Max",
            R.drawable.devic_headphones_generic
        ),
        @Json(name = "beats.flex") BEATS_FLEX(
            "Beats Flex"
        ),
        @Json(name = "beats.solo.3") BEATS_SOLO_3(
            "Beats Solo 3"
        ),
        @Json(name = "beats.studio.3") BEATS_STUDIO_3(
            "Beats Studio 3"
        ),
        @Json(name = "beats.x") BEATS_X(
            "Beats X"
        ),
        @Json(name = "beats.powerbeats.3") POWERBEATS_3(
            "Power Beats 3"
        ),
        @Json(name = "beats.powerbeats.pro") POWERBEATS_PRO(
            "Power Beats Pro"
        ),
        @Json(name = "beats.fit.pro") BEATS_FIT_PRO(
            "Beats Fit Pro"
        ),
        @Json(name = "fakes.tws.i99999") FAKE_AIRPODS_GEN1(
            "AirPods (Gen 1)? \uD83C\uDFAD",
            R.drawable.devic_airpods_gen1_both,
        ),
        @Json(name = "fakes.generic.airpods.gen2") FAKE_AIRPODS_GEN2(
            "AirPods (Gen 2)? \uD83C\uDFAD",
            R.drawable.devic_airpods_gen2_both,
        ),
        @Json(name = "fakes.generic.airpods.gen3") FAKE_AIRPODS_GEN3(
            "AirPods (Gen 3)? \uD83C\uDFAD",
            R.drawable.devic_airpods_gen2_both,
        ),
        @Json(name = "fakes.varunr.airpodspro") FAKE_AIRPODS_PRO(
            "AirPods Pro? \uD83C\uDFAD",
            R.drawable.devic_airpods_pro2_both,
        ),
        @Json(name = "fakes.generic.airpods.pro2") FAKE_AIRPODS_PRO2(
            "AirPods Pro2? \uD83C\uDFAD",
            R.drawable.devic_airpods_pro2_both,
        ),
        @Json(name = "unknown") UNKNOWN(
            "Unknown"
        );
    }

    companion object {
        const val BASE_CONFIDENCE = 0.5f
    }
}