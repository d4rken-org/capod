package eu.darken.capod.monitor.core

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.main.core.GeneralSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identity of a monitoring session, persisted while the monitor runs (see
 * [GeneralSettings.monitorSessionMark]). [pid] ties exit records to the exact process that was
 * monitoring, [versionCode] filters out app-update kills (which report REASON_USER_REQUESTED
 * before API 34).
 */
@Serializable
data class MonitorSessionMark(
    val startedAt: Long,
    val pid: Int,
    val versionCode: Long,
)

/**
 * Detects that the OS force-stopped us while the monitor was running (e.g. MIUI/HyperOS killing
 * the app when the user clears recents), so the UI can point the user at vendor settings
 * (autostart, battery restrictions, lock-in-recents).
 *
 * A force-stop can't be observed from inside the dying process: `onDestroy()`/`onTaskRemoved()`
 * are skipped and `START_STICKY` restarts are suppressed for force-stopped apps. Instead,
 * [GeneralSettings.monitorSessionMark] acts as a breadcrumb — set when monitoring starts, cleared
 * on clean service destroy — and on the next monitor start the previous mark is checked against
 * the system's [ApplicationExitInfo] records.
 */
@Singleton
class MonitorKillDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val timeSource: TimeSource,
) {

    /**
     * Marks the monitor as running and evaluates whether the previous monitoring session was
     * killed by the OS.
     *
     * The atomic swap makes this race-free against concurrent starts: a session that already
     * restarted leaves a mark *newer* than any exit record, which never qualifies — a kill the
     * system recovered from by itself needs no user-facing hint.
     */
    suspend fun onMonitorStart() {
        val current = MonitorSessionMark(
            startedAt = timeSource.currentTimeMillis(),
            pid = Process.myPid(),
            versionCode = BuildConfigWrap.VERSION_CODE,
        )
        val previous = generalSettings.monitorSessionMark.update { current }.old

        try {
            evaluate(previous = previous, current = current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Exit record evaluation failed: ${e.asLog()}" }
        }
    }

    /** Clears the breadcrumb — call on clean service destroy. Skipped by OS force-stops. */
    fun onMonitorCleanStopBlocking() {
        generalSettings.monitorSessionMark.valueBlocking = null
    }

    // NewApi: lint can't follow the hasApiLevel(30) guard below.
    @SuppressLint("NewApi")
    private suspend fun evaluate(previous: MonitorSessionMark?, current: MonitorSessionMark) {
        if (!hasApiLevel(30)) return

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val records = activityManager.getHistoricalProcessExitReasons(null, 0, MAX_RECORDS)
        if (records.isEmpty()) return

        val watermark = generalSettings.exitInfoWatermark.value()
        val newRecords = records.filter { it.timestamp > watermark }
        // Full record dumps end up in user debug logs — field data to verify/extend the
        // vendor-specific reason codes we match on.
        newRecords.forEach { log(TAG, INFO) { "New exit record: $it" } }

        val killedAt = findOsKill(
            records = newRecords.map { ExitRecord(reason = it.reason, timestamp = it.timestamp, pid = it.pid) },
            previous = previous,
            currentVersionCode = current.versionCode,
        )
        if (killedAt != null) {
            log(TAG, WARN) { "OS killed the monitor at $killedAt (session was $previous)" }
            generalSettings.lastOsKillAt.value(killedAt)
        }

        val newest = records.maxOf { it.timestamp }
        if (newest > watermark) generalSettings.exitInfoWatermark.value(newest)
    }

    internal data class ExitRecord(val reason: Int, val timestamp: Long, val pid: Int)

    companion object {
        private const val MAX_RECORDS = 10

        /**
         * Newest exit record showing the OS killed us while the monitor was running, or null.
         *
         * Only [ApplicationExitInfo.REASON_USER_REQUESTED] counts: MIUI-style force-stops
         * (recents-clear, security-app cleaners, settings force-stop) report it, while crashes,
         * low-memory kills and self-stops must not trigger the hint. REASON_SIGNALED is deliberately
         * excluded — LMKD SIGKILLs on ordinary low-memory devices would be false positives.
         *
         * A record must match the previous session's [MonitorSessionMark.pid] (so a swipe-kill of a
         * later UI-only process isn't blamed on an old monitoring session) and postdate its start
         * (which also dedups: once a new session's mark is written, older records can never match
         * again). Records are ignored entirely when the app version changed, because before API 34
         * package updates also killed the old process with REASON_USER_REQUESTED.
         */
        internal fun findOsKill(
            records: List<ExitRecord>,
            previous: MonitorSessionMark?,
            currentVersionCode: Long,
        ): Long? {
            if (previous == null) return null
            if (previous.versionCode != currentVersionCode) return null
            return records
                .filter { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED }
                .filter { it.pid == previous.pid }
                .filter { it.timestamp > previous.startedAt }
                .maxOfOrNull { it.timestamp }
        }

        private val TAG = logTag("Monitor", "KillDetector")
    }
}
