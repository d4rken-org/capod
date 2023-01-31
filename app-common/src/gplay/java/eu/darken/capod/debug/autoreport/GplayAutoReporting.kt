package eu.darken.capod.debug.autoreport

import android.app.Application
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.getkeepsafe.relinker.ReLinker
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GplayAutoReporting @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debugSettings: DebugSettings,
    private val installId: InstallId,
    private val bugsnagLogger: Provider<BugsnagLogger>,
    private val bugsnagErrorHandler: Provider<BugsnagErrorHandler>,
    private val nopBugsnagErrorHandler: Provider<NOPBugsnagErrorHandler>,
) : AutomaticBugReporter {

    override fun setup(application: Application) {
        val isEnabled = debugSettings.isAutoReportingEnabled.value
        log(TAG) { "setup(): isEnabled=$isEnabled" }

        if (!isEnabled) return

        ReLinker
            .log { message -> log(TAG) { "ReLinker: $message" } }
            .loadLibrary(application, "bugsnag-plugin-android-anr")

        try {
            val bugsnagConfig = Configuration.load(context).apply {
                if (debugSettings.isAutoReportingEnabled.value) {
                    Logging.install(bugsnagLogger.get())
                    setUser(installId.id, null, null)
                    autoTrackSessions = true
                    addOnError(bugsnagErrorHandler.get())
                    addMetadata("App", "buildFlavor", BuildConfigWrap.FLAVOR)
                    log(TAG) { "Bugsnag setup done!" }
                } else {
                    autoTrackSessions = false
                    addOnError(nopBugsnagErrorHandler.get())
                    log(TAG) { "Installing Bugsnag NOP error handler due to user opt-out!" }
                }
            }

            Bugsnag.start(context, bugsnagConfig)
            Bugs.reporter = this
        } catch (e: IllegalStateException) {
            log(TAG) { "Bugsnag API Key not configured." }
        }
    }

    override fun notify(throwable: Throwable) {
        Bugsnag.notify(throwable)
    }

    companion object {
        private val TAG = logTag("Debug", "AutoReport")
    }

}