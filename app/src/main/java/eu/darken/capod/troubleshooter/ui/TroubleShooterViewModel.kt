package eu.darken.capod.troubleshooter.ui

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.monitor.core.ble.BleScanModeController
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.unknown.UnknownSnapshotBle
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class TroubleShooterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val profilesRepo: DeviceProfilesRepo,
    private val blePodMonitor: BlePodMonitor,
    private val bleScanModeController: BleScanModeController,
    private val deviceMonitor: DeviceMonitor,
    private val timeSource: TimeSource,
) : ViewModel4(dispatcherProvider) {

    private val _bleState = MutableStateFlow<BleState>(BleState.Intro())

    /** Guards against re-entrant runs (e.g. a double tap on "Try again"). */
    private val runLock = Mutex()

    data class State(val bleState: BleState)

    val state = _bleState.map { State(it) }.asLiveState()

    init {
        _bleState
            .onEach { log(TAG) { "New BLE State: $it" } }
            .launchIn(vmScope)
    }

    private fun progress(message: String) {
        var state = _bleState.value
        if (state !is BleState.Working) {
            state = BleState.Working("Starting...")
        }
        _bleState.value = state.nextStep(message)
    }

    private fun success(message: String) {
        var state = _bleState.value
        if (state !is BleState.Working) {
            state = BleState.Working("Starting...")
        }
        _bleState.value = state.toSuccess(message)
    }

    private fun failure(message: String, type: BleState.Result.Failure.Type) {
        var state = _bleState.value
        if (state !is BleState.Working) {
            state = BleState.Working("Starting...")
        }
        _bleState.value = state.toFailure(message, type)
    }

    fun troubleShootBle() = launch(context = dispatcherProvider.IO) {
        if (!runLock.tryLock()) {
            log(TAG, WARN) { "troubleShootBle() ignored, a run is already in progress" }
            return@launch
        }
        log(TAG, INFO) { "troubleShootBle()" }

        try {
            bleScanModeController.withTemporaryOverride(ScannerMode.LOW_LATENCY) override@{
                // The combo under which supported headphones were detected (set in sweep 2).
                var supportedCombo: BlePodMonitor.CompatOverride? = null
                // Set only when the run reaches a terminal success. Persisted on exit; staying null
                // (any failure, or cancellation) means we just clear the override, which restores the
                // user's original — never-touched — settings.
                var comboToPersist: BlePodMonitor.CompatOverride? = null
                try {
                    run {
                        progress("Checking for headphones...")
                        if (findLiveBleHeadphones()) {
                            success("Headphones found, nothing to troubleshoot.")
                            return@override
                        } else {
                            progress("Headphones not detected.\n")
                        }
                    }

                    val doScan: suspend (BlePodMonitor.CompatOverride, Boolean) -> Collection<BlePodSnapshot> =
                        { combo, unfiltered ->
                            progress(
                                "SCAN - Settings: filteringDisabled=${combo.offloadedFilteringDisabled}, " +
                                    "batchingDisabled=${combo.offloadedBatchingDisabled}, " +
                                    "indirectCallback=${combo.indirectCallback}, unfiltered=$unfiltered"
                            )
                            blePodMonitor.setCompatOverride(combo)
                            blePodMonitor.setUnfilteredOverride(unfiltered)
                            val devices = collectFreshDevices()
                            log(TAG) { "SCAN: Fresh BLE devices: $devices" }
                            if (devices.isNotEmpty()) {
                                progress("SCAN: Received data from ${devices.size} BLE devices")
                            } else {
                                progress("SCAN: No data received")
                            }
                            devices
                        }

                    run {
                        progress("Checking if we can receive BLE data at all.")
                        val gotData = COMPAT_COMBOS.any { combo -> doScan(combo, true).isNotEmpty() }
                        if (!gotData) {
                            failure("Phone is not receiving BLE data.", BleState.Result.Failure.Type.PHONE)
                            return@override
                        }
                    }

                    progress("We received at least some BLE data.\n")

                    run {
                        progress("Checking for supported headphones.")
                        supportedCombo = COMPAT_COMBOS.firstOrNull { combo ->
                            doScan(combo, false).any { it !is UnknownSnapshotBle }
                        }
                        if (supportedCombo == null) {
                            failure("No compatible headphones found", BleState.Result.Failure.Type.HEADPHONES)
                            return@override
                        }
                    }

                    progress("Found some headphones that are supported by CAPod.\n")

                    run {
                        progress("Checking for your headphones with new BLE settings...")
                        if (findLiveBleHeadphones()) {
                            comboToPersist = supportedCombo
                            success("Found your headphones, new BLE settings worked :)!")
                            return@override
                        }
                    }

                    progress("Still no headphones detected that count as yours.\n")

                    run {
                        progress("Checking all closeby headphones.")

                        val otherDevices = collectFreshDevices()
                        otherDevices.forEachIndexed { index, dev -> log(TAG) { "Device #$index: $dev" } }

                        val candidate = otherDevices
                            .filter { it !is UnknownSnapshotBle }
                            .maxByOrNull { it.signalQuality }

                        if (candidate == null) {
                            failure(
                                "No supported headphones found near your device.",
                                BleState.Result.Failure.Type.HEADPHONES,
                            )
                            return@override
                        }

                        progress("Headphones found nearby, but not detected as yours.\n")
                        progress("Creating profile for closest headphones.")
                        log(TAG, INFO) { "Candidate is $candidate" }

                        profilesRepo.addProfile(
                            profile = AppleDeviceProfile(
                                label = context.getString(R.string.troubleshooter_title),
                                model = candidate.model,
                            ),
                            addFirst = true,
                        )

                        if (findLiveBleHeadphones()) {
                            comboToPersist = supportedCombo
                            success("Success! Detected your headphones.")
                        } else {
                            failure("No headphones detected near your device.", BleState.Result.Failure.Type.HEADPHONES)
                        }
                    }
                } finally {
                    blePodMonitor.setUnfilteredOverride(false)
                    try {
                        // Persist the winning combo before dropping the override so the effective scan
                        // settings stay equal with no restart flicker. Done in its own try so the
                        // override is still cleared (restoring originals) even if a write throws.
                        comboToPersist?.let { persistCompat(it) }
                    } finally {
                        blePodMonitor.setCompatOverride(null)
                    }
                }
            }
        } finally {
            runLock.unlock()
        }
    }

    /**
     * Waits up to [STEP_TIME] for the primary profile to be backed by a *fresh, live BLE*
     * observation. A cached-only / AAP-only primary does not count — the troubleshooter is about
     * whether BLE advertisements are actually reaching us. The freshness cutoff (snapshot seen at or
     * after this call) means it reflects the currently-active scan settings and doesn't rely on the
     * device cache having been cleared beforehand.
     */
    private suspend fun findLiveBleHeadphones(): Boolean {
        val threshold = timeSource.now()
        return withTimeoutOrNull(STEP_TIME) {
            deviceMonitor.primaryDevice().firstOrNull { device ->
                device?.ble != null && (device.seenLastAt?.let { it >= threshold } == true)
            } != null
        } ?: false
    }

    /**
     * Collects BLE devices observed *after the current scan settings take effect*. Option changes
     * restart the scan after a throttle, and [BlePodMonitor] keeps a 20s device cache, so without a
     * freshness cutoff a stale observation from a previous combo could be mistaken for a "win".
     */
    private suspend fun collectFreshDevices(): List<BlePodSnapshot> {
        // Drop anything cached under a previous combo so it can't satisfy this attempt.
        blePodMonitor.clearDeviceCache()
        val freshThreshold = timeSource.now().plusMillis(SCAN_SETTLE_MS)
        val start = timeSource.elapsedRealtime()
        val collected = mutableListOf<BlePodSnapshot>()
        // Accumulate as we go: a timeout must not discard what we already observed. toList() only
        // returns once the flow completes, which a quiet channel may never do within the window.
        withTimeoutOrNull(STEP_TIME) {
            blePodMonitor.devices
                .takeWhile { timeSource.elapsedRealtime() - start < STEP_TIME - 1000 }
                .collect { snapshots -> collected.addAll(snapshots) }
        }
        return collected
            .filter { it.seenLastAt >= freshThreshold }
            .distinctBy { it.address }
    }

    private fun persistCompat(combo: BlePodMonitor.CompatOverride) {
        generalSettings.isOffloadedFilteringDisabled.valueBlocking = combo.offloadedFilteringDisabled
        generalSettings.isOffloadedBatchingDisabled.valueBlocking = combo.offloadedBatchingDisabled
        generalSettings.useIndirectScanResultCallback.valueBlocking = combo.indirectCallback
    }

    sealed class BleState {
        class Intro : BleState()

        data class Working(
            val current: String,
            val history: List<String> = emptyList(),
        ) : BleState() {

            val allSteps: List<String>
                get() = history + current

            fun nextStep(message: String) = this.copy(
                current = message,
                history = history + current,
            )

            fun toSuccess(message: String) = Result.Success(
                history = history + message,
            )

            fun toFailure(message: String, type: Result.Failure.Type) = Result.Failure(
                failureType = type,
                history = history + message,
            )
        }

        sealed class Result : BleState() {

            abstract val history: List<String>

            data class Success(
                override val history: List<String>,
            ) : Result()

            data class Failure(
                val failureType: Type,
                override val history: List<String>,
            ) : Result() {
                enum class Type {
                    PHONE,
                    HEADPHONES,
                    ;
                }
            }
        }
    }

    companion object {
        const val STEP_TIME = 10 * 1000L

        /**
         * Grace period after switching compat settings before an observation counts as "fresh".
         * Covers [BlePodMonitor]'s ~1s scan-option throttle plus the scanner restart.
         */
        const val SCAN_SETTLE_MS = 1500L

        /**
         * Compatibility combinations to probe, ordered fewest-disables-first so the first one that
         * works (and gets persisted) is the *minimal* set of overrides — e.g. a phone that only
         * needs batching disabled won't also get filtering disabled. Triple semantics:
         * (offloadedFilteringDisabled, offloadedBatchingDisabled, indirectCallback).
         */
        val COMPAT_COMBOS: List<BlePodMonitor.CompatOverride> = listOf(
            BlePodMonitor.CompatOverride(false, false, false), // baseline (no overrides)
            BlePodMonitor.CompatOverride(false, true, false),  // batching only
            BlePodMonitor.CompatOverride(true, false, false),  // filtering only
            BlePodMonitor.CompatOverride(false, false, true),  // indirect callback only
            BlePodMonitor.CompatOverride(false, true, true),   // batching + indirect
            BlePodMonitor.CompatOverride(true, false, true),   // filtering + indirect
            BlePodMonitor.CompatOverride(true, true, false),   // filtering + batching
            BlePodMonitor.CompatOverride(true, true, true),    // everything
        )

        val TAG = logTag("TroubleShooter", "VM")
    }
}
