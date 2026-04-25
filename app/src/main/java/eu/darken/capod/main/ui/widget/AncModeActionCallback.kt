package eu.darken.capod.main.ui.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Keep
class AncModeActionCallback : ActionCallback {

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AncModeCallbackEntryPoint {
        fun aapConnectionManager(): AapConnectionManager
        fun widgetSettings(): WidgetSettings
        fun deviceMonitor(): DeviceMonitor
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val modeName = parameters[ANC_MODE_KEY] ?: run {
            log(TAG, ERROR) { "onAction: missing ANC_MODE_KEY" }
            return
        }
        val widgetId = parameters[WIDGET_ID_KEY] ?: run {
            log(TAG, ERROR) { "onAction: missing WIDGET_ID_KEY" }
            return
        }

        log(TAG, VERBOSE) { "onAction(mode=$modeName, widgetId=$widgetId)" }

        val mode = try {
            enumValueOf<AapSetting.AncMode.Value>(modeName)
        } catch (e: IllegalArgumentException) {
            log(TAG, ERROR) { "onAction: invalid mode name: $modeName" }
            return
        }

        val ep = EntryPointAccessors.fromApplication(context, AncModeCallbackEntryPoint::class.java)

        val profileId = ep.widgetSettings().getWidgetConfig(widgetId).profileId
        if (profileId == null) {
            log(TAG, ERROR) { "onAction: no profile for widgetId=$widgetId" }
            return
        }

        val device = ep.deviceMonitor().getDeviceForProfile(profileId)
        if (device == null) {
            log(TAG, ERROR) { "onAction: no device for profileId=$profileId" }
            AncGlanceWidget().updateAll(context)
            return
        }

        val address = device.address
        if (address == null) {
            log(TAG, ERROR) { "onAction: device has no address" }
            AncGlanceWidget().updateAll(context)
            return
        }

        try {
            ep.aapConnectionManager().sendCommand(address, AapCommand.SetAncMode(mode))
            log(TAG, VERBOSE) { "onAction: sent SetAncMode($mode) to $address" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "onAction: sendCommand failed: ${e.asLog()}" }
        }

        AncGlanceWidget().updateAll(context)
    }

    companion object {
        val ANC_MODE_KEY = ActionParameters.Key<String>("anc_mode")
        val WIDGET_ID_KEY = ActionParameters.Key<Int>("widget_id")
        private val TAG = logTag("Widget", "ANC", "Callback")
    }
}
