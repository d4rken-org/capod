package eu.darken.capod.common

import android.os.SystemClock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TimeSource {
    fun now(): Instant
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    fun elapsedRealtimeNanos(): Long
    fun uptimeMillis(): Long
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
}

@Singleton
class DefaultTimeSource @Inject constructor() : TimeSource by SystemTimeSource
