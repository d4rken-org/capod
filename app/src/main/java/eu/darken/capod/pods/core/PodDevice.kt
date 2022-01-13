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

    /**
     * This is not correct but it works ¯\_(ツ)_/¯
     * The range of the RSSI is device specific (ROMs).
     */
    val signalQuality: Float
        get() = (100 - abs(rssi)) / 100f

    val rawData: ByteArray

    val rawDataHex: String
        get() = rawData.joinToString(separator = " ") { String.format("%02X", it) }

    fun getLabel(context: Context): String

    @get:DrawableRes
    val iconRes: Int
        get() = R.drawable.ic_device_generic_earbuds

    @JvmInline
    value class Id(private val id: UUID = UUID.randomUUID())
}