package eu.darken.capod.pods.core.unknown

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.DeviceProfile
import java.time.Instant

data class UnknownSnapshotBle(
    override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(),
    override val seenLastAt: Instant = SystemTimeSource.now(),
    override val seenFirstAt: Instant = SystemTimeSource.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val reliability: Float = 0f,
    override val meta: Meta = Meta(),
    private val rssiAverage: Int? = null,
) : BlePodSnapshot {

    override val model: PodModel = PodModel.UNKNOWN

    override fun getLabel(context: Context): String = context.getString(R.string.pods_unknown_label)

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    data class Meta(
        override val profile: DeviceProfile? = null
    ) : BlePodSnapshot.Meta

    companion object {
        private val TAG = logTag("PodDevice", "Unknown")
    }
}
