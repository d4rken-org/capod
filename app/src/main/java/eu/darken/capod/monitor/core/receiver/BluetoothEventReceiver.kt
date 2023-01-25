package eu.darken.capod.monitor.core.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.hasFeature
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothEventReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorControl: MonitorControl
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context, $intent)" }
        if (!EXPECTED_ACTIONS.contains(intent.action)) {
            log(TAG, WARN) { "Unknown action: ${intent.action}" }
            return
        }

        val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (bluetoothDevice == null) {
            log(TAG, WARN) { "Event without Bluetooth device association." }
            return
        } else {
            log { "Event related to $bluetoothDevice" }
        }
        val supportedFeatures = ContinuityProtocol.BLE_FEATURE_UUIDS.filter { bluetoothDevice.hasFeature(it) }

        if (supportedFeatures.isEmpty()) {
            log(TAG) { "Device has no features we support." }
            return
        } else {
            log { "Device has the following we features we support $supportedFeatures" }
        }

        val pending = goAsync()
        appScope.launch {
            log(TAG) { "Starting monitor" }
            monitorControl.startMonitor(bluetoothDevice, forceStart = false)
            pending.finish()
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "EventReceiver")
        private val EXPECTED_ACTIONS = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED
        )
    }
}
