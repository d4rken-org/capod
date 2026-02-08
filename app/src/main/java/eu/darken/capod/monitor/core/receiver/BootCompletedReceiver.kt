package eu.darken.capod.monitor.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.worker.MonitorControl
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorControl: MonitorControl

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        log(TAG) { "Boot completed, starting monitor." }
        monitorControl.startMonitor(forceStart = false)
    }

    companion object {
        private val TAG = logTag("Monitor", "BootReceiver")
    }
}
