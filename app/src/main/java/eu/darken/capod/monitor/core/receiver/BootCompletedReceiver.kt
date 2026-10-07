package eu.darken.capod.monitor.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.toReactionConfig
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var bluetoothManager: BluetoothManager2
    @Inject lateinit var profilesRepo: DeviceProfilesRepo
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(8_000) {
                    startMonitorIfNeeded()
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Boot connection check failed: $e" }
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun startMonitorIfNeeded() {
        val profiles = profilesRepo.profiles.first()
        // Keep existing boot monitoring for profiles that have not opted into the new path.
        if (profiles.any { !it.toReactionConfig().audioConnectOnAcl || it.toReactionConfig().autoConnect }) {
            monitorControl.startMonitor(forceStart = false)
            return
        }
        val addresses = profiles.mapNotNull { it.address }.toSet()
        if (addresses.isNotEmpty() && bluetoothManager.connectedDevices.first().any { it.address in addresses }) {
            log(TAG) { "Boot completed with connected Pods, starting monitor." }
            monitorControl.startMonitor(forceStart = false)
        } else {
            log(TAG) { "Boot completed without connected Pods, waiting for Bluetooth events." }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "BootReceiver")
    }
}
