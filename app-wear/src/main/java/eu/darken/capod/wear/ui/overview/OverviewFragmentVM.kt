package eu.darken.capod.wear.ui.overview

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.navigation.navVia
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.wear.ui.overview.cards.*
import eu.darken.capod.wear.ui.overview.cards.pods.DualPodsCardVH
import eu.darken.capod.wear.ui.overview.cards.pods.SinglePodsCardVH
import eu.darken.capod.wear.ui.overview.cards.pods.UnknownPodDeviceCardVH
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
    private val generalSettings: GeneralSettings,
    debugSettings: DebugSettings,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val launchUpgradeFlow = SingleLiveEvent<(Activity) -> Unit>()

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(3000)
        }
    }

    val requestPermissionEvent = SingleLiveEvent<Permission>()

    private val pods: Flow<List<PodDevice>> = permissionTool.missingPermissions
        .flatMapLatest { permissions ->
            if (permissions.isNotEmpty()) {
                return@flatMapLatest flowOf(emptyList())
            }

            generalSettings.showAll.flow.flatMapLatest { showAll ->
                if (showAll) {
                    podMonitor.devices
                } else {
                    podMonitor.mainDevice.map { mainDevice ->
                        mainDevice?.let { listOf(it) } ?: emptyList()
                    }
                }
            }
        }
        .catch { errorEvents.postValue(it) }

    val listItems: LiveData<List<OverviewAdapter.Item>> = combine(
        updateTicker,
        permissionTool.missingPermissions,
        pods,
        debugSettings.isDebugModeEnabled.flow,
        generalSettings.showAll.flow,
        bluetoothManager.isBluetoothEnabled,
        podMonitor.mainDevice,
    ) { _, permissions, pods, isDebugMode, showAll, isBluetoothEnabled, mainPod ->
        val items = mutableListOf<OverviewAdapter.Item>()

        permissions
            .map { perm ->
                PermissionCardVH.Item(
                    permission = perm,
                    onRequest = { requestPermissionEvent.postValue(it) },
                )
            }
            .run { items.addAll(this) }

        if (permissions.isEmpty()) {
            if (!isBluetoothEnabled) {
                items.add(0, BluetoothDisabledVH.Item)
            } else if (mainPod == null) {
                items.add(0, MissingMainDeviceVH.Item)
            }
        }

        if (permissions.isEmpty() && isBluetoothEnabled) {
            pods.map {
                val now = Instant.now()
                when (it) {
                    is DualPodDevice -> DualPodsCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode,
                        isMainPod = it == mainPod,
                    )
                    is SinglePodDevice -> SinglePodsCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode,
                        isMainPod = it == mainPod,
                    )
                    else -> UnknownPodDeviceCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode,
                        isMainPod = it == mainPod,
                    )
                }
            }.run { items.addAll(this) }
        }

        items.add(0, AppTitleVH.Item())
        SettingsVH.Item(
            onClick = {
                OverviewFragmentDirections.actionOverviewFragmentToSettingsFragment().navVia(this@OverviewFragmentVM)
            }
        ).run { items.add(this) }

        items
    }
        .catch { errorEvents.postValue(it) }
        .asLiveData2()

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionTool.recheck()
    }
}