package eu.darken.capod.monitor.core.ble

import eu.darken.capod.common.AppForegroundState
import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.PodFactory
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.datastore.FakeDataStoreValue

class BlePodMonitorTest : BaseTest() {
    @Test
    fun `backgrounding with disconnected Pods cancels scanner even with a live subscriber`() = runTest {
        var started = 0
        var stopped = 0
        val fixture = createFixture {
            flow<Collection<BleScanResult>> {
                started++
                try { awaitCancellation() } finally { stopped++ }
            }
        }
        backgroundScope.launch { fixture.monitor.devices.collect {} }
        runCurrent()
        started shouldBe 1
        fixture.foreground.value = false
        runCurrent()
        stopped shouldBe 1
        fixture.foreground.value = true
        runCurrent()
        started shouldBe 2
    }

    @Test
    fun `scan security exception emits empty devices and rechecks permissions`() = runTest {
        val fixture = createFixture {
            flow<Collection<BleScanResult>> { throw SecurityException("scan denied") }
        }

        fixture.monitor.devices.drop(1).first() shouldBe emptyList()

        verify(exactly = 1) { fixture.permissionTool.recheck() }
        verify(exactly = 1) {
            fixture.bleScanner.scan(
                filters = any(),
                filterPolicy = any(),
                scannerMode = any(),
                disableOffloadFiltering = any(),
                disableOffloadBatching = any(),
                disableDirectScanCallback = any(),
            )
        }
    }

    @Test
    fun `non security scan failure retries`() = runTest {
        var attempts = 0
        val fixture = createFixture {
            attempts += 1
            if (attempts == 1) {
                flow<Collection<BleScanResult>> { throw IllegalStateException("temporary scanner failure") }
            } else {
                flowOf(emptyList())
            }
        }

        fixture.monitor.devices.drop(1).first() shouldBe emptyList()

        attempts shouldBe 2
        verify(exactly = 0) { fixture.permissionTool.recheck() }
        verify(exactly = 2) {
            fixture.bleScanner.scan(
                filters = any(),
                filterPolicy = any(),
                scannerMode = any(),
                disableOffloadFiltering = any(),
                disableOffloadBatching = any(),
                disableDirectScanCallback = any(),
            )
        }
    }

    @Test
    fun `address moving to another identity evicts the old identity`() = runTest {
        val harness = createScanHarness()
        val podA = harness.pod(address = ADDRESS_X)
        val podB = harness.pod(address = ADDRESS_X)

        harness.scan(podA) shouldBe listOf(podA)
        harness.scan(podB) shouldBe listOf(podB)
        harness.scan() shouldBe listOf(podB)
    }

    @Test
    fun `address moving to a case context identity still evicts the old identity`() = runTest {
        val harness = createScanHarness()
        val idB = BlePodSnapshot.Id()
        val podA = harness.pod(address = ADDRESS_X)
        val podBCase = harness.pod(address = ADDRESS_Y, id = idB, caseContext = true)
        val podBNoCase = harness.pod(address = ADDRESS_X, id = idB, caseContext = false)

        harness.scan(podA, podBCase) shouldContainExactlyInAnyOrder listOf(podA, podBCase)
        harness.scan(podBNoCase) shouldBe listOf(podBCase)
    }

    @Test
    fun `address moving to another identity within one batch keeps only the new identity`() = runTest {
        val harness = createScanHarness()
        val podA = harness.pod(address = ADDRESS_X)
        val podB = harness.pod(address = ADDRESS_X)

        harness.scan(podA, podB) shouldBe listOf(podB)
    }

    @Test
    fun `identity with a different address is kept`() = runTest {
        val harness = createScanHarness()
        val podA = harness.pod(address = ADDRESS_Y)
        val podB = harness.pod(address = ADDRESS_X)

        harness.scan(podA) shouldBe listOf(podA)
        harness.scan(podB) shouldContainExactlyInAnyOrder listOf(podA, podB)
    }

    private fun TestScope.createScanHarness(): ScanHarness {
        val batches = Channel<Collection<BleScanResult>>(Channel.UNLIMITED)
        val snapshotsByScan = mutableMapOf<BleScanResult, BlePodSnapshot>()
        val podFactory = mockk<PodFactory>().apply {
            coEvery { createPod(any()) } answers {
                val scanResult = firstArg<BleScanResult>()
                snapshotsByScan[scanResult]?.let { PodFactory.Result(scanResult = scanResult, device = it) }
            }
        }
        val timeSource = TestTimeSource()
        val fixture = createFixture(podFactory = podFactory, timeSource = timeSource) { batches.receiveAsFlow() }

        val emissions = mutableListOf<List<BlePodSnapshot>>()
        backgroundScope.launch { fixture.monitor.devices.collect { emissions.add(it) } }
        runCurrent()

        return ScanHarness(
            testScope = this,
            batches = batches,
            snapshotsByScan = snapshotsByScan,
            emissions = emissions,
            timeSource = timeSource,
        )
    }

