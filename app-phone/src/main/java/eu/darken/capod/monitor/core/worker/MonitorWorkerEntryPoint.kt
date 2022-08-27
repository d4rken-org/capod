package eu.darken.capod.monitor.core.worker

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import eu.darken.capod.monitor.core.MonitorComponent

@InstallIn(MonitorComponent::class)
@EntryPoint
interface MonitorWorkerEntryPoint