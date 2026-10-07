package eu.darken.capod.monitor.core.receiver

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.hasFeature
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class BluetoothEventReceiver : BroadcastReceiver() {

    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var bluetoothManager: BluetoothManager2
    @Inject lateinit var aapManager: AapConnectionManager
    @Inject lateinit var profilesRepo: DeviceProfilesRepo
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context, $intent)" }
        if (!EXPECTED_ACTIONS.contains(intent.action)) {
            log(TAG, WARN) { "Unknown action: ${intent.action}" }
            return
        }

        if (!shouldHandleBluetoothEvent(intent.action, intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))) return

        val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (bluetoothDevice == null) {
            log(TAG, WARN) { "Event without Bluetooth device association." }
            return
        } else {
            log(TAG) { "Event related to $bluetoothDevice" }
        }
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
            && intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.TRANSPORT_AUTO) != BluetoothDevice.TRANSPORT_LE
        ) {
            val pending = goAsync()
            appScope.launch {
                try {
                    withTimeout(8_000) {
                        if (!connectAudioIfEnabled(bluetoothDevice)) handleMonitorEvent(intent, bluetoothDevice)
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "ACL audio connection failed: $e" }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        handleMonitorEvent(intent, bluetoothDevice)
    }

    /** Returns true when this event belongs to the experimental path, even if approval is missing. */
    internal suspend fun connectAudioIfEnabled(device: BluetoothDevice): Boolean {
        val profile = profilesRepo.profiles.first().filterIsInstance<AppleDeviceProfile>()
            .firstOrNull { it.address.equals(device.address, ignoreCase = true) }
        if (profile?.reactionConfig?.autoConnect != true) return false
        bluetoothManager.markDeviceConnected(device.address)
        if (profile.reactionConfig.autoConnectCondition == AutoConnectCondition.IN_EAR) {
            monitorControl.startMonitor(forceStart = false)
            aapManager.connect(device.address, device, profile.model)
        } else if (!hasApiLevel(37) || bluetoothManager.isCompanionAssociated(device.address)) {
            val result = bluetoothManager.connectAudio(device)
            log(TAG) { "Experimental ACL audio connection result=$result" }
        } else {
            log(TAG, WARN) { "Experimental ACL audio connection needs companion association" }
        }
        return true
    }

    private fun handleMonitorEvent(intent: Intent, bluetoothDevice: BluetoothDevice) {
        val supportedFeatures = try {
            ContinuityProtocol.BLE_FEATURE_UUIDS.filter { bluetoothDevice.hasFeature(it) }
        } catch (e: SecurityException) {
            log(TAG, WARN) { "Missing BLUETOOTH_CONNECT, can't check device features: ${e.message}" }
            return
        }

        if (supportedFeatures.isEmpty()) {
            log(TAG) { "Device has no features we support." }
            return
        } else {
            log(TAG) { "Device has the following we features we support $supportedFeatures" }
        }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> bluetoothManager.markDeviceConnected(bluetoothDevice.address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                bluetoothManager.markDeviceDisconnected(bluetoothDevice.address)
                // A disconnect is not a reason to start monitoring.
                return
            }
        }

        log(TAG) { "Starting monitor" }
        monitorControl.startMonitor(forceStart = false)
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

/** Profile teardown and intermediate states must not restart a disconnected monitor. */
internal fun shouldHandleBluetoothEvent(action: String?, profileState: Int): Boolean = when (action) {
    BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> true
    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
        profileState == BluetoothProfile.STATE_CONNECTED
    else -> false
}
