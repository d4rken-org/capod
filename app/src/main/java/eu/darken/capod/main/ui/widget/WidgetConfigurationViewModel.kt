package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.combine
import eu.darken.capod.common.uix.ViewModel2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class WidgetConfigurationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val widgetSettings: WidgetSettings,
    private val upgradeRepo: UpgradeRepo,
    @ApplicationContext private val context: Context,
) : ViewModel2(dispatcherProvider) {

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(context) }

    val widgetId: Int
        get() = savedStateHandle.get<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    private val isAncWidget: Boolean = run {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@run false
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return@run false
        val ancProvider = ComponentName(context, AncWidgetProvider::class.java)
        info.provider == ancProvider
    }

    init {
        log(TAG) { "ViewModel init(widgetId=$widgetId, isAncWidget=$isAncWidget)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID" }
        } else {
            widgetSettings.migrateLegacyConfigIfNeeded(widgetId, appWidgetManager.getAppWidgetOptions(widgetId))
        }
    }

    private val initialConfig: WidgetConfig = run {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@run WidgetConfig()
        widgetSettings.getWidgetConfig(widgetId)
    }

    private val selectedProfile = MutableStateFlow(initialConfig.profileId)

    private val forceCustomMode = MutableStateFlow(WidgetTheme.matchPreset(initialConfig.theme) == null)

    private val currentTheme = MutableStateFlow(initialConfig.theme)

    private val visibleProfiles = deviceProfilesRepo.profiles.map { profiles ->
        if (isAncWidget) profiles.filter { it.model.features.hasAncControl } else profiles
    }

    val state = combine(
        selectedProfile,
        currentTheme,
        forceCustomMode,
        visibleProfiles,
        upgradeRepo.upgradeInfo,
    ) { selected, theme, forceCustom, profiles, upgradeInfo ->
        log(TAG) { "state update: profile=$selected, theme=$theme, forceCustom=$forceCustom, profiles=${profiles.size}" }

        val activePreset = if (forceCustom) null else WidgetTheme.matchPreset(theme)
        State(
            profiles = profiles,
            isPro = upgradeInfo.isPro,
            selectedProfile = selected,
            theme = theme,
            activePreset = activePreset,
            isCustomMode = activePreset == null,
            isAncWidget = isAncWidget,
        )
    }.asLiveState()

    data class State(
        val profiles: List<DeviceProfile> = emptyList(),
        val selectedProfile: ProfileId? = null,
        val isPro: Boolean = false,
        val theme: WidgetTheme = WidgetTheme.DEFAULT,
        val activePreset: WidgetTheme.Preset? = WidgetTheme.Preset.MATERIAL_YOU,
        val isCustomMode: Boolean = false,
        val isAncWidget: Boolean = false,
    ) {
        val canConfirm: Boolean = selectedProfile != null && profiles.any { it.id == selectedProfile }
    }

    fun selectProfile(profileId: ProfileId) {
        log(TAG, INFO) { "selectProfile(profileId=$profileId)" }
        selectedProfile.value = profileId
    }

    fun selectPreset(preset: WidgetTheme.Preset) {
        log(TAG, INFO) { "selectPreset(preset=$preset)" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme(
            backgroundColor = preset.presetBg,
            foregroundColor = preset.presetFg,
            backgroundAlpha = currentTheme.value.backgroundAlpha,
            showDeviceLabel = currentTheme.value.showDeviceLabel,
        )
    }

    fun enterCustomMode(resolvedBg: Int, resolvedFg: Int) {
        log(TAG, INFO) { "enterCustomMode(resolvedBg=${String.format("#%06X", 0xFFFFFF and resolvedBg)}, resolvedFg=${String.format("#%06X", 0xFFFFFF and resolvedFg)})" }
        forceCustomMode.value = true
        // Populate null colors with the currently displayed values so the user has a starting point
        val theme = currentTheme.value
        currentTheme.value = theme.copy(
            backgroundColor = theme.backgroundColor ?: (resolvedBg or 0xFF000000.toInt()),
            foregroundColor = theme.foregroundColor ?: (resolvedFg or 0xFF000000.toInt()),
        )
    }

    fun setBackgroundColor(color: Int) {
        log(TAG, INFO) { "setBackgroundColor(color=${String.format("#%06X", 0xFFFFFF and color)})" }
        currentTheme.value = currentTheme.value.copy(backgroundColor = color or 0xFF000000.toInt())
    }

    fun setForegroundColor(color: Int) {
        log(TAG, INFO) { "setForegroundColor(color=${String.format("#%06X", 0xFFFFFF and color)})" }
        currentTheme.value = currentTheme.value.copy(foregroundColor = color or 0xFF000000.toInt())
    }

    fun setBackgroundAlpha(alpha: Int) {
        log(TAG, INFO) { "setBackgroundAlpha(alpha=$alpha)" }
        currentTheme.value = currentTheme.value.copy(backgroundAlpha = alpha.coerceIn(0, 255))
    }

    fun setShowDeviceLabel(show: Boolean) {
        log(TAG, INFO) { "setShowDeviceLabel(show=$show)" }
        currentTheme.value = currentTheme.value.copy(showDeviceLabel = show)
    }

    fun resetToDefaults() {
        log(TAG, INFO) { "resetToDefaults()" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme.DEFAULT
    }

    fun confirmSelection() {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG, INFO) { "confirmSelection: invalid widget ID, skipping save" }
            return
        }
        val config = WidgetConfig(
            profileId = selectedProfile.value,
            theme = currentTheme.value,
        )
        log(TAG, INFO) { "confirmSelection(widgetId=$widgetId, config=$config)" }
        widgetSettings.saveWidgetConfig(widgetId, config)
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationVM")
    }
}
