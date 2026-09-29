package eu.darken.capod.common.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback for devices where [BluetoothManager2.nudgeConnection] is [NudgeAvailability.BROKEN],
 * e.g. Samsung One UI 8 / Android 16 where `BluetoothHeadset.connect()` is rejected server-side
 * with `SecurityException: ... MODIFY_PHONE_STATE`.
 *
 * We cannot ask the audio profiles to connect ourselves, but we can make the *system* do it:
 *
 * 1. [BluetoothDevice.fetchUuidsWithSdp] pages the bonded device (creates an ACL link) and makes the
 *    Bluetooth stack broadcast `ACTION_UUID` for it a fixed [UUID_INTENT_DELAY_MS] later.
 * 2. The system's SettingsLib (`CachedBluetoothDevice.onUuidChanged`, running in SystemUI with
 *    `BLUETOOTH_PRIVILEGED`) reacts to that broadcast by calling `BluetoothDevice.connect()`,
 *    which brings up HFP/A2DP, but only if the device is still ACL-connected at that moment.
 * 3. AirPods drop a bare ACL link after ~3.5s, so we re-page (RFCOMM to the HFP record, falling
 *    back to an L2CAP socket) whenever the link is down until the broadcast has had its chance.
 * 4. Our RFCOMM channel sits on the pods' HFP server channel, which blocks the system's HFP
 *    connect, so every held socket is released the moment `ACTION_UUID` fires (timer fallback
 *    shortly after the expected delay).
 *
 * Everything here only needs `BLUETOOTH_CONNECT`. Success is defined as the device showing up in
 * [BluetoothManager2.connectedDevices]; held sockets are closed as soon as that happens so
 * [eu.darken.capod.pods.core.apple.aap.AapConnectionManager] does not race a second channel.
 */
@Singleton
@SuppressLint("MissingPermission")
class AclPager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager2,
    private val socketFactory: L2capSocketFactory,
    private val dispatcherProvider: DispatcherProvider,
    private val timeSource: TimeSource,
) {

    enum class Result {
        /** Device appeared in the headset profile's connected set during/after paging. */
        CONNECTED,

        /** Paging ran but the device did not connect within the window. */
        NOT_CONNECTED,

        /** Another page for any device is still running. */
        BUSY,

        /** Last attempt was less than [COOLDOWN_MS] ago. */
        COOLDOWN,

        /** No usable [BluetoothDevice] or no strategy could even be started. */
        UNAVAILABLE,
    }

    private val mutex = Mutex()
    private var lastAttemptAt: Long? = null

    suspend fun page(device: BluetoothDevice2): Result = withContext(dispatcherProvider.IO) {
        val target = device.internal
        if (target == null) {
            log(TAG, WARN) { "No internal BluetoothDevice for ${device.address}" }
            return@withContext Result.UNAVAILABLE
        }

        if (!mutex.tryLock()) {
            log(TAG, VERBOSE) { "Paging already in progress, skipping ${device.address}" }
            return@withContext Result.BUSY
        }
        try {
            val now = timeSource.elapsedRealtime()
            val sinceLast = lastAttemptAt?.let { now - it }
            if (sinceLast != null && sinceLast < COOLDOWN_MS) {
                log(TAG, VERBOSE) { "Paging on cooldown (${COOLDOWN_MS - sinceLast}ms left), skipping ${device.address}" }
                return@withContext Result.COOLDOWN
            }
            lastAttemptAt = now

            runPagingWindow(device, target)
        } finally {
            // Cooldown counts from the end of the window, not its start.
            lastAttemptAt = timeSource.elapsedRealtime()
            mutex.unlock()
        }
    }

    private suspend fun runPagingWindow(device: BluetoothDevice2, target: BluetoothDevice): Result {
        val start = timeSource.elapsedRealtime()
        val deadline = start + WINDOW_MS
        val held = java.util.Collections.synchronizedList(mutableListOf<BluetoothSocket>())
        var attempts = 0
        var pagesStarted = 0
        var cycles = 0
        var cycleStart = start
        var lastPageAt = Long.MIN_VALUE / 2
        var released = false
        var inFlight: Job? = null

        val uuidBroadcastSeen = AtomicBoolean(false)
        val uuidReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_UUID) return
                val extra = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (!extra.address.equals(device.address, ignoreCase = true)) return
                log(TAG) { "ACTION_UUID broadcast for ${device.address} at +${timeSource.elapsedRealtime() - start}ms" }
                uuidBroadcastSeen.set(true)
            }
        }
        val receiverRegistered = runCatching {
            context.registerReceiver(uuidReceiver, IntentFilter(BluetoothDevice.ACTION_UUID))
        }.onFailure { log(TAG, WARN) { "Could not register ACTION_UUID receiver: $it" } }.isSuccess

        fun startCycle(): Boolean {
            val started = try {
                target.fetchUuidsWithSdp()
            } catch (e: SecurityException) {
                log(TAG, WARN) { "fetchUuidsWithSdp(${device.address}) denied: $e" }
                false
            }
            cycles++
            cycleStart = timeSource.elapsedRealtime()
            uuidBroadcastSeen.set(false)
            released = false
            log(TAG, INFO) {
                "Paging ${device.address} (cycle $cycles/$MAX_CYCLES, +${cycleStart - start}ms): SDP started=$started, " +
                    "UUID broadcast expected in ${UUID_INTENT_DELAY_MS}ms"
            }
            attempts++
            if (started) {
                pagesStarted++
                lastPageAt = cycleStart
            }
            return started
        }

        startCycle()

        try {
            coroutineScope {
                while (true) {
                    if (isProfileConnected(device)) {
                        log(TAG, INFO) { "Connected after paging (${timeSource.elapsedRealtime() - start}ms, pages=$pagesStarted, cycles=$cycles)" }
                        return@coroutineScope
                    }
                    val now = timeSource.elapsedRealtime()
                    if (now >= deadline) break

                    val sinceCycle = now - cycleStart
                    val broadcastPhase = uuidBroadcastSeen.get() || sinceCycle >= UUID_INTENT_DELAY_MS + UUID_INTENT_SLACK_MS
                    val aclUp = isAclConnected(device)

                    // Once the broadcast is out the system takes over; get off the HFP channel at once.
                    if (broadcastPhase && !released) {
                        released = true
                        log(TAG, INFO) { "Releasing ${held.size} held socket(s) at +${now - start}ms so the system can connect" }
                        inFlight?.cancel()
                        synchronized(held) { held.forEach { it.closeQuietly() }; held.clear() }
                    }

                    // Broadcast came and went without the link being up: nothing will happen this
                    // cycle, so schedule another one (a fresh fetchUuidsWithSdp = fresh broadcast).
                    if (broadcastPhase && !aclUp && sinceCycle >= UUID_INTENT_DELAY_MS + CYCLE_RETRY_AFTER_MS) {
                        if (cycles >= MAX_CYCLES) break
                        startCycle()
                        delay(POLL_MS)
                        continue
                    }

                    val busy = inFlight?.isActive == true
                    val canRepage = !broadcastPhase && !busy && now - lastPageAt >= REPAGE_MIN_GAP_MS && attempts < MAX_PAGES
                    if (!aclUp && canRepage) {
                        log(TAG, INFO) { "ACL link is down at +${now - start}ms, re-paging ${device.address}" }
                        lastPageAt = now
                        attempts++
                        inFlight = launch {
                            holdLink(device, target)?.let {
                                held += it
                                pagesStarted++
                            }
                        }
                    }
                    delay(POLL_MS)
                }
                inFlight?.cancel()
            }
        } finally {
            synchronized(held) { held.forEach { it.closeQuietly() }; held.clear() }
            if (receiverRegistered) runCatching { context.unregisterReceiver(uuidReceiver) }
        }

        if (isProfileConnected(device)) return Result.CONNECTED
        log(TAG, INFO) { "Not connected after ${timeSource.elapsedRealtime() - start}ms (pages=$pagesStarted, cycles=$cycles)" }
        return if (pagesStarted == 0) Result.UNAVAILABLE else Result.NOT_CONNECTED
    }

    /**
     * Bring the ACL link (back) up and keep a channel open on it. RFCOMM to the HFP service record
     * first: the pods accept it and it holds the link for a few seconds. Falls back to an L2CAP
     * socket to the AAP PSM, which some stacks refuse, but the page itself still happens.
     * Returns the socket to hold, or null if nothing could be opened.
     */
    private suspend fun holdLink(device: BluetoothDevice2, target: BluetoothDevice): BluetoothSocket? {
        val rfcomm = runCatching { target.createInsecureRfcommSocketToServiceRecord(HFP_UUID) }
            .onFailure { log(TAG, WARN) { "RFCOMM socket creation failed for ${device.address}: $it" } }
            .getOrNull()
        if (rfcomm != null) {
            val result = rfcomm.connectCancellable(device.address)
            log(TAG, INFO) { "RFCOMM (HFP) connect: ${result.exceptionOrNull() ?: "ok"}" }
            if (result.isSuccess) return rfcomm
            rfcomm.closeQuietly()
        }

        val l2cap = runCatching { socketFactory.createSocket(target, AAP_PSM) }
            .onFailure { log(TAG, WARN) { "L2CAP socket creation failed for ${device.address}: $it" } }
            .getOrNull() ?: return null
        val result = l2cap.connectCancellable(device.address)
        log(TAG, INFO) { "L2CAP (PSM=0x${"%04X".format(AAP_PSM)}) connect: ${result.exceptionOrNull() ?: "ok"}" }
        // Even a refused channel paged the link; keep the socket object around until we're done.
        return l2cap
    }

    private suspend fun isProfileConnected(device: BluetoothDevice2): Boolean = runCatching {
        bluetoothManager.connectedDevices.first().any { it.address.equals(device.address, ignoreCase = true) }
    }.getOrDefault(false)

    private fun isAclConnected(device: BluetoothDevice2): Boolean =
        bluetoothManager.aclConnectedAddresses.value.contains(device.address.uppercase(Locale.US))

    /**
     * [BluetoothSocket.connect] blocks the calling thread and ignores coroutine cancellation. Run it
     * on its own daemon thread and bound it; closing the socket is the only way to unblock it.
     * Mirrors `AapConnection.connectCancellable`.
     */
    private suspend fun BluetoothSocket.connectCancellable(address: String): kotlin.Result<Unit> {
        val outcome = CompletableDeferred<kotlin.Result<Unit>>()
        val abandoned = AtomicBoolean(false)
        val thread = Thread(
            {
                val result = runCatching { connect() }
                outcome.complete(result)
                if (abandoned.get() && result.isSuccess) closeQuietly()
            },
            "AclPager-connect-$address",
        ).apply {
            isDaemon = true
            start()
        }

        return try {
            withTimeout(SOCKET_CONNECT_TIMEOUT_MS) { outcome.await() }
        } catch (e: Exception) {
            abandoned.set(true)
            closeQuietly()
            thread.interrupt()
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            kotlin.Result.failure(e)
        }
    }

    private fun BluetoothSocket.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "AclPager")
        private const val AAP_PSM = 0x1001
        private val HFP_UUID: UUID = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB")

        /** `RemoteDevices.UUID_INTENT_DELAY` in the AOSP Bluetooth app. */
        internal const val UUID_INTENT_DELAY_MS = 6_000L
        internal const val COOLDOWN_MS = 15_000L

        /** Grace after the expected broadcast time before we let go of the link regardless. */
        internal const val UUID_INTENT_SLACK_MS = 500L

        /** Wait this long after a missed broadcast before starting another SDP cycle. */
        internal const val CYCLE_RETRY_AFTER_MS = 1_500L
        internal const val MAX_CYCLES = 3

        /** Three cycles plus the system's own connect getting HFP up (seen: ~6.5s). */
        internal const val WINDOW_MS = 30_000L
        internal const val REPAGE_MIN_GAP_MS = 750L
        internal const val MAX_PAGES = 12
        internal const val SOCKET_CONNECT_TIMEOUT_MS = 3_000L
        internal const val POLL_MS = 250L
    }
}
