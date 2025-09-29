package eu.darken.capod.troubleshooter.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.monitor.core.PodMonitor
import eu.darken.capod.monitor.core.primaryDevice
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.unknown.UnknownDevice
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class TroubleShooterFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val profilesRepo: DeviceProfilesRepo,
    private val podMonitor: PodMonitor,
    private val debugSettings: DebugSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val _bleState = MutableStateFlow<BleState>(BleState.Intro())
    val bleState = _bleState.asLiveData2()

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
        log(TAG) { "troubleShootBle()" }

        generalSettings.scannerMode.value = ScannerMode.LOW_LATENCY

        run {
            progress("Checking for headphones...")
            val mainDevice = withTimeoutOrNull(STEP_TIME) {
                podMonitor.primaryDevice().filterNotNull().firstOrNull()
            }
            if (mainDevice != null) {
                success("Headphones found, nothing to troubleshoot.")
                return@launch
            } else {
                progress("Headphones not detected.\n")
            }
        }

        val doScan: suspend (Boolean, Boolean, Boolean, Boolean) -> Collection<PodDevice> = { hardwareFilteringDisabled,
                                                                                              hardwareBatchingDisabled,
                                                                                              indirectCallback,
                                                                                              unfiltered ->
            val sb = StringBuilder("SCAN - Settings: ")
            sb.append("hardwareFilteringDisabled=$hardwareFilteringDisabled, ")
            sb.append("hardwareBatchingDisabled=$hardwareBatchingDisabled, ")
            sb.append("indirectCallback=$indirectCallback, ")
            sb.append("unfiltered=$unfiltered")
            progress(sb.toString())
            generalSettings.isOffloadedFilteringDisabled.value = hardwareFilteringDisabled
            generalSettings.isOffloadedBatchingDisabled.value = hardwareBatchingDisabled
            generalSettings.useIndirectScanResultCallback.value = indirectCallback
            debugSettings.showUnfiltered.value = unfiltered

            val start = System.currentTimeMillis()
            val devices = withTimeoutOrNull(STEP_TIME) {
                podMonitor.devices
                    .take(10)
                    .takeWhile { System.currentTimeMillis() - start < STEP_TIME - 1000 }
                    .toList()
                    .flatten()
                    .distinctBy { it.address }
            } ?: emptyList()
            log(TAG) { "SCAN: BLE Devices: $devices" }
            if (devices.isNotEmpty()) {
                progress("SCAN: Received data from ${devices.size} BLE devices")
                devices
            } else {
                progress("SCAN: No data received")
                devices
            }
        }


        run {
            progress("Checking if we can receive BLE data at all.")
            if (doScan(false, false, false, true).isNotEmpty()) return@run
            if (doScan(false, false, true, true).isNotEmpty()) return@run
            if (doScan(true, true, true, true).isNotEmpty()) return@run
            if (doScan(true, true, false, true).isNotEmpty()) return@run
            if (doScan(true, false, true, true).isNotEmpty()) return@run
            if (doScan(true, false, false, true).isNotEmpty()) return@run
            if (doScan(false, true, true, true).isNotEmpty()) return@run
            if (doScan(false, true, false, true).isNotEmpty()) return@run

            failure("Phone is not receiving BLE data.", BleState.Result.Failure.Type.PHONE)

            generalSettings.isOffloadedFilteringDisabled.value = false
            generalSettings.isOffloadedBatchingDisabled.value = false
            generalSettings.useIndirectScanResultCallback.value = false
            debugSettings.showUnfiltered.value = false

            return@launch
        }

        progress("We received at least some BLE data.\n")

        run {
            progress("Checking for supported headphones.")

            if (doScan(false, false, false, false).any { it !is UnknownDevice }) return@run
            if (doScan(false, false, true, false).any { it !is UnknownDevice }) return@run
            if (doScan(true, true, true, false).any { it !is UnknownDevice }) return@run
            if (doScan(true, true, false, false).any { it !is UnknownDevice }) return@run
            if (doScan(true, false, true, false).any { it !is UnknownDevice }) return@run
            if (doScan(true, false, false, false).any { it !is UnknownDevice }) return@run
            if (doScan(false, true, true, false).any { it !is UnknownDevice }) return@run
            if (doScan(false, true, false, false).any { it !is UnknownDevice }) return@run

            failure("No compatible headphones found", BleState.Result.Failure.Type.HEADPHONES)

            generalSettings.isOffloadedFilteringDisabled.value = false
            generalSettings.isOffloadedBatchingDisabled.value = false
            generalSettings.useIndirectScanResultCallback.value = false

            return@launch
        }

        progress("Found some headphones that are supported by CAPod.\n")

        run {
            progress("Checking for your headphones with new BLE settings...")
            val mainDevice = withTimeoutOrNull(STEP_TIME) {
                podMonitor.primaryDevice().filterNotNull().firstOrNull()
            }
            if (mainDevice != null) {
                success("Found your headphones, new BLE settings worked :)!")
                return@launch
            }
        }

        progress("Still no headphones detected that count as yours.\n")

        run {
            progress("Checking all closeby headphones.")

            val otherDevices = withTimeoutOrNull(STEP_TIME) {
                val start = System.currentTimeMillis()
                podMonitor.devices
                    .take(10)
                    .takeWhile { System.currentTimeMillis() - start < STEP_TIME - 1000 }
                    .toList()
                    .flatten()
                    .distinctBy { it.address }
            } ?: emptyList()

            otherDevices.forEachIndexed { index, dev -> log(TAG) { "Device #$index: $dev" } }

            if (otherDevices.isEmpty()) {
                failure("No supported headphones found near your device.", BleState.Result.Failure.Type.HEADPHONES)
                return@launch
            }

            progress("Headphones found nearby, but not detected as yours.\n")
            progress("Creating profile for closest headphones.")

            val candidate = otherDevices
                .filter { it !is UnknownDevice }
                .maxBy { it.signalQuality }

            log(TAG, INFO) { "Candidate is $candidate" }

            profilesRepo.addProfile(
                profile = AppleDeviceProfile(
                    label = context.getString(R.string.troubleshooter_title),
                    model = candidate.model,
                ),
                addFirst = true,
            )

            val mainDevice = withTimeoutOrNull(STEP_TIME) {
                podMonitor.primaryDevice().filterNotNull().firstOrNull()
            }

            generalSettings.scannerMode.value = ScannerMode.BALANCED

            if (mainDevice != null) {
                success("Success! Detected your headphones.")
            } else {
                failure("No headphones detected near your device.", BleState.Result.Failure.Type.HEADPHONES)
            }
        }
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
                history = history + current
            )

            fun toSuccess(message: String) = Result.Success(
                history = history + message
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
        const val STEP_TIME = 10 * 1000L // 6 scans per 30 seconds max

        val TAG = logTag("TroubleShooter", "Fragment", "VM")
    }
}