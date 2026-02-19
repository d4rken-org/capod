package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
) : ViewModel3(dispatcherProvider) {

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(context) }

    val widgetId: Int
        get() = savedStateHandle.get<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    init {
        log(TAG) { "ViewModel init(widgetId=$widgetId)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID" }
        }
    }

    private val selectedProfile = MutableStateFlow(widgetSettings.getWidgetProfile(widgetId))

    private val initialTheme: WidgetTheme = run {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@run WidgetTheme.DEFAULT
        WidgetTheme.fromBundle(appWidgetManager.getAppWidgetOptions(widgetId))
    }

    private val forceCustomMode = MutableStateFlow(WidgetTheme.matchPreset(initialTheme) == null)

    private val currentTheme = MutableStateFlow(initialTheme)

    val state = eu.darken.capod.common.flow.combine(
        selectedProfile,
        currentTheme,
        forceCustomMode,
        deviceProfilesRepo.profiles,
        upgradeRepo.upgradeInfo,
    ) { selected, theme, forceCustom, profiles, upgradeInfo ->
        log(TAG) { "state update: profile=$selected, theme=$theme, forceCustom=$forceCustom" }

        val activePreset = if (forceCustom) null else WidgetTheme.matchPreset(theme)
        State(
            profiles = profiles,
            isPro = upgradeInfo.isPro,
            selectedProfile = selected,
            theme = theme,
            activePreset = activePreset,
            isCustomMode = activePreset == null,
        )
    }.asLiveData2()

    data class State(
        val profiles: List<DeviceProfile> = emptyList(),
        val selectedProfile: ProfileId? = null,
        val isPro: Boolean = false,
        val theme: WidgetTheme = WidgetTheme.DEFAULT,
        val activePreset: WidgetTheme.Preset? = WidgetTheme.Preset.MATERIAL_YOU,
        val isCustomMode: Boolean = false,
    ) {
        val canConfirm: Boolean = selectedProfile != null
    }

    fun selectProfile(profileId: ProfileId) {
        log(TAG) { "selectProfile(profileId=$profileId)" }
        selectedProfile.value = profileId
    }

    fun selectPreset(preset: WidgetTheme.Preset) {
        log(TAG) { "selectPreset(preset=$preset)" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme(
            backgroundColor = preset.presetBg,
            foregroundColor = preset.presetFg,
            backgroundAlpha = currentTheme.value.backgroundAlpha,
            showDeviceLabel = currentTheme.value.showDeviceLabel,
        )
    }

    fun enterCustomMode(resolvedBg: Int, resolvedFg: Int) {
        log(TAG) { "enterCustomMode(resolvedBg=${String.format("#%06X", 0xFFFFFF and resolvedBg)}, resolvedFg=${String.format("#%06X", 0xFFFFFF and resolvedFg)})" }
        forceCustomMode.value = true
        // Populate null colors with the currently displayed values so the user has a starting point
        val theme = currentTheme.value
        currentTheme.value = theme.copy(
            backgroundColor = theme.backgroundColor ?: (resolvedBg or 0xFF000000.toInt()),
            foregroundColor = theme.foregroundColor ?: (resolvedFg or 0xFF000000.toInt()),
        )
    }

    fun setBackgroundColor(color: Int) {
        log(TAG) { "setBackgroundColor(color=${String.format("#%06X", 0xFFFFFF and color)})" }
        currentTheme.value = currentTheme.value.copy(backgroundColor = color or 0xFF000000.toInt())
    }

    fun setForegroundColor(color: Int) {
        log(TAG) { "setForegroundColor(color=${String.format("#%06X", 0xFFFFFF and color)})" }
        currentTheme.value = currentTheme.value.copy(foregroundColor = color or 0xFF000000.toInt())
    }

    fun setBackgroundAlpha(alpha: Int) {
        log(TAG) { "setBackgroundAlpha(alpha=$alpha)" }
        currentTheme.value = currentTheme.value.copy(backgroundAlpha = alpha.coerceIn(0, 255))
    }

    fun toggleDeviceLabel() {
        val newValue = !currentTheme.value.showDeviceLabel
        log(TAG) { "toggleDeviceLabel(showDeviceLabel=$newValue)" }
        currentTheme.value = currentTheme.value.copy(showDeviceLabel = newValue)
    }

    fun setShowDeviceLabel(show: Boolean) {
        log(TAG) { "setShowDeviceLabel(show=$show)" }
        currentTheme.value = currentTheme.value.copy(showDeviceLabel = show)
    }

    fun resetToDefaults() {
        log(TAG) { "resetToDefaults()" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme.DEFAULT
    }

    fun confirmSelection() {
        val selectedProfile = selectedProfile.value
        if (selectedProfile != null) {
            log(TAG) { "confirmSelection(widgetId=$widgetId, selectedProfile=$selectedProfile)" }
            widgetSettings.saveWidgetProfile(widgetId, selectedProfile)
        }

        // Save theme to AppWidgetOptions bundle
        val theme = currentTheme.value
        log(TAG) { "confirmSelection: saving theme=$theme" }
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        theme.toBundle(options)
        appWidgetManager.updateAppWidgetOptions(widgetId, options)
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationVM")
    }
}
