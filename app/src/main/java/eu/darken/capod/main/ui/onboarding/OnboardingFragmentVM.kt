package eu.darken.capod.main.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class OnboardingFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun finishOnboarding() = launch {
        generalSettings.isOnboardingDone.value = true
        OnboardingFragmentDirections.actionOnboardingFragmentToOverviewFragment().navigate()
    }

    companion object {
        val TAG = logTag("Onboarding", "Fragment", "VM")
    }
}