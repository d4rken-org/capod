package eu.darken.capod.monitor.core.ble

import eu.darken.capod.common.TimeSource
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.pods.core.apple.ble.PodFactory
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.datastore.FakeDataStoreValue

class BlePodMonitorTest : BaseTest() {

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
                scannerMode = any(),
                disableOffloadFiltering = any(),
                disableOffloadBatching = any(),
                disableDirectScanCallback = any(),
            )
        }
    }

    private fun TestScope.createFixture(
        scanFlowFactory: () -> Flow<Collection<BleScanResult>>,
    ): Fixture {
        mockkObject(ProximityPairing)
        every { ProximityPairing.getBleScanFilter() } returns emptySet()

        val bleScanner = mockk<BleScanner>().apply {
            every {
                scan(
                    filters = any(),
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
        val debugSettings = mockk<DebugSettings>().apply {
            every { showUnfiltered } returns FakeDataStoreValue(false).mock
        }
        val permissionTool = mockk<PermissionTool>().apply {
            every { missingScanPermissions } returns MutableStateFlow<Set<Permission>>(emptySet())
            every { recheck() } just Runs
        }
        val bluetoothManager = mockk<BluetoothManager2>().apply {
            every { isBluetoothEnabled } returns MutableStateFlow(true)
        }
        val profilesRepo = mockk<DeviceProfilesRepo>().apply {
            every { profiles } returns MutableStateFlow(emptyList())
        }
        val timeSource: TimeSource = TestTimeSource()

        return Fixture(
            monitor = BlePodMonitor(
                appScope = backgroundScope,
                bleScanner = bleScanner,
                bleScanModeController = scanModeController,
                podFactory = mockk<PodFactory>(relaxed = true),
                timeSource = timeSource,
                generalSettings = generalSettings,
                bluetoothManager = bluetoothManager,
                debugSettings = debugSettings,
                permissionTool = permissionTool,
                profilesRepo = profilesRepo,
            ),
            bleScanner = bleScanner,
            permissionTool = permissionTool,
        )
    }

    private data class Fixture(
        val monitor: BlePodMonitor,
        val bleScanner: BleScanner,
        val permissionTool: PermissionTool,
    )
}
