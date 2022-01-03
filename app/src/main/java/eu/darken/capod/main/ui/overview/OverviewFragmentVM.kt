package eu.darken.capod.main.ui.overview

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.navigation.navVia
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isGrantedOrNotRequired
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.ui.overview.cards.PermissionCardVH
import eu.darken.capod.main.ui.overview.cards.pods.DualApplePodsCardVH
import eu.darken.capod.main.ui.overview.cards.pods.UnknownPodDeviceCardVH
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.airpods.DualApplePods
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val enabledState: Flow<Boolean> = flow {
        emit(false)
    }

    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())
    private val requiredPermissions: Flow<List<Permission>> = permissionCheckTrigger
        .map {
            Permission.values().filter { !it.isGrantedOrNotRequired(context) }
        }
        .onEach {
            if (it.isEmpty()) {
                monitorControl.startMonitor()
            }
        }

    val requestPermissionevent = SingleLiveEvent<Permission>()

    private val pods: Flow<List<PodDevice>> = requiredPermissions
        .flatMapLatest {
            if (it.isEmpty()) {
                podMonitor.pods
            } else {
                channelFlow {
                    send(emptyList())
                    awaitClose()
                }
            }
        }

    val listItems: LiveData<List<OverviewAdapter.Item>> = combine(
        enabledState,
        requiredPermissions,
        pods
    ) { state, permissions, pods ->
        val items = mutableListOf<OverviewAdapter.Item>()

        permissions
            .map {
                PermissionCardVH.Item(
                    permission = it,
                    onRequest = { requestPermissionevent.postValue(it) }
                )
            }
            .forEach { items.add(it) }

        pods
            .map {
                when (it) {
                    is DualApplePods -> DualApplePodsCardVH.Item(it)
                    else -> UnknownPodDeviceCardVH.Item(it)
                }
            }
            .run { items.addAll(this) }

        items
    }.asLiveData2()

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