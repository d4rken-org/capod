package eu.darken.cap.common.debug.bugreporting

import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.cap.common.InstallId
import eu.darken.cap.common.debug.bugsnag.BugsnagErrorHandler
import eu.darken.cap.common.debug.bugsnag.BugsnagLogger
import eu.darken.cap.common.debug.bugsnag.NOPBugsnagErrorHandler
import eu.darken.cap.common.debug.logging.Logging
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class BugReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bugReportSettings: BugReportSettings,
    private val installId: InstallId,
    private val bugsnagLogger: Provider<BugsnagLogger>,
    private val bugsnagErrorHandler: Provider<BugsnagErrorHandler>,
    private val nopBugsnagErrorHandler: Provider<NOPBugsnagErrorHandler>,
) {

    fun setup() {
        val isEnabled = bugReportSettings.isEnabled.value
        log(TAG) { "setup(): isEnabled=$isEnabled" }

        try {
            val bugsnagConfig = Configuration.load(context).apply {
                if (bugReportSettings.isEnabled.value) {
                    Logging.install(bugsnagLogger.get())
                    setUser(installId.id, null, null)
                    autoTrackSessions = true
                    addOnError(bugsnagErrorHandler.get())
                    log(TAG) { "Bugsnag setup done!" }
                } else {
                    autoTrackSessions = false
                    addOnError(nopBugsnagErrorHandler.get())
                    log(TAG) { "Installing Bugsnag NOP error handler due to user opt-out!" }
                }
            }

            Bugsnag.start(context, bugsnagConfig)
            Bugs.ready = true
        } catch (e: IllegalStateException) {
            log(TAG) { "Bugsnag API Key not configured." }
        }
    }

    companion object {
        private val TAG = logTag("BugReporter")
    }
}