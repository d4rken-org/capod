package eu.darken.capod.pods.core

import android.content.Context
import androidx.annotation.DrawableRes
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.profiles.core.DeviceProfile
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

interface PodDevice {

    val identifier: Id

    val model: PodModel

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

    companion object {
        const val BASE_CONFIDENCE = 0.0f
    }
}