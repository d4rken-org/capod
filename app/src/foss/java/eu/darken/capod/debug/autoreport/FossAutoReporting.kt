package eu.darken.capod.debug.autoreport

import android.app.Application
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossAutoReporting @Inject constructor() : AutomaticBugReporter {
    override fun setup(application: Application) {
        // NOOP
    }

    override fun notify(throwable: Throwable) {
        throw IllegalStateException("Who initliazed this? Without setup no calls to here!")
    }
}