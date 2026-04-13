package eu.darken.capod.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class TimeModule {

    @Binds
    abstract fun timeSource(defaultTimeSource: DefaultTimeSource): TimeSource
}
