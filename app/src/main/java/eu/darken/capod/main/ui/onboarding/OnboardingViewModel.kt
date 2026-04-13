package eu.darken.capod.main.ui.onboarding

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.main.core.GeneralSettings
import javax.inject.Inject
import eu.darken.capod.common.datastore.valueBlocking

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    fun openPrivacyPolicy() {
        log(TAG, INFO) { "openPrivacyPolicy()" }
        webpageTool.open(PrivacyPolicy.URL)
    }

    fun finishOnboarding() = launch {
        log(TAG, INFO) { "finishOnboarding()" }
        generalSettings.isOnboardingDone.valueBlocking = true
        navTo(Nav.Main.Overview, popUpTo = Nav.Main.Onboarding, inclusive = true)
    }

    companion object {
        private val TAG = logTag("Onboarding", "VM")
    }
}
