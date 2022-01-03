package eu.darken.capod.pods.core

import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.DrawableRes
import eu.darken.capod.R
import java.time.Instant
import java.util.*
import kotlin.math.abs

interface PodDevice {

    val identifier: UUID

    val scanResult: ScanResult

    val lastSeenAt: Instant

    val rssi: Int
        get() = scanResult.rssi

    fun getSignalQuality(context: Context): String {
        val percentage = (100 - abs(rssi))
        return "~$percentage%"
    }

    fun getLabel(context: Context): String

    @get:DrawableRes
    val iconRes: Int
        get() = R.drawable.ic_baseline_earbuds_24
}