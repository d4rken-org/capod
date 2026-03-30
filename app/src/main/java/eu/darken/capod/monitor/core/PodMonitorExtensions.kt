package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun BlePodMonitor.devicesWithProfiles(): Flow<List<BlePodSnapshot>> = devices
    .map { devices -> devices.filter { it.meta.profile != null } }

fun BlePodMonitor.primaryDevice(): Flow<BlePodSnapshot?> = devicesWithProfiles().map { it.firstOrNull() }