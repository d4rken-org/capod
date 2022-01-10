package eu.darken.capod.main.ui.overview

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.navigation.navVia
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.main.ui.overview.cards.NoPairedDeviceCardVH
import eu.darken.capod.main.ui.overview.cards.PermissionCardVH
import eu.darken.capod.main.ui.overview.cards.pods.*
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.BasicSingleApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.SingleApplePods
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import java.time.Instant
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OverviewFragmentVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val monitorControl: MonitorControl,
    private val podMonitor: PodMonitor,
    private val permissionTool: PermissionTool,
    private val generalSettings: GeneralSettings,
    debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(1000)
        }
    }

    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())
    private val requiredPermissions: Flow<Collection<Permission>> = permissionCheckTrigger
        .map { permissionTool.missingPermissions() }
        .onEach {
            log(TAG) { "Missing permissions: $it" }
            if (it.isEmpty() && generalSettings.monitorMode.value != MonitorMode.MANUAL) {
                log(TAG) { "All permissions granted, starting monitor." }
                monitorControl.startMonitor()
            }
        }

    val requestPermissionEvent = SingleLiveEvent<Permission>()

    private val pods: Flow<List<PodDevice>> = requiredPermissions
        .flatMapLatest { permissions ->
            if (permissions.isEmpty()) {
                generalSettings.showAll.flow
                    .flatMapLatest { showAll ->
                        if (showAll) {
                            podMonitor.devices
                        } else {
                            podMonitor.mainDevice.map { mainDevice ->
                                mainDevice?.let { listOf(it) } ?: emptyList()
                            }
                        }
                    }
            } else {
                channelFlow {
                    send(emptyList())
                    awaitClose()
                }
            }
        }
        .catch { errorEvents.postValue(it) }

    val listItems: LiveData<List<OverviewAdapter.Item>> = combine(
        updateTicker,
        requiredPermissions,
        pods,
        debugSettings.isDebugModeEnabled.flow,
        generalSettings.showAll.flow,
    ) { tick, permissions, pods, isDebugMode, showAll ->
        val items = mutableListOf<OverviewAdapter.Item>()

        val now = Instant.now()
        pods
            .map {
                when (it) {
                    is DualApplePods -> DualApplePodsCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode
                    )
                    is SingleApplePods -> SingleApplePodsCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode
                    )
                    is BasicSingleApplePods -> BasicSingleApplePodsCardVH.Item(
                        now = now,
                        device = it,
                        showDebug = isDebugMode
                    )
                    else -> UnknownPodDeviceCardVH.Item(
                        now = now,
                        device = it
                    )
                }
            }
            .run { items.addAll(this) }

        permissions
            .map {
                PermissionCardVH.Item(
                    permission = it,
                    onRequest = { requestPermissionEvent.postValue(it) }
                )
            }
            .run { items.addAll(this) }

        if (!showAll && items.none { it is PodDeviceVH.Item }) {
            NoPairedDeviceCardVH.Item {
                generalSettings.showAll.value = true
            }.run { items.add(this) }
        }

        items
    }
        .catch { errorEvents.postValue(it) }
        .asLiveData2()

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionCheckTrigger.value = UUID.randomUUID()
    }

    fun goToSettings() = launch {
        OverviewFragmentDirections.actionOverviewFragmentToSettingsFragment().navVia(this@OverviewFragmentVM)
    }

}