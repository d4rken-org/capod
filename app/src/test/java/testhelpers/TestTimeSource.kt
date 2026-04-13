package testhelpers

import eu.darken.capod.common.TimeSource
import java.time.Duration
import java.time.Instant

class TestTimeSource(
    var wallNow: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    var elapsedRealtimeMs: Long = 0L,
    var elapsedRealtimeNanosValue: Long = 0L,
    var uptimeMillisValue: Long = 0L,
) : TimeSource {

    override fun now(): Instant = wallNow

    override fun currentTimeMillis(): Long = wallNow.toEpochMilli()

    override fun elapsedRealtime(): Long = elapsedRealtimeMs

    override fun elapsedRealtimeNanos(): Long = elapsedRealtimeNanosValue

    override fun uptimeMillis(): Long = uptimeMillisValue

    fun advanceBy(duration: Duration) {
        wallNow = wallNow.plus(duration)
        elapsedRealtimeMs += duration.toMillis()
        elapsedRealtimeNanosValue += duration.toNanos()
        uptimeMillisValue += duration.toMillis()
    }
}