    private class ScanHarness(
        private val testScope: TestScope,
        private val batches: Channel<Collection<BleScanResult>>,
        private val snapshotsByScan: MutableMap<BleScanResult, BlePodSnapshot>,
        private val emissions: List<List<BlePodSnapshot>>,
        private val timeSource: TestTimeSource,
    ) {
        private var scanCounter = 0L

        fun pod(
            address: String,
            id: BlePodSnapshot.Id = BlePodSnapshot.Id(),
            caseContext: Boolean = false,
        ): DualApplePods {
            val seenAt = timeSource.now()
            return mockk<DualApplePods>(relaxed = true).apply {
                every { identifier } returns id
                every { this@apply.address } returns address
                every { seenLastAt } returns seenAt
                every { hasCaseContext } returns caseContext
                every { meta } returns ApplePods.AppleMeta()
            }
        }

        fun scan(vararg pods: BlePodSnapshot): List<BlePodSnapshot> {
            val batch = pods.map { pod ->
                scanCounter += 1
                BleScanResult(
                    receivedAt = timeSource.now(),
                    address = "scan-$scanCounter",
                    rssi = -50,
                    generatedAtNanos = scanCounter,
                    manufacturerSpecificData = emptyMap(),
                ).also { snapshotsByScan[it] = pod }
            }
            val emissionsBefore = emissions.size
            batches.trySend(batch)
            testScope.runCurrent()
            emissions.size shouldBe emissionsBefore + 1
            return emissions.last()
        }
    }

    private fun TestScope.createFixture(
        podFactory: PodFactory = mockk(relaxed = true),
        timeSource: TimeSource = TestTimeSource(),
        scanFlowFactory: () -> Flow<Collection<BleScanResult>>,
    ): Fixture {
        mockkObject(ProximityPairing)
        every { ProximityPairing.getBleScanFilter() } returns emptySet()

        val bleScanner = mockk<BleScanner>().apply {
            every {
                scan(
                    filters = any(),
                    filterPolicy = any(),
                    scannerMode = any(),
                    disableOffloadFiltering = any(),
                    disableOffloadBatching = any(),
                    disableDirectScanCallback = any(),
                )
            } answers { scanFlowFactory() }
        }

        val scanModeController = mockk<BleScanModeController>().apply {
            every { scannerMode } returns MutableStateFlow(ScannerMode.BALANCED)
        }
        val generalSettings = mockk<GeneralSettings>().apply {
            every { isOffloadedBatchingDisabled } returns FakeDataStoreValue(false).mock
            every { isOffloadedFilteringDisabled } returns FakeDataStoreValue(false).mock
            every { useIndirectScanResultCallback } returns FakeDataStoreValue(false).mock
        }
        val permissionTool = mockk<PermissionTool>().apply {
            every { missingScanPermissions } returns MutableStateFlow<Set<Permission>>(emptySet())
            every { recheck() } just Runs
        }
        val foreground = MutableStateFlow(true)
        val appForeground = mockk<AppForegroundState> {
            every { isForeground } returns foreground
        }
        val bluetoothManager = mockk<BluetoothManager2>().apply {
            every { isBluetoothEnabled } returns MutableStateFlow(true)
            every { connectedDevices } returns flowOf(emptyList())
        }
        val profilesRepo = mockk<DeviceProfilesRepo>().apply {
            every { profiles } returns MutableStateFlow(emptyList())
        }
        return Fixture(
            monitor = BlePodMonitor(
                appScope = backgroundScope,
                bleScanner = bleScanner,
                bleScanModeController = scanModeController,
                podFactory = podFactory,
                timeSource = timeSource,
                generalSettings = generalSettings,
                bluetoothManager = bluetoothManager,
                appForegroundState = appForeground,
                permissionTool = permissionTool,
                profilesRepo = profilesRepo,
            ),
            bleScanner = bleScanner,
            permissionTool = permissionTool,
            foreground = foreground,
        )
    }

    private data class Fixture(
        val monitor: BlePodMonitor,
        val bleScanner: BleScanner,
        val permissionTool: PermissionTool,
        val foreground: MutableStateFlow<Boolean>,
    )

    companion object {
        private const val ADDRESS_X = "00:11:22:33:44:01"
        private const val ADDRESS_Y = "00:11:22:33:44:02"
    }
}
