package eu.darken.capod.common.debug.logging

import android.annotation.SuppressLint
import android.util.Log
import eu.darken.capod.common.TimeSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Instant


@SuppressLint("LogNotTimber")
class FileLogger(
    private val logFile: File,
    private val timeSource: TimeSource,
) : Logging.Logger {
    private var logWriter: OutputStreamWriter? = null

    /**
     * A failure here belongs to the caller: swallowing it left an installed logger writing nowhere,
     * so a recording looked like it had started and produced an empty log.
     */
    @SuppressLint("SetWorldReadable")
    @Synchronized
    fun start() {
        if (logWriter != null) return

        logFile.parentFile!!.mkdirs()
        // Whether THIS attempt created the file decides what a failure below may delete: a resumed
        // session appends to a log file that already holds the previous recording, and failing to
        // open it must not erase that.
        val createdNow = logFile.createNewFile()
        if (createdNow) {
            Log.i(TAG, "File logger writing to " + logFile.path)
        }
        if (logFile.setReadable(true, false)) {
            Log.i(TAG, "Debug run log read permission set")
        }

        var writer: OutputStreamWriter? = null
        try {
            writer = OutputStreamWriter(FileOutputStream(logFile, true))
            writer.write("=== BEGIN ===\n")
            writer.write("Logfile: $logFile\n")
            writer.flush()
        } catch (e: IOException) {
            Log.e(TAG, "File logger failed to start.", e)
            try {
                writer?.close()
            } catch (ignore: IOException) {
            }
            if (createdNow) logFile.delete()
            throw e
        }

        // Published only once it is usable, so a failed attempt leaves nothing behind that would
        // make a later start() a no-op.
        logWriter = writer
        Log.i(TAG, "File logger started.")
    }

    @Synchronized
    fun stop() {
        logWriter?.let {
            logWriter = null
            try {
                it.write("=== END ===\n")
                it.close()
            } catch (ignore: IOException) {
            }
            Log.i(TAG, "File logger stopped.")
        }
    }

    override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
        logWriter?.let {
            try {
                it.write("${timeSource.now()}  ${priority.shortLabel}/$tag: $message\n")
                it.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log line.", e)
                try {
                    it.close()
                } catch (ignore: Exception) {
                }
                logWriter = null
            }
        }
    }

    override fun toString(): String = "FileLogger(file=$logFile)"

    companion object {
        private val TAG = logTag("Debug", "FileLogger")
    }
}
