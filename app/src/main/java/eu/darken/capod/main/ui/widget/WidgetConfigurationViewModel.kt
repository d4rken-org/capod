package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class WidgetConfigurationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val widgetSettings: WidgetSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    val widgetId: Int
        get() = savedStateHandle.get<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    init {
        log(TAG) { "ViewModel init(widgetId=$widgetId)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID" }
        }
    }

    private val selectedProfile = MutableStateFlow(widgetSettings.getWidgetProfile(widgetId))

    val state = combine(
        selectedProfile,
        deviceProfilesRepo.profiles,
        upgradeRepo.upgradeInfo,
    ) { selected, profiles, upgradeInfo ->
        log(TAG) { "loadProfiles()" }


        State(
            profiles = profiles,
            isPro = upgradeInfo.isPro,
            selectedProfile = selected,
        )
    }.asLiveData2()

    data class State(
        val profiles: List<DeviceProfile> = emptyList(),
        val selectedProfile: ProfileId? = null,
        val isPro: Boolean = false,
    ) {
        val canConfirm: Boolean = selectedProfile != null
    }

    fun selectProfile(profileId: ProfileId) {
        log(TAG) { "selectProfile(profileId=$profileId)" }
        selectedProfile.value =  profileId
    }

    fun confirmSelection() {
        val selectedProfile = selectedProfile.value
        if (selectedProfile != null) {
            log(TAG) { "confirmSelection(widgetId=$widgetId, selectedProfile=$selectedProfile)" }
            widgetSettings.saveWidgetProfile(widgetId, selectedProfile)
        }
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationVM")
    }
}