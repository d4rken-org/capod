package eu.darken.capod.monitor.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject

/**
 * Remedies for OS kills detected by [MonitorKillDetector]: vendor-specific "don't kill my app"
 * instructions and, where the device has one (MIUI/HyperOS), the autostart permission screen.
 */
@Reusable
class MonitorKillGuidance @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
) {

    /**
     * Whether the MIUI/HyperOS autostart settings screen exists on this device.
     * Requires the `com.miui.securitycenter` `<queries>` entry in the manifest — package
     * visibility filtering makes this resolve to null otherwise on API 30+.
     */
    val hasAutostartSettings: Boolean by lazy {
        autostartIntent.resolveActivity(context.packageManager) != null
    }

    fun openKillInstructions() {
        webpageTool.open(dontKillMyAppUrl(Build.MANUFACTURER))
    }

    fun openAutostartSettings() {
        try {
            context.startActivity(autostartIntent)
        } catch (e: Exception) {
            // ActivityNotFoundException on most builds, SecurityException on some MIUI versions.
            log(TAG, WARN) { "Failed to open autostart settings: ${e.asLog()}" }
            openKillInstructions()
        }
    }

    private val autostartIntent: Intent
        get() = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    companion object {
        internal fun dontKillMyAppUrl(manufacturer: String): String {
            val slug = when (manufacturer.lowercase()) {
                "xiaomi", "redmi", "poco" -> "xiaomi"
                "huawei", "honor" -> "huawei"
                "samsung" -> "samsung"
                "oneplus" -> "oneplus"
                "oppo" -> "oppo"
                "realme" -> "realme"
                "vivo", "iqoo" -> "vivo"
                "meizu" -> "meizu"
                "asus" -> "asus"
                "sony" -> "sony"
                "lenovo" -> "lenovo"
                "motorola" -> "motorola"
                else -> null
            }
            return if (slug != null) "https://dontkillmyapp.com/$slug" else "https://dontkillmyapp.com/"
        }

        private val TAG = logTag("Monitor", "KillGuidance")
    }
}
