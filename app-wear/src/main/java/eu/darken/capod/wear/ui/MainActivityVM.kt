package eu.darken.capod.wear.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel2
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.PodDevice
import kotlinx.coroutines.flow.combine
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val podMonitor: PodMonitor,
    private val permissionTool: PermissionTool,
) : ViewModel2(dispatcherProvider = dispatcherProvider) {

    sealed class State {
        data class PermissionRequired(
            val permissions: List<Permission>,
        ) : State()

        data class Devices(
            val devices: List<PodDevice>
        ) : State()
    }

    val state = combine(
        podMonitor.devices,
        permissionTool.missingPermissions,
    ) { devices, missingPermissions ->
        when {
            missingPermissions.isNotEmpty() -> State.PermissionRequired(
                permissions = missingPermissions.toList()
            )
            else -> State.Devices(
                devices = devices
            )
        }
    }
}