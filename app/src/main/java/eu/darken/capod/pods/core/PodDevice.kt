package eu.darken.capod.pods.core

import android.bluetooth.le.ScanResult
import android.content.Context

interface PodDevice {

    val scanResult: ScanResult

    val rssi: Int
        get() = scanResult.rssi

    fun getLabel(context: Context): String
}