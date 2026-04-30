package eu.darken.capod.monitor.core.ble

import eu.darken.capod.common.AppForegroundState
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BleScanModeControllerTest : BaseTest() {

    @Test
    fun `temporary override wins`() {
        resolveScannerMode(
            overrideMode = ScannerMode.LOW_POWER,
            isForeground = true,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = setOf(ADDRESS),
        ) shouldBe ScannerMode.LOW_POWER
    }

    @Test
    fun `connected paired profile uses low latency in foreground`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = true,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = setOf(ADDRESS),
        ) shouldBe ScannerMode.LOW_LATENCY
    }

    @Test
    fun `connected paired profile uses low latency in background`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = false,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = setOf(ADDRESS),
        ) shouldBe ScannerMode.LOW_LATENCY
    }

    @Test
    fun `foreground without connected paired profile uses balanced`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = true,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = emptySet(),
        ) shouldBe ScannerMode.BALANCED
    }

    @Test
    fun `background without connected paired profile uses low power`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = false,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = emptySet(),
        ) shouldBe ScannerMode.LOW_POWER
    }

    @Test
    fun `connected unprofiled device does not use low latency`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = true,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = setOf(OTHER_ADDRESS),
        ) shouldBe ScannerMode.BALANCED
    }

    @Test
    fun `connected unpaired profile does not use low latency`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = false,
            profileAddresses = setOf(ADDRESS),
            bondedAddresses = emptySet(),
            connectedAddresses = setOf(ADDRESS),
        ) shouldBe ScannerMode.LOW_POWER
    }

    @Test
    fun `low latency requires the same address to be profiled bonded and connected`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = false,
            profileAddresses = setOf(ADDRESS, OTHER_ADDRESS),
            bondedAddresses = setOf(ADDRESS, THIRD_ADDRESS),
            connectedAddresses = setOf(OTHER_ADDRESS, THIRD_ADDRESS),
        ) shouldBe ScannerMode.LOW_POWER
    }

    @Test
    fun `address matching is case insensitive`() {
        resolveScannerMode(
            overrideMode = null,
            isForeground = false,
            profileAddresses = setOf(ADDRESS.lowercase()),
            bondedAddresses = setOf(ADDRESS),
            connectedAddresses = setOf(ADDRESS.lowercase()),
        ) shouldBe ScannerMode.LOW_LATENCY
    }

    @Test
    fun `scannerMode emits even when connectedDevices never emits - regression guard`() = runTest {
        val controller = createController(
            isForeground = MutableStateFlow(true),
            profiles = MutableStateFlow(listOf(profileWithAddress(ADDRESS))),
            connectedDevices = MutableStateFlow(emptyList()),
            bondedAddresses = MutableStateFlow(emptySet()),
        )

        controller.scannerMode.first() shouldBe ScannerMode.BALANCED
    }

    @Test
    fun `scannerMode upgrades to LOW_LATENCY when bonded profile connects`() =
        runTest(UnconfinedTestDispatcher()) {
            val connected = MutableStateFlow<List<BluetoothDevice2>>(emptyList())
            val controller = createController(
                isForeground = MutableStateFlow(true),
                profiles = MutableStateFlow(listOf(profileWithAddress(ADDRESS))),
                connectedDevices = connected,
                bondedAddresses = MutableStateFlow(setOf(ADDRESS)),
            )

            val emissions = mutableListOf<ScannerMode>()
            val job = backgroundScope.launch { controller.scannerMode.collect { emissions += it } }
            runCurrent()

            emissions.last() shouldBe ScannerMode.BALANCED

            connected.value = listOf(deviceWithAddress(ADDRESS))
            runCurrent()

            emissions.last() shouldBe ScannerMode.LOW_LATENCY
            job.cancel()
        }

    @Test
    fun `withTemporaryOverride raises mode while active and releases on completion`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = createController(
                isForeground = MutableStateFlow(false),
                profiles = MutableStateFlow(emptyList()),
                connectedDevices = MutableStateFlow(emptyList()),
                bondedAddresses = MutableStateFlow(emptySet()),
            )

            val emissions = mutableListOf<ScannerMode>()
            val collector = backgroundScope.launch { controller.scannerMode.collect { emissions += it } }
            runCurrent()

            emissions.last() shouldBe ScannerMode.LOW_POWER

            val gate = CompletableDeferred<Unit>()
            val running = launch {
                controller.withTemporaryOverride(ScannerMode.LOW_LATENCY) { gate.await() }
            }
            runCurrent()

            emissions.last() shouldBe ScannerMode.LOW_LATENCY

            gate.complete(Unit)
            running.join()
            runCurrent()

            emissions.last() shouldBe ScannerMode.LOW_POWER
            collector.cancel()
        }

    @Test
    fun `overlapping withTemporaryOverride blocks both honored`() = runTest(UnconfinedTestDispatcher()) {
        val controller = createController(
            isForeground = MutableStateFlow(false),
            profiles = MutableStateFlow(emptyList()),
            connectedDevices = MutableStateFlow(emptyList()),
            bondedAddresses = MutableStateFlow(emptySet()),
        )

        val emissions = mutableListOf<ScannerMode>()
        val collector = backgroundScope.launch { controller.scannerMode.collect { emissions += it } }
        runCurrent()

        val gateA = CompletableDeferred<Unit>()
        val gateB = CompletableDeferred<Unit>()

        val a = launch { controller.withTemporaryOverride(ScannerMode.LOW_LATENCY) { gateA.await() } }
        val b = launch { controller.withTemporaryOverride(ScannerMode.LOW_LATENCY) { gateB.await() } }
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_LATENCY

        gateA.complete(Unit)
        a.join()
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_LATENCY

        gateB.complete(Unit)
        b.join()
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_POWER
        collector.cancel()
    }

    @Test
    fun `withTemporaryOverride releases on exception`() = runTest(UnconfinedTestDispatcher()) {
        val controller = createController(
            isForeground = MutableStateFlow(false),
            profiles = MutableStateFlow(emptyList()),
            connectedDevices = MutableStateFlow(emptyList()),
            bondedAddresses = MutableStateFlow(emptySet()),
        )

        val emissions = mutableListOf<ScannerMode>()
        val collector = backgroundScope.launch { controller.scannerMode.collect { emissions += it } }
        runCurrent()

        runCatching {
            controller.withTemporaryOverride(ScannerMode.LOW_LATENCY) {
                throw IllegalStateException("boom")
            }
        }
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_POWER
        collector.cancel()
    }

    @Test
    fun `withTemporaryOverride releases on cancellation`() = runTest(UnconfinedTestDispatcher()) {
        val controller = createController(
            isForeground = MutableStateFlow(false),
            profiles = MutableStateFlow(emptyList()),
            connectedDevices = MutableStateFlow(emptyList()),
            bondedAddresses = MutableStateFlow(emptySet()),
        )

        val emissions = mutableListOf<ScannerMode>()
        val collector = backgroundScope.launch { controller.scannerMode.collect { emissions += it } }
        runCurrent()

        val gate = CompletableDeferred<Unit>()
        val running = launch {
            try {
                controller.withTemporaryOverride(ScannerMode.LOW_LATENCY) { gate.await() }
            } catch (_: CancellationException) {
                // expected
            }
        }
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_LATENCY

        running.cancel()
        running.join()
        runCurrent()

        emissions.last() shouldBe ScannerMode.LOW_POWER
        collector.cancel()
    }

    private fun profileWithAddress(address: BluetoothAddress): DeviceProfile = AppleDeviceProfile(
        label = "test",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = address,
    )

    private fun deviceWithAddress(address: BluetoothAddress): BluetoothDevice2 = mockk {
        every { this@mockk.address } returns address
    }

    private fun TestScope.createController(
        isForeground: MutableStateFlow<Boolean>,
        profiles: MutableStateFlow<List<DeviceProfile>>,
        connectedDevices: MutableStateFlow<List<BluetoothDevice2>>,
        bondedAddresses: MutableStateFlow<Set<BluetoothAddress>>,
    ): BleScanModeController {
        val foregroundState: AppForegroundState = mockk {
            every { this@mockk.isForeground } returns isForeground
        }
        val profilesRepo: DeviceProfilesRepo = mockk {
            every { this@mockk.profiles } returns profiles
        }
        val bluetoothManager: BluetoothManager2 = mockk {
            every { this@mockk.connectedDevices } returns connectedDevices
            every { this@mockk.bondedDeviceAddresses } returns bondedAddresses
        }
        return BleScanModeController(
            appScope = backgroundScope,
            appForegroundState = foregroundState,
            profilesRepo = profilesRepo,
            bluetoothManager = bluetoothManager,
        )
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val OTHER_ADDRESS = "11:22:33:44:55:66"
        const val THIRD_ADDRESS = "22:33:44:55:66:77"
    }
}
