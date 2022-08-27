package eu.darken.capod.monitor.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineScope

@InstallIn(MonitorComponent::class)
@Module()
abstract class ProcessorModule {

    @Binds
    @MonitorScope
    abstract fun processorScope(scope: MonitorCoroutineScope): CoroutineScope

}
