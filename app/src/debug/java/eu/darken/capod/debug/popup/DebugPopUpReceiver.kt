package eu.darken.capod.debug.popup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.reaction.ui.popup.PopUpWindow
import javax.inject.Inject

@AndroidEntryPoint
class DebugPopUpReceiver : BroadcastReceiver() {

    @Inject lateinit var popUpWindow: PopUpWindow

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive(${intent.action})" }
        when (intent.action) {
            ACTION_SHOW -> popUpWindow.show(MockPodDataProvider.dualPodMonitoredMixed())
            ACTION_HIDE -> popUpWindow.close()
            ACTION_RESHOW -> {
                popUpWindow.close()
                popUpWindow.show(MockPodDataProvider.dualPodMonitoredMixed())
            }

            ACTION_FLASH -> {
                popUpWindow.show(MockPodDataProvider.dualPodMonitoredMixed())
                popUpWindow.close()
            }

            else -> {
                log(TAG, WARN) { "Unknown action: ${intent.action}" }
                return
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "eu.darken.capod.DEBUG_POPUP_SHOW"
        const val ACTION_HIDE = "eu.darken.capod.DEBUG_POPUP_HIDE"
        const val ACTION_RESHOW = "eu.darken.capod.DEBUG_POPUP_RESHOW"
        const val ACTION_FLASH = "eu.darken.capod.DEBUG_POPUP_FLASH"
        private val TAG = logTag("Debug", "PopUp", "Receiver")
    }
}
