package eu.darken.capod.pods.core.apple.history

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.collections.median
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import java.time.Duration
import java.time.Instant
import kotlin.math.max

data class KnownDevice(
    val id: PodDevice.Id,
    val seenFirstAt: Instant,
    val seenCounter: Int,
    val history: List<ApplePods>,
    val lastCaseBattery: Float?,
) {
    val lastPayload: ProximityPayload
        get() = history.last().payload

    val lastAddress: BluetoothAddress
        get() = history.last().address

    val reliability: Float
        get() {
            if (history.size < 2) return 0f

            val now = Instant.now()

            val pingInterval = List(history.dropLast(1).size) { index ->
                Duration.between(history[index].seenLastAt, history[index + 1].seenLastAt).toMillis()
            }.map { it.toInt() }.median()

            val expectedPings: Float = LOOKBACK.toMillis() / pingInterval.toFloat()

            val recentPings = history.filter {
                Duration.between(it.seenLastAt, now).toMillis() < LOOKBACK.toMillis() + pingInterval
            }.size

            val reliability: Float = (recentPings / expectedPings)
            // log("#####") { "interval=$pingInterval, expectedPerMinute=$expectedPings, count=$recentPings" }

            return max(0.0f, reliability).coerceAtMost(1f)
        }

    fun rssiSmoothed(latest: Int): Int {
        val now = Instant.now()
        return history
            .filter { Duration.between(it.seenLastAt, now) < LOOKBACK }
            .map { it.rssi }
            .plus(latest)
            .median()
    }

    fun isOlderThan(age: Duration): Boolean {
        val now = Instant.now()
        return Duration.between(history.last().seenLastAt, now) > age
    }

    override fun toString(): String = "KnownDevice(history=${history.size}, last=${history.last()})"

    companion object {
        // 30 seconds at fastest interval
        const val MAX_HISTORY = 60
        val LOOKBACK = Duration.ofSeconds(30)
    }
}