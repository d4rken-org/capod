package eu.darken.capod.debug.autoreport

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.bugsnag.android.Event
import com.bugsnag.android.OnErrorCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BugsnagErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bugsnagLogger: BugsnagLogger,
    private val debugSettings: DebugSettings,
) : OnErrorCallback {

    override fun onError(event: Event): Boolean {
        bugsnagLogger.injectLog(event)

        TAB_APP.also { tab ->
            event.addMetadata(tab, "gitSha", BuildConfigWrap.GIT_SHA)
            event.addMetadata(tab, "buildTime", BuildConfigWrap.BUILDTIME)

            context.tryFormattedSignature()?.let { event.addMetadata(tab, "signatures", it) }
        }

        return debugSettings.isAutoReportingEnabled.value && !eu.darken.capod.common.BuildConfigWrap.DEBUG
    }

    companion object {
        private const val TAB_APP = "app"

        @Suppress("DEPRECATION")
        @SuppressLint("PackageManagerGetSignatures")
        fun Context.tryFormattedSignature(): String? = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.let { sigs ->
                val sb = StringBuilder("[")
                for (i in sigs.indices) {
                    sb.append(sigs[i].hashCode())
                    if (i + 1 != sigs.size) sb.append(", ")
                }
                sb.append("]")
                sb.toString()
            }
        } catch (e: Exception) {
            log(WARN) { e.asLog() }
            null
        }
    }

}