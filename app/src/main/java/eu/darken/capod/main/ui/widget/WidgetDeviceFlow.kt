package eu.darken.capod.main.ui.widget

import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

internal fun DeviceMonitor.widgetDeviceFlow(profileId: String): Flow<PodDevice?> = devices
    .map { devices -> devices.firstOrNull { it.profileId == profileId } }
    .distinctUntilChangedBy { it?.toWidgetKey() }
