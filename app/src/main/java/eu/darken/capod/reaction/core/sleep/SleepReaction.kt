package eu.darken.capod.reaction.core.sleep

import eu.darken.capod.common.MediaControl
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepReaction @Inject constructor(
    private val aapManager: AapConnectionManager,
    private val deviceMonitor: DeviceMonitor,
    private val mediaControl: MediaControl,
    private val notifications: SleepReactionNotifications,
    private val timeSource: TimeSource,
) {

    private val cooldowns = ConcurrentHashMap<BluetoothAddress, Long>()

    fun monitor(): Flow<Unit> = aapManager.sleepEvents
        .onEach { address -> handle(address) }
        .map { }
        .setupCommonEventHandlers(TAG) { "sleepReaction" }

    private suspend fun handle(address: BluetoothAddress) {
        val now = timeSource.elapsedRealtime()
        val last = cooldowns[address]
        if (last != null && now - last < COOLDOWN_MS) {
            log(TAG) { "Sleep event from $address suppressed by cooldown" }
            return
        }
        val primary = deviceMonitor.primaryDevice().first()
        if (primary?.address != address) {
            log(TAG) { "Sleep event from $address ignored — not primary device (primary=${primary?.address})" }
            return
        }
        // Use sendPause's return value as the atomic check+act: true means we really paused
        // something, false means nothing was playing. Gating the cooldown and notification on
        // this closes the race where audio could stop between an upfront isPlaying check and
        // the key dispatch, and avoids burning the 5-minute window on no-ops.
        val paused = mediaControl.sendPause()
        if (!paused) {
            log(TAG) { "Sleep event from $address ignored — nothing was playing" }
            return
        }
        cooldowns[address] = now
        val label = primary.label ?: primary.model.label
        log(TAG, INFO) { "Sleep detected on $address ($label) — paused media, notifying" }
        notifications.show(label)
    }

    companion object {
        private val TAG = logTag("Reaction", "Sleep")
        private const val COOLDOWN_MS = 5L * 60L * 1000L
    }
}
