package eu.darken.capod.common.debug.recording.core

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.debug.logging.FileLogger
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class Recorder @Inject constructor(
    private val timeSource: TimeSource,
) {
    private val mutex = Mutex()
    private var fileLogger: FileLogger? = null

    val isRecording: Boolean
        get() = path != null

    var path: File? = null
        private set

    suspend fun start(path: File) = mutex.withLock {
        if (fileLogger != null) return@withLock
        this.path = path
        fileLogger = FileLogger(path, timeSource)
        fileLogger?.let {
            it.start()
            Logging.install(it)
            log(TAG, INFO) { "Now logging to file!" }
        }
    }

    suspend fun stop() = mutex.withLock {
        val logger = fileLogger ?: return@withLock
        // A half-finished stop is worse than a failed one: the logger stays installed globally and
        // keeps writing into a session nobody tracks any more. So cancellation cannot interrupt it,
        // and a throw on the way out still uninstalls, closes and clears.
        withContext(NonCancellable) {
            try {
                log(TAG, INFO) { "Stopping file-logger-tree: $logger" }
                try {
                    Logging.remove(logger)
                } finally {
                    logger.stop()
                }
            } finally {
                fileLogger = null
                this@Recorder.path = null
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder")
    }

}
