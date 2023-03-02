package eu.darken.capod.wear.ui.overview

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.wear.ui.overview.cards.BluetoothDisabledVH
import eu.darken.capod.wear.ui.overview.cards.MissingMainDeviceVH
import eu.darken.capod.wear.ui.overview.cards.PermissionCardVH
import eu.darken.capod.wear.ui.overview.cards.pods.DualPodsCardVH
import eu.darken.capod.wear.ui.overview.cards.pods.SinglePodsCardVH
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class OverviewFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val podMonitor: PodMonitor,
    private val permissionTool: PermissionTool,
    debugSettings: DebugSettings,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    val requestPermissionEvent = SingleLiveEvent<Permission>()

    private val mainDevice = podMonitor.mainDevice.throttleLatest(1000)

    val listItems: LiveData<List<OverviewAdapter.Item>> = combine(
        updateTicker,
        permissionTool.missingPermissions,
        debugSettings.isDebugModeEnabled.flow,
        bluetoothManager.isBluetoothEnabled,
        mainDevice,
    ) { _, permissions, isDebugMode, isBluetoothEnabled, _ ->
        val items = mutableListOf<OverviewAdapter.Item>()

        if (permissions.isNotEmpty()) {
            permissions
                .map { perm ->
                    PermissionCardVH.Item(
                        permission = perm,
                        onRequest = { requestPermissionEvent.postValue(it) },
                    )
                }
                .run { items.addAll(this) }
            return@combine items
        }

        if (!isBluetoothEnabled) {
            items.add(0, BluetoothDisabledVH.Item)
            return@combine items
        }

        val podToShow = podMonitor.latestMainDevice()
        log(TAG, VERBOSE) { "Showing $podToShow" }

        val now = Instant.now()

        val pod = when (podToShow) {
            is DualPodDevice -> DualPodsCardVH.Item(
                now = now,
                device = podToShow,
                showDebug = isDebugMode,
                isMainPod = true,
            )
            is SinglePodDevice -> SinglePodsCardVH.Item(
                now = now,
                device = podToShow,
                showDebug = isDebugMode,
                isMainPod = true,
            )
            else -> MissingMainDeviceVH.Item
        }
        items.add(pod)

        items
    }
        .catch { errorEvents.postValue(it) }
        .asLiveData2()

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionTool.recheck()
    }
}