package eu.darken.capod.pods.core

import android.content.Context
import androidx.annotation.DrawableRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.profiles.core.DeviceProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

interface PodDevice {

    val identifier: Id

    val model: Model

    val address: BluetoothAddress
        get() = scanResult.address

    val seenLastAt: Instant

    val seenFirstAt: Instant

    val seenCounter: Int

    val scanResult: BleScanResult

    val rssi: Int
        get() = scanResult.rssi

    val reliability: Float

    val signalQuality: Float
        get() {
            /**
             * This is not correct but it works ¯\_(ツ)_/¯
             * The range of the RSSI is device specific (ROMs).
             */
            val sqRssi = ((100 - abs(rssi)) / 100f)
            val sqReliability = max(BASE_CONFIDENCE, reliability)
            val sqAge = (Duration.between(seenFirstAt, Instant.now()).toMinutes().coerceAtMost(60) / 60f) * 0.25f
            log(VERBOSE) { "Signal Quality ($address): rssi=$sqRssi, reliability=$reliability, age=$sqAge" }
            return (sqRssi + sqReliability + sqAge) / 2f
        }

    val rawData: Map<Int, ByteArray>
        get() = scanResult.manufacturerSpecificData

    val rawDataHex: List<String>
        get() = rawData.entries.map { entry ->
            "${entry.key}: ${entry.value.joinToString(separator = " ") { String.format("%02X", it) }}"
        }

    interface Meta {
        val profile: DeviceProfile?
    }

    val meta: Meta

    fun getLabel(context: Context): String = model.label

    @get:DrawableRes
    val iconRes: Int
        get() = model.iconRes

    @JvmInline
    value class Id(private val id: UUID = UUID.randomUUID())

    @Serializable
    @JsonClass(generateAdapter = false)
    enum class Model(
        val label: String,
        @DrawableRes val iconRes: Int = R.drawable.device_earbuds_generic_both,
    ) {
        @SerialName("airpods.gen1") @Json(name = "airpods.gen1") AIRPODS_GEN1(
            label = "AirPods (Gen 1)",
            iconRes = R.drawable.device_airpods_gen1_both,
        ),
        @SerialName("airpods.gen2") @Json(name = "airpods.gen2") AIRPODS_GEN2(
            "AirPods (Gen 2)",
            R.drawable.device_airpods_gen1_both,
        ),
        @SerialName("airpods.gen3") @Json(name = "airpods.gen3") AIRPODS_GEN3(
            "AirPods (Gen 3)",
            R.drawable.device_airpods_gen3_both,
        ),
        @SerialName("airpods.gen4") @Json(name = "airpods.gen4") AIRPODS_GEN4(
            "AirPods (Gen 4)",
            R.drawable.device_airpods_gen3_both,
        ),
        @SerialName("airpods.gen4.anc") @Json(name = "airpods.gen4.anc") AIRPODS_GEN4_ANC(
            "AirPods (Gen 4 ANC)",
            R.drawable.device_airpods_gen4anc_both,
        ),
        @SerialName("airpods.pro") @Json(name = "airpods.pro") AIRPODS_PRO(
            "AirPods Pro",
            R.drawable.device_airpods_pro2_both
        ),
        @SerialName("airpods.pro2") @Json(name = "airpods.pro2") AIRPODS_PRO2(
            "AirPods Pro 2",
            R.drawable.device_airpods_pro2_both
        ),
        @SerialName("airpods.pro2.usbc") @Json(name = "airpods.pro2.usbc") AIRPODS_PRO2_USBC(
            "AirPods Pro 2 USB-C",
            R.drawable.device_airpods_pro2_both
        ),
        @SerialName("airpods.pro3") @Json(name = "airpods.pro3") AIRPODS_PRO3(
            "AirPods Pro 3",
            R.drawable.device_airpods_pro2_both
        ),
        @SerialName("airpods.max") @Json(name = "airpods.max") AIRPODS_MAX(
            "AirPods Max",
            R.drawable.device_airpods_max
        ),
        @SerialName("airpods.max.usbc") @Json(name = "airpods.max.usbc") AIRPODS_MAX_USBC(
            "AirPods Max USB-C",
            R.drawable.device_airpods_max
        ),
        @SerialName("beats.flex") @Json(name = "beats.flex") BEATS_FLEX(
            "Beats Flex",
            R.drawable.device_beats_earbuds,
        ),
        @SerialName("beats.solo.3") @Json(name = "beats.solo.3") BEATS_SOLO_3(
            "Beats Solo 3",
            R.drawable.device_beats_headphones,
        ),
        @SerialName("beats.studio.3") @Json(name = "beats.studio.3") BEATS_STUDIO_3(
            "Beats Studio 3",
            R.drawable.device_beats_studio3,
        ),
        @SerialName("beats.x") @Json(name = "beats.x") BEATS_X(
            "Beats X",
            R.drawable.device_beats_x,
        ),
        @SerialName("beats.powerbeats.3") @Json(name = "beats.powerbeats.3") POWERBEATS_3(
            "Power Beats 3",
            R.drawable.device_powerbeats_3,
        ),
        @SerialName("beats.powerbeats.4") @Json(name = "beats.powerbeats.4") POWERBEATS_4(
            "Power Beats 4",
            R.drawable.device_powerbeats_4,
        ),
        @SerialName("beats.powerbeats.pro") @Json(name = "beats.powerbeats.pro") POWERBEATS_PRO(
            "Power Beats Pro",
            R.drawable.device_powerbeats_pro_both,
        ),
        @SerialName("beats.powerbeats.pro2") @Json(name = "beats.powerbeats.pro2") POWERBEATS_PRO2(
            "Power Beats Pro 2",
            R.drawable.device_powerbeats_pro2_both,
        ),
        @SerialName("beats.fit.pro") @Json(name = "beats.fit.pro") BEATS_FIT_PRO(
            "Beats Fit Pro",
            R.drawable.device_beats_fitpro_both,
        ),
        @SerialName("fakes.tws.i99999") @Json(name = "fakes.tws.i99999") FAKE_AIRPODS_GEN1(
            "AirPods (Gen 1)? \uD83C\uDFAD",
            R.drawable.device_airpods_gen1_both,
        ),
        @SerialName("fakes.generic.airpods.gen2") @Json(name = "fakes.generic.airpods.gen2") FAKE_AIRPODS_GEN2(
            "AirPods (Gen 2)? \uD83C\uDFAD",
            R.drawable.device_airpods_gen1_both,
        ),
        @SerialName("fakes.generic.airpods.gen3") @Json(name = "fakes.generic.airpods.gen3") FAKE_AIRPODS_GEN3(
            "AirPods (Gen 3)? \uD83C\uDFAD",
            R.drawable.device_airpods_gen3_both,
        ),
        @SerialName("fakes.varunr.airpodspro") @Json(name = "fakes.varunr.airpodspro") FAKE_AIRPODS_PRO(
            "AirPods Pro? \uD83C\uDFAD",
            R.drawable.device_airpods_pro2_both,
        ),
        @SerialName("fakes.generic.airpods.pro2") @Json(name = "fakes.generic.airpods.pro2") FAKE_AIRPODS_PRO2(
            "AirPods Pro2? \uD83C\uDFAD",
            R.drawable.device_airpods_pro2_both,
        ),
        @SerialName("unknown") @Json(name = "unknown") UNKNOWN(
            "Unknown"
        );
    }

    companion object {
        const val BASE_CONFIDENCE = 0.0f
    }
}