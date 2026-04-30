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
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
    private val timeSource: TimeSource,
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
                handlerThread.quitSafely()
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
                            seenDevicesCache[device.address] ?: run {
                                val now = timeSource.now()
                                seenDevicesCache[device.address] = now
                                now
                            }
                        }
                    )
                }
        }
        .retryWhen { cause, attempt ->
            log(TAG, WARN) { "connectedDevices Flow failed (attempt ${attempt + 1}): $cause" }
            when {
                // BLUETOOTH_CONNECT not granted (or revoked). Keep retrying — the next
                // attempt will succeed as soon as the user grants it. Terminating here
                // would leave the stateIn StateFlow serving a stale emptyList() forever.
                cause is SecurityException -> {
                    delay(3_000L)
                    true
                }
                attempt < 3 -> {
                    delay(1000 * (attempt + 1))
                    true
                }
                else -> false
            }
        }
        .catch { e ->
            log(TAG, ERROR) { "connectedDevices Flow failed after retries: $e" }
            emit(emptyList())  // Emit empty list and complete gracefully
        }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "connectedDevices" }
        .stateIn(
            scope = appScope + dispatcherProvider.IO,
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
                    seenDevicesCache[device.address] ?: run {
                        val now = timeSource.now()
                        seenDevicesCache[device.address] = now
                        now
                    }
                }
            )

        }.toSet()
        emit(wrappedDevices)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun queryBondedAddresses(): Set<BluetoothAddress> =
        adapter?.bondedDevices?.map { it.address }?.toSet() ?: emptySet()

    val bondedDeviceAddresses: Flow<Set<BluetoothAddress>> = callbackFlow {
        fun sendBondedAddresses(): Boolean = try {
            trySend(queryBondedAddresses())
            true
        } catch (e: Exception) {
            log(TAG, WARN) { "Error querying bonded device addresses: $e" }
            trySend(emptySet())
            close(e)
            false
        }

        if (!sendBondedAddresses()) return@callbackFlow

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                sendBondedAddresses()
            }
        }
        try {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to register bond-state receiver: $e" }
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                log(TAG, WARN) { "Error unregistering bond-state receiver: $e" }
            }
        }
    }
        .retryWhen { cause, attempt ->
            log(TAG, WARN) { "bondedDeviceAddresses Flow failed (attempt ${attempt + 1}): $cause" }
            when {
                cause is SecurityException -> {
                    delay(3_000L)
                    true
                }
                attempt < 3 -> {
                    delay(1000 * (attempt + 1))
                    true
                }
                else -> false
            }
        }
        .catch { e ->
            log(TAG, ERROR) { "bondedDeviceAddresses Flow failed after retries: $e" }
            emit(emptySet())
        }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "bondedDeviceAddresses" }
        .stateIn(
            scope = appScope + dispatcherProvider.IO,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000L,
                replayExpirationMillis = 0L,
            ),
            initialValue = emptySet(),
        )

    private var _isNudgeAvailable: Boolean = true
    val isNudgeAvailable: Boolean get() = _isNudgeAvailable

    /**
     * Set the Android-local alias for a bonded [device]. Updates what Android's system Bluetooth
     * settings display without touching the AirPods firmware itself. Uses reflection on the hidden
     * `setAlias(String)` method because the public API 30+ variant requires `BLUETOOTH_PRIVILEGED`,
     * which third-party apps cannot hold.
     *
     * Returns `true` on success, `false` if the call threw, was rejected, or returned `false`.
     *
     * Known failure mode on Android 12+: `SecurityException: does not have a CDM association with
     * the Bluetooth Device`. The hidden method was moved behind a Companion Device Manager (CDM)
     * permission check at the service layer — only apps that have explicitly requested and been
     * granted a CDM association for this specific device can rename it. CAPod does not currently
     * pursue a CDM association (that's a user-visible pairing flow), so setAlias is effectively
     * unavailable on modern Android and callers should be prepared to surface a user-facing
     * fallback when it returns false. See DeviceSettingsViewModel.setDeviceName.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun setDeviceAlias(device: BluetoothDevice2, alias: String): Boolean {
        return try {
            val method = BluetoothDevice::class.java.getDeclaredMethod("setAlias", String::class.java)
                .apply { isAccessible = true }
            val result = method.invoke(device.internal, alias) as? Boolean ?: false
            log(TAG) { "setDeviceAlias(${device.address}, $alias) -> $result" }
            result
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            log(TAG, WARN) { "setDeviceAlias(${device.address}, $alias) failed: $cause" }
            false
        }
    }

    suspend fun nudgeConnection(device: BluetoothDevice2): Boolean = getBluetoothProfile().map { bluetoothProfile ->
        try {
            log(TAG) { "Nudging Android connection to $device" }

            val connectMethod = BluetoothHeadset::class.java.getDeclaredMethod(
                "connect", BluetoothDevice::class.java
            ).apply { isAccessible = true }

            val accepted = connectMethod.invoke(bluetoothProfile.proxy, device.internal) as? Boolean ?: false
            log(TAG) { "Nudged connection to $device — accepted=$accepted" }
            accepted
        } catch (e: Exception) {
            val isSecurityException = e is SecurityException ||
                (e is java.lang.reflect.InvocationTargetException && e.cause is SecurityException)
            if (isSecurityException) {
                log(TAG, ERROR) { "nudgeConnection is permanently unavailable: missing MODIFY_PHONE_STATE permission" }
                _isNudgeAvailable = false
            }
            Bugs.report(tag = TAG, "BluetoothHeadset.connect(device) is unavailable", exception = e)
            false
        }
    }.first()

    companion object {
        private val TAG = logTag("Bluetooth", "Manager2")
    }
}
