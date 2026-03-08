package eu.darken.capod.common.debug.recording.core

import java.io.File

sealed interface DebugSession {
    val id: String
    val displayName: String
    val createdAt: Long
    val diskSize: Long

    data class Recording(
        override val id: String,
        override val displayName: String,
        override val createdAt: Long,
        override val diskSize: Long,
        val path: File,
        val startedAt: Long,
    ) : DebugSession

    data class Compressing(
        override val id: String,
        override val displayName: String,
        override val createdAt: Long,
        override val diskSize: Long,
        val path: File,
    ) : DebugSession

    data class Ready(
        override val id: String,
        override val displayName: String,
        override val createdAt: Long,
        override val diskSize: Long,
        val logDir: File?,
        val zipFile: File?,
        val compressedSize: Long,
    ) : DebugSession

    data class Failed(
        override val id: String,
        override val displayName: String,
        override val createdAt: Long,
        override val diskSize: Long,
        val path: File,
        val reason: Reason,
    ) : DebugSession {
        enum class Reason { EMPTY_LOG, MISSING_LOG, CORRUPT_ZIP, ZIP_FAILED }
    }
}
