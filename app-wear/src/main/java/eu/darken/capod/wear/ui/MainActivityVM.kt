package eu.darken.capod.wear.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.uix.ViewModel2
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel2(dispatcherProvider = dispatcherProvider)