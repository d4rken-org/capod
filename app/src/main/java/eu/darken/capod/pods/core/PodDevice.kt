package eu.darken.capod.pods.core

import android.content.Context
import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import java.time.Instant
import java.util.*
import kotlin.math.abs

interface PodDevice {

    val identifier: Id

    val lastSeenAt: Instant

    val scanResult: BleScanResult

    val rssi: Int
        get() = scanResult.rssi

    val rawData: ByteArray

    val rawDataHex: String
        get() = rawData.joinToString(separator = " ") { String.format("%02X", it) }

    fun getSignalQuality(context: Context): String {
        val percentage = (100 - abs(rssi))
        return "~$percentage%"
    }

    fun getLabel(context: Context): String

    fun getStatusShort(context: Context): String

    fun getStatusLong(context: Context): List<String>

    @get:DrawableRes
    val iconRes: Int
        get() = R.drawable.ic_device_generic_earbuds

    @JvmInline
    value class Id(private val id: UUID = UUID.randomUUID())
}