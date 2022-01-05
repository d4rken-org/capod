package eu.darken.capod.main.ui.overview

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.navigation.navVia
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isRequired
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.main.ui.overview.cards.PermissionCardVH
import eu.darken.capod.main.ui.overview.cards.pods.BasicSingleApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.DualApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.SingleApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.UnknownPodDeviceCardVH
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
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
    private val monitorControl: MonitorControl,
    private val podMonitor: PodMonitor,
    private val generalSettings: GeneralSettings,
    private val debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val updateTicker = channelFlow<Unit> {
        while (isActive) {
            trySend(Unit)
            delay(1000)
        }
    }

    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())
    private val requiredPermissions: Flow<List<Permission>> = permissionCheckTrigger
        .map {
            Permission.values().filter { it.isRequired(context) }
        }
        .onEach {
            log(TAG) { "Missing permissions: $it" }
            if (it.isEmpty() && generalSettings.monitorMode.value != MonitorMode.MANUAL) {
                log(TAG) { "All permissions granted, starting monitor." }
                monitorControl.startMonitor()
            }
        }

    val requestPermissionevent = SingleLiveEvent<Permission>()

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
    ) { tick, permissions, pods, isDebugMode ->
        val items = mutableListOf<OverviewAdapter.Item>()

        permissions
            .map {
                PermissionCardVH.Item(
                    permission = it,
                    onRequest = { requestPermissionevent.postValue(it) }
                )
            }
            .forEach { items.add(it) }

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

        items
    }
        .catch { errorEvents.postValue(it) }
        .asLiveData2()

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionCheckTrigger.value = UUID.randomUUID()
    }

    fun toggleDebugLog() = launch {
        recorderModule.startRecorder()
    }

    fun goToSettings() = launch {
        OverviewFragmentDirections.actionOverviewFragmentToSettingsFragment().navVia(this@OverviewFragmentVM)
    }

}