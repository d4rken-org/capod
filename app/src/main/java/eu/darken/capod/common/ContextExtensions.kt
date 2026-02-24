package eu.darken.capod.common

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent


@SuppressLint("NewApi")
fun Context.startServiceCompat(intent: Intent): ComponentName? {
    return if (hasApiLevel(26)) startForegroundService(intent) else startService(intent)
}