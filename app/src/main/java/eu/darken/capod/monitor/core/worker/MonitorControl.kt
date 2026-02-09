package eu.darken.capod.monitor.core.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.startServiceCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorControl @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun startMonitor(
        forceStart: Boolean = false,
    ) {
        log(TAG, VERBOSE) { "startMonitor(forceStart=$forceStart)" }

        val hasBluetoothPermission =
            Permission.BLUETOOTH.isGranted(context) || Permission.BLUETOOTH_CONNECT.isGranted(context)
        if (!hasBluetoothPermission) {
            log(TAG, WARN) { "Missing Bluetooth permission, not starting monitor service." }
            return
        }

        try {
            context.startServiceCompat(MonitorService.intent(context, forceStart))
            log(TAG) { "Monitor start request sent." }
        } catch (e: IllegalStateException) {
            log(TAG, WARN) { "Failed to start monitor service: ${e.message}" }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Failed to start monitor service, permission issue: ${e.message}" }
        }
    }

    fun stopMonitor() {
        log(TAG, VERBOSE) { "stopMonitor()" }
        context.stopService(MonitorService.intent(context))
        log(TAG) { "Monitor stop request sent." }
    }

    companion object {
        private val TAG = logTag("Monitor", "Control")
    }
}
