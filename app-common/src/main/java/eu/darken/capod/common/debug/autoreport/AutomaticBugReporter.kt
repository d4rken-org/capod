package eu.darken.capod.common.debug.autoreport

import android.app.Application

interface AutomaticBugReporter {

    fun setup(application: Application)

    fun notify(throwable: Throwable)
}