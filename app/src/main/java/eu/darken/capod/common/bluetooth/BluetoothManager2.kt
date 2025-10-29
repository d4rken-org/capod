package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager2 @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val manager: BluetoothManager,
) {

    val adapter: BluetoothAdapter?
        get() = manager.adapter

    val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

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

    fun getBluetoothProfile(profile: Int = BluetoothProfile.HEADSET): Flow<BluetoothProfile2> = callbackFlow {
        log(TAG, VERBOSE) { "getBluetoothProfile(profile=$profile)" }

        var profileProxy: BluetoothProfile2? = null
        manager.adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                log(TAG, VERBOSE) { "onServiceConnected(profile=$profile, proxy=$proxy)" }

                profileProxy = BluetoothProfile2(
                    profileType = profile,
                    profileProxy = proxy,
                ).also { trySend(it) }
            }

            override fun onServiceDisconnected(profile: Int) {
                log(TAG, WARN) { "onServiceDisconnected(profile=$profile)" }
                close()  // Close gracefully without exception to prevent crash
            }

        }, profile)

        awaitClose {
            log(TAG) { "Closing BluetoothProfile: $profileProxy" }
            profileProxy?.let {
                manager.adapter.closeProfileProxy(it.profileType, it.proxy)
            }
        }
    }


    private fun monitorProfile(
        profile: Int = BluetoothProfile.HEADSET
    ): Flow<Set<BluetoothDevice>> = getBluetoothProfile(profile).flatMapLatest { bluetoothProfile ->
        callbackFlow {
            log(TAG, VERBOSE) { "monitorProfile(): for profile=$profile starting" }

            try {
                trySend(bluetoothProfile.connectedDevices)
            } catch (e: Exception) {
                log(TAG, ERROR) { "monitorProfile(): Error querying initial connected devices: $e" }
                close(e)
                return@callbackFlow
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }

            val handlerThread = HandlerThread("BluetoothEventReceiver").apply { start() }
            val handler = Handler(handlerThread.looper)

            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    log(TAG, VERBOSE) { "monitorProfile(): Bluetooth event (intent=$intent, extras=${intent.extras})" }

                    if (intent.action == null) {
                        log(TAG, ERROR) { "monitorProfile(): Bluetooth event without action?" }
                        return
                    }
                    val device = intent.getParcelableExtra<BluetoothDevice?>(BluetoothDevice.EXTRA_DEVICE)
                    if (device == null) {
                        log(TAG, ERROR) { "monitorProfile(): Event is missing EXTRA_DEVICE" }
                        return
                    }

                    this@callbackFlow.launch {
                        if (intent.action != BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                            log(TAG, WARN) { "Unknown action: ${intent.action}" }
                            return@launch
                        }

                        // Profile connection changed - query actual state from proxy
                        val statePrevious = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
                        log(TAG) { "monitorProfile(): HEADSET profile state changed for $device - previous: $statePrevious" }

                        val stateNow = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        log(TAG) { "monitorProfile(): HEADSET profile state changed for $device - now: $stateNow" }

                        val currentDevices = try {
                            bluetoothProfile.connectedDevices
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "monitorProfile(): Error handling profile event: $e" }
                            // Log but continue - don't kill the whole Flow for one bad event
                            emptySet()
                        }.toMutableSet()
                        log(TAG) { "monitorProfile(): currentDevices: $currentDevices" }

                        when (stateNow) {
                            BluetoothProfile.STATE_CONNECTING -> {
                                log(TAG) { "monitorProfile(): Currently connecting $device" }
                            }

                            BluetoothProfile.STATE_CONNECTED -> {
                                log(TAG) { "monitorProfile(): Device has connected $device" }
                                if (!currentDevices.contains(device)) {
                                    log(
                                        TAG,
                                        VERBOSE
                                    ) { "monitorProfile(): $device not in proxy yet, adding manually" }
                                    currentDevices.add(device)
                                }
                                trySend(currentDevices)
                            }

                            BluetoothProfile.STATE_DISCONNECTING -> {
                                log(TAG) { "monitorProfile(): Currently DISconnecting $device" }
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                log(TAG) { "monitorProfile(): Device has disconnected $device" }
                                if (currentDevices.contains(device)) {
                                    log(
                                        TAG,
                                        VERBOSE
                                    ) { "monitorProfile(): $device still in proxy, removing manually" }
                                    currentDevices.remove(device)
                                }
                                trySend(currentDevices)
                            }
                        }
                    }
                }
            }

            try {
                context.registerReceiver(receiver, filter, null, handler)
            } catch (e: Exception) {
                log(TAG, ERROR) { "monitorProfile(): Failed to register receiver: $e" }
                close(e)
                return@callbackFlow
            }

            awaitClose {
                log(TAG, VERBOSE) { "monitorProfile(): profile=$profile closed." }
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "monitorProfile(): Error unregistering receiver: $e" }
                } finally {
                    handlerThread.quitSafely()
                }
            }
        }
    }

    private val seenDevicesLock = Mutex()
    private val seenDevicesCache = mutableMapOf<String, Instant>()

    val connectedDevices: Flow<List<BluetoothDevice2>> = isBluetoothEnabled
        .flatMapLatest { enabled ->
            if (enabled) monitorProfile(BluetoothProfile.HEADSET)
            else flowOf(emptySet())  // Return empty when Bluetooth is off
        }
        .map { devices ->
            val currentAddresses = devices.map { it.address }

            seenDevicesLock.withLock {
                val cleanedCache = seenDevicesCache.filterKeys { currentAddresses.contains(it) }
                seenDevicesCache.clear()
                seenDevicesCache.putAll(cleanedCache)
            }

            devices
                .filter { device ->
                    ContinuityProtocol.BLE_FEATURE_UUIDS.any { feature -> device.hasFeature(feature) }
                }
                .map { device ->
                    BluetoothDevice2(
                        internal = device,
                        seenFirstAt = seenDevicesLock.withLock {
                            seenDevicesCache[device.address] ?: Instant.now().also {
                                seenDevicesCache[device.address] = it
                            }
                        }
                    )
                }
        }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "connectedDevices" }
        .stateIn(
            scope = appScope + Dispatchers.IO,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
                replayExpirationMillis = 0L,
            ),
            initialValue = null
        )
        .filterNotNull()

    fun bondedDevices(): Flow<Set<BluetoothDevice2>> = flow {
        val rawDevices = adapter?.bondedDevices ?: throw IllegalStateException("Bluetooth adapter unavailable")
        val wrappedDevices = rawDevices.map { device ->

            BluetoothDevice2(
                internal = device,
                seenFirstAt = seenDevicesLock.withLock {
                    seenDevicesCache[device.address] ?: Instant.now().also {
                        seenDevicesCache[device.address] = it
                    }
                }
            )

        }.toSet()
        emit(wrappedDevices)
    }

    suspend fun nudgeConnection(device: BluetoothDevice2): Boolean = getBluetoothProfile().map { bluetoothProfile ->
        try {
            log(TAG) { "Nudging Android connection to $device" }

            val connectMethod = BluetoothHeadset::class.java.getDeclaredMethod(
                "connect", BluetoothDevice::class.java
            ).apply { isAccessible = true }

            connectMethod.invoke(bluetoothProfile.proxy, device.internal)

            log(TAG) { "Nudged connection to $device" }
            true
        } catch (e: Exception) {
            Bugs.report(tag = TAG, "BluetoothHeadset.connect(device) is unavailable", exception = e)
            false
        }
    }.first()

    companion object {
        private val TAG = logTag("Bluetooth", "Manager2")
    }
}