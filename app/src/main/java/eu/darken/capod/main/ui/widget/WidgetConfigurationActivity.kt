package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.databinding.WidgetConfigurationActivityBinding
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : Activity2() {

    private val vm: WidgetConfigurationViewModel by viewModels()
    private lateinit var ui: WidgetConfigurationActivityBinding

    @Inject lateinit var profileAdapter: WidgetProfileSelectionAdapter
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @ApplicationContext @Inject lateinit var appContext: Context

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)

        // Get widget ID from intent
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        log(TAG) { "onCreate(widgetId=$widgetId)" }

        // If widget ID is invalid, finish
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID, finishing" }
            finish()
            return
        }

        ui = WidgetConfigurationActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        EdgeToEdgeHelper(this).apply {
            insetsPadding(ui.root, top = true, bottom = true, left = true, right = true)
        }

        ui.profilesRecycler.adapter = profileAdapter

        ui.cancelButton.setOnClickListener {
            log(TAG) { "Cancel clicked" }
            finish()
        }

        ui.confirmButton.setOnClickListener {
            if (vm.state.value?.isPro == true) {
                log(TAG) { "Confirm clicked" }
                confirmSelection()
            } else {
                log(TAG) { "Upgrade clicked" }
                upgradeRepo.launchBillingFlow(this@WidgetConfigurationActivity)
            }
        }

        vm.state.observe2 { state ->
            val adapterItems = state.profiles.map { profile ->
                WidgetProfileSelectionVH.Item(
                    profile = profile,
                    isSelected = profile.id == state.selectedProfile,
                    onProfileClick = { vm.selectProfile(it.id) }
                )
            }
            profileAdapter.asyncDiffer.submitUpdate(adapterItems)

            ui.proRequiredCaption.isVisible = !state.isPro

            if (state.isPro) {
                ui.confirmButton.text = getString(android.R.string.ok)
                ui.confirmButton.isEnabled = state.canConfirm
            } else {
                ui.confirmButton.text = getString(R.string.general_upgrade_action)
                ui.confirmButton.isEnabled = true
            }
        }
    }

    private fun confirmSelection() {
        vm.confirmSelection()

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, resultValue)

        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        WidgetProvider.updateWidget(
            context = appContext,
            appWidgetManager = appWidgetManager,
            widgetId = widgetId
        )

        finish()

    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationActivity")
    }
}