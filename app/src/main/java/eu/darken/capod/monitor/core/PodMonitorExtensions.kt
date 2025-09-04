package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.PodDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.collections.firstOrNull

fun PodMonitor.devicesWithProfiles(): Flow<List<PodDevice>> = devices
    .map { devices -> devices.filter { it.profile != null } }

fun PodMonitor.primaryDevice(): Flow<PodDevice?> = devicesWithProfiles().map { it.firstOrNull() }