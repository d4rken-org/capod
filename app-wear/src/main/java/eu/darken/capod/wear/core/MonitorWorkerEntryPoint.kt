package eu.darken.capod.wear.core

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import eu.darken.capod.monitor.core.MonitorComponent

@InstallIn(MonitorComponent::class)
@EntryPoint
interface MonitorWorkerEntryPoint