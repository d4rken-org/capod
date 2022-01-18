package eu.darken.capod.common.bluetooth

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BluetoothManager2 @Inject constructor(
    private val manager: BluetoothManager,
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    val adapter: BluetoothAdapter
        get() = manager.adapter

    val scanner: BluetoothLeScanner
        get() = adapter.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is disabled or permissiong missing")

    val isBluetoothEnabled: Flow<Boolean> = callbackFlow {
        send(manager.adapter?.isEnabled ?: false)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED != intent.action) {
                    log(TAG) { "Unknown BluetoothAdapter action: $intent" }
                    return
                }

                val value = when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_OFF -> false
                    BluetoothAdapter.STATE_ON -> true
                    else -> false
                }

                trySend(value)
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        awaitClose { context.unregisterReceiver(receiver) }
    }

    suspend fun getBluetoothProfile(
        profile: Int = BluetoothProfile.HEADSET
    ): BluetoothProfile2 = withContext(dispatcherProvider.IO) {
        log(TAG, VERBOSE) { "getBluetoothProfile(profile=$profile)" }

        suspendCancellableCoroutine {
            val connectionState = AtomicBoolean(false)
            manager.adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    log(TAG, VERBOSE) { "onServiceConnected(profile=$profile, proxy=$proxy)" }
                    connectionState.set(true)
                    BluetoothProfile2(
                        profileType = profile,
                        profileProxy = proxy,
                        isConnectedAtomic = connectionState
                    ).run { it.resume(this) }
                }

                override fun onServiceDisconnected(profile: Int) {
                    log(TAG, WARN) { "onServiceDisconnected(profile=$profile" }
                    connectionState.set(false)
                    it.cancel(IOException("BluetoothProfile service disconnected (profile=$profile)"))
                }

            }, profile)
        }
    }

    private fun connectedDevicesForProfile(profile: Int): Flow<Set<BluetoothDevice>> = callbackFlow {
        log(TAG, VERBOSE) { "connectedDevices(profile=$profile) starting" }
        trySend(getBluetoothProfile(profile).connectedDevices)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        val handlerThread = HandlerThread("BluetoothEventReceiver").apply {
            start()
        }
        val handler = Handler(handlerThread.looper)

        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG, VERBOSE) { "Bluetooth event (intent=$intent, extras=${intent.extras})" }
                val action = intent.action
                if (action == null) {
                    log(TAG, ERROR) { "Bluetooth event without action, how did we get this?" }
                    return
                }
                val device = intent.getParcelableExtra<BluetoothDevice?>(BluetoothDevice.EXTRA_DEVICE)
                if (device == null) {
                    log(TAG, ERROR) { "Connection event is missing EXTRA_DEVICE: ${intent.extras}" }
                    return
                }

                this@callbackFlow.launch {
                    val currentDevices = getBluetoothProfile(profile).connectedDevices

                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            log(TAG) { "Adding $device to current devices $currentDevices" }
                            trySend(currentDevices.plus(device))
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            log(TAG) { "Removing $device from current devices $currentDevices" }
                            trySend(currentDevices.minus(device))
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter, null, handler)

        awaitClose {
            log(TAG, VERBOSE) { "connectedDevices(profile=$profile) closed." }
            context.unregisterReceiver(receiver)
        }
    }

    fun connectedDevices(
        featureFilter: Set<ParcelUuid> = ContinuityProtocol.BLE_FEATURE_UUIDS
    ): Flow<List<BluetoothDevice>> = isBluetoothEnabled
        .flatMapLatest { connectedDevicesForProfile(BluetoothProfile.HEADSET) }
        .map { devices ->
            devices.filter { device ->
                featureFilter.any { feature ->
                    device.hasFeature(feature)
                }
            }
        }

    fun bondedDevices(): Set<BluetoothDevice> = adapter.bondedDevices

    suspend fun nudgeConnection(device: BluetoothDevice): Boolean = try {
        log(TAG) { "Nudging Android connection to $device" }

        val connectMethod = BluetoothHeadset::class.java.getDeclaredMethod(
            "connect", BluetoothDevice::class.java
        ).apply { isAccessible = true }

        connectMethod.invoke(getBluetoothProfile().profile, device)

        log(TAG) { "Nudged connection to $device" }
        true
    } catch (e: Exception) {
        Bugs.report(tag = TAG, "BluetoothHeadset.connect(device) is unavailable", exception = e)
        false
    }

    companion object {
        private val TAG = logTag("Bluetooth", "Manager2")
    }
}