package eu.darken.capod.pods.core.apple.ble

import android.content.Context
import androidx.annotation.DrawableRes
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.DeviceProfile
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.max

interface BlePodSnapshot {

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

    /**
     * Pure RSSI quality for display bars — linear map of -100..-30 dBm → 0..1.
     * Does not include reliability/age. Use for SignalIndicator only, not for
     * device matching or sorting (that's signalQuality's job).
     */
    val rssiQuality: Float
        get() = ((rssi + 100) / 70f).coerceIn(0f, 1f)

    /**
     * Composite quality for profile matching and sorting. Weighted blend of
     * RSSI strength, detection reliability, and observation age.
     * Used by AppleFactory (minimumSignalQuality filter) and TroubleShooter
     * (closest-device selection). Not used for display bars.
     */
    val signalQuality: Float
        get() {
            val sqRssi = ((rssi + 100) / 70f).coerceIn(0f, 1f)
            val sqReliability = max(BASE_CONFIDENCE, reliability)
            val sqAge = (Duration.between(seenFirstAt, SystemTimeSource.now()).toMinutes().coerceAtMost(60) / 60f) * 0.25f
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
