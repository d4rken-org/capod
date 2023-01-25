package eu.darken.capod.common.bluetooth

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BleScanResultReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var scanResultForwarder: BleScanResultForwarder

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }
        if (intent.action != ACTION) {
            log(TAG, WARN) { "Unknown action: ${intent.action}" }
            return
        }
        if (intent.extras == null) {
            log(TAG) { "Extras are null!" }
            return
        }

        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        log(TAG, VERBOSE) { "errorCode=$errorCode" }
        if (errorCode != 0) {
            log(TAG, WARN) { "ScanCallback error code: $errorCode" }
            return
        }

        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        log(TAG, VERBOSE) { "callbackType=$callbackType" }

        val scanResults = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        log(TAG, VERBOSE) { "scanResults=$scanResults" }

        if (scanResults == null) {
            log(TAG) { "Scan results were empty!" }
            return
        }

        val pending = goAsync()
        appScope.launch {
            scanResultForwarder.forward(scanResults)
            pending.finish()
        }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "BleScanner", "Forwarder", "Receiver")
        val ACTION = "eu.darken.capod.bluetooth.DELIVER_SCAN_RESULTS"
    }
}
