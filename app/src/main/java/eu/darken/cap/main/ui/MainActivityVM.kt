package eu.darken.cap.main.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.cap.common.coroutine.DispatcherProvider
import eu.darken.cap.common.viewmodel.SmartVM
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : SmartVM(dispatcherProvider = dispatcherProvider) {

}