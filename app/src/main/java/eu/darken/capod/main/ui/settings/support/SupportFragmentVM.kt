package eu.darken.capod.main.ui.settings.support

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.InstallId
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.livedata.SingleLiveEvent
import eu.darken.capod.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class SupportFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val installId: InstallId,
) : ViewModel3(dispatcherProvider) {

    val clipboardEvent = SingleLiveEvent<String>()

    fun copyInstallID() = launch {
        clipboardEvent.postValue(installId.id)
    }
}