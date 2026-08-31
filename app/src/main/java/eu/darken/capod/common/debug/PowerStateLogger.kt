package eu.darken.capod.common.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A BLE reception blackout can just as well be the display going off as the scan being wrong, and a
 * capture currently carries no evidence either way. Only active while a debug recording runs.
 */
@Singleton
class PowerStateLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
) {

    fun setup() {
        Bugs.isDebug
            .flatMapLatest { isRecording ->
                if (isRecording) {
                    // The catch belongs to the episode, not to the flag: Flow.catch completes the
                    // flow it is applied to, so catching after flatMapLatest would end the isDebug
                    // collection on the first failure and every later recording in this process
                    // would carry no power state at all.
                    powerStateEvents().catch { log(TAG, ERROR) { "Power state logging failed: ${it.asLog()}" } }
                } else {
                    emptyFlow()
                }
            }
            .launchIn(appScope)
    }

    private fun powerStateEvents(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Android callback, outside any flow: a throw here takes the process with it.
                try {
                    logPowerState(intent.action ?: "unknown")
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to log power state: ${e.asLog()}" }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Registered before the first snapshot is taken: ACTION_SCREEN_OFF is not sticky, so a
        // screen-off in between would show up as neither a transition nor a corrected state.
        context.registerReceiver(receiver, filter)

        logPowerState("recording started")

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to unregister receiver: ${e.asLog()}" }
            }
        }
    }

    private fun logPowerState(trigger: String) {
        val powerManager = try {
            context.getSystemService(PowerManager::class.java)
        } catch (e: Exception) {
            null
        }
        val interactive = powerManager.read { isInteractive }
        val deviceIdle = powerManager.read { isDeviceIdleMode }
        val powerSave = powerManager.read { isPowerSaveMode }
        val ignoringBatteryOptimizations = powerManager.read { isIgnoringBatteryOptimizations(context.packageName) }

        log(TAG, INFO) {
            "Power state ($trigger): interactive=$interactive, deviceIdle=$deviceIdle, " +
                "powerSave=$powerSave, ignoringBatteryOptimizations=$ignoringBatteryOptimizations"
        }
    }

    // Per field, so an OEM PowerManager that throws costs one value instead of the whole line.
    private fun PowerManager?.read(value: PowerManager.() -> Any?): String {
        val powerManager = this ?: return UNAVAILABLE
        return try {
            powerManager.value()?.toString() ?: UNAVAILABLE
        } catch (e: Exception) {
            UNAVAILABLE
        }
    }

    companion object {
        private const val UNAVAILABLE = "unavailable"
        private val TAG = logTag("Debug", "PowerStateLogger")
    }
}
