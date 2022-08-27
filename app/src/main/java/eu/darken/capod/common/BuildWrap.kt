package eu.darken.capod.common

import android.os.Build

// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildWrap {

    val VERSION = VersionWrap

    object VersionWrap {
        val SDK_INT = Build.VERSION.SDK_INT
    }
}

fun hasApiLevel(level: Int): Boolean = BuildWrap.VERSION.SDK_INT >= level

fun withinApiLevel(start: Int, end: Int): Boolean = BuildWrap.VERSION.SDK_INT in start..end
