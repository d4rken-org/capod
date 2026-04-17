package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.core.themeState
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : Activity2() {

    private val vm: WidgetConfigurationViewModel by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @ApplicationContext @Inject lateinit var appContext: Context

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        log(TAG) { "onCreate(widgetId=$widgetId)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID, finishing" }
            finish()
            return
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsStateWithLifecycle(initialValue = generalSettings.currentThemeState)
            CapodTheme(state = themeState) {
                val backgroundColor = MaterialTheme.colorScheme.background
                val useDarkIcons = backgroundColor.luminance() > 0.5f
                SideEffect {
                    window.decorView.setBackgroundColor(backgroundColor.toArgb())
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = useDarkIcons
                    insetsController.isAppearanceLightNavigationBars = useDarkIcons
                }

                val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
                state?.let { currentState ->
                    WidgetConfigurationScreen(
                        state = currentState,
                        showAapRequiredHint = currentState.isAncWidget,
                        onSelectProfile = { profile -> vm.selectProfile(profile.id) },
                        onSelectPreset = { preset -> vm.selectPreset(preset) },
                        onEnterCustomMode = { bg, fg -> vm.enterCustomMode(bg, fg) },
                        onSetBackgroundColor = { color -> vm.setBackgroundColor(color) },
                        onSetForegroundColor = { color -> vm.setForegroundColor(color) },
                        onSetBackgroundAlpha = { alpha -> vm.setBackgroundAlpha(alpha) },
                        onSetShowDeviceLabel = { show -> vm.setShowDeviceLabel(show) },
                        onReset = { vm.resetToDefaults() },
                        onConfirm = {
                            if (currentState.isPro) {
                                confirmSelection(currentState.isAncWidget)
                            } else {
                                startActivity(
                                    Intent(this@WidgetConfigurationActivity, MainActivity::class.java).apply {
                                        putExtra(MainActivity.EXTRA_NAVIGATE_TO_UPGRADE, true)
                                    }
                                )
                            }
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun confirmSelection(isAncWidget: Boolean) {
        vm.confirmSelection()

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, resultValue)

        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(appContext)
            val glanceId = manager.getGlanceIdBy(widgetId)
            if (isAncWidget) {
                AncGlanceWidget().update(appContext, glanceId)
            } else {
                BatteryGlanceWidget().update(appContext, glanceId)
            }
            finish()
        }
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationActivity")
    }
}
