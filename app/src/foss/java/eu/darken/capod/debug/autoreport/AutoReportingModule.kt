package eu.darken.capod.debug.autoreport

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.common.debug.autoreport.AutomaticBugReporter
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class AutoReportingModule {
    @Binds
    @Singleton
    abstract fun autoreporting(foss: FossAutoReporting): AutomaticBugReporter
}