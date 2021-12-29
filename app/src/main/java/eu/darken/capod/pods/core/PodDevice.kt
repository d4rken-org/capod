package eu.darken.capod.pods.core

import android.bluetooth.le.ScanResult

interface PodDevice {

    val scanResult: ScanResult

    interface Status
}