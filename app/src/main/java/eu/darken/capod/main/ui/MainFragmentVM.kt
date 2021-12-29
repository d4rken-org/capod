package eu.darken.capod.main.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.recording.core.RecorderModule
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isGranted
import eu.darken.capod.common.smart.Smart2VM
import eu.darken.capod.main.ui.cards.PermissionCardVH
import eu.darken.capod.main.ui.cards.ToggleCardVH
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainFragmentVM @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) : Smart2VM(dispatcherProvider = dispatcherProvider) {


    private val enabledState: Flow<Boolean> = flow {
        emit(false)
    }

    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())
    private val requiredPermissions: Flow<List<Permission>> = permissionCheckTrigger.map {
        Permission.values()
            .filter { hasApiLevel(it.minApiLevel) && !it.isGranted(context) }
    }

    val requestPermissionevent = SingleLiveEvent<Permission>()

    val listItems: LiveData<List<MainAdapter.Item>> = combine(
        enabledState,
        requiredPermissions
    ) { state, permissions ->
        val items = mutableListOf<MainAdapter.Item>()
        ToggleCardVH.Item(
            isEnabled = state,
            onToggle = {

            }
        ).run { items.add(this) }

        permissions
            .map {
                PermissionCardVH.Item(
                    permission = it,
                    onRequest = { requestPermissionevent.postValue(it) }
                )
            }
            .forEach { items.add(it) }

        items
    }.asLiveData2()

    fun onPermissionResult(granted: Boolean) {
        if (granted) permissionCheckTrigger.value = UUID.randomUUID()
    }

    fun toggleDebugLog() = launch {
        recorderModule.startRecorder()
    }

    fun goToSettings() = launch {

    }

}