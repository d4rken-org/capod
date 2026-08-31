package eu.darken.capod.common.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.TestTimeSource
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A capture that shows no BLE results has to answer whether the scan was filtered at all. The
 * configuration is decided once when the scan starts, which is usually before the recording the
 * investigator is reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BleScannerConfigLogTest : BaseTest() {

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    @Before
    fun setup() {
        Bugs.isDebug.value = false
        Logging.install(logCapture)
    }

    @After
    fun teardown() {
        Logging.remove(logCapture)
        Bugs.isDebug.value = false
    }

    private fun configLines() = logLines.filter { it.startsWith("Scan config:") }

    private fun createScanner(
        offloadFilteringSupported: Boolean = true,
        offloadBatchingSupported: Boolean = true,
    ): BleScanner {
        val adapter = mockk<BluetoothAdapter>(relaxed = true).apply {
            every { isOffloadedFilteringSupported } returns offloadFilteringSupported
            every { isOffloadedScanBatchingSupported } returns offloadBatchingSupported
        }
        val leScanner = mockk<BluetoothLeScanner>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager2>().apply {
            every { this@apply.adapter } returns adapter
            every { scanner } returns leScanner
        }
        return BleScanner(
            context = ApplicationProvider.getApplicationContext(),
            bluetoothManager = bluetoothManager,
            scanResultForwarder = BleScanResultForwarder(),
            timeSource = TestTimeSource(),
        )
    }

    private fun createConfig(
        filterPolicy: ScanFilterPolicy = ScanFilterPolicy.PROXIMITY_PAIRING,
        platformFilterCount: Int = 1,
        requestedFilterCount: Int = 1,
        offloadFilteringSupported: Boolean = true,
        offloadFilteringDisabledBySetting: Boolean = false,
        offloadBatchingSupported: Boolean = true,
        offloadBatchingDisabledBySetting: Boolean = false,
    ) = ScanConfig(
        scannerMode = ScannerMode.BALANCED,
        filterPolicy = filterPolicy,
        platformFilterCount = platformFilterCount,
        requestedFilterCount = requestedFilterCount,
        offloadFilteringSupported = offloadFilteringSupported,
        offloadFilteringDisabledBySetting = offloadFilteringDisabledBySetting,
        offloadBatchingSupported = offloadBatchingSupported,
        offloadBatchingDisabledBySetting = offloadBatchingDisabledBySetting,
        reportDelayMs = 1000L,
        directCallback = true,
    )

    /**
     * Both render `offloadFilteringRequested=false`, and the reader has to be able to tell a device
     * that cannot offload from a user who turned offloading off.
     */
    @Test
    fun `an unsupported adapter reads differently from a disabled setting`() {
        val unsupported = createConfig(
            offloadFilteringSupported = false,
            offloadBatchingSupported = false,
        ).summary()
        unsupported shouldContain "offloadFilteringRequested=false (supported=false, disabledBySetting=false)"
        unsupported shouldContain "offloadBatchingRequested=false (supported=false, disabledBySetting=false)"

        val disabled = createConfig(
            offloadFilteringDisabledBySetting = true,
            offloadBatchingDisabledBySetting = true,
        ).summary()
        disabled shouldContain "offloadFilteringRequested=false (supported=true, disabledBySetting=true)"
        disabled shouldContain "offloadBatchingRequested=false (supported=true, disabledBySetting=true)"
    }

    @Test
    fun `the filters that reached the platform are reported against the requested ones`() {
        createConfig(
            platformFilterCount = 0,
            requestedFilterCount = 1,
            offloadFilteringDisabledBySetting = true,
        ).summary() shouldContain "platformFilters=0/1"

        createConfig(platformFilterCount = 1, requestedFilterCount = 1).summary() shouldContain "platformFilters=1/1"
    }

    /**
     * The unfiltered scan mode is implemented as a single match-all filter, so the filter count
     * alone reports it as a filtered scan. Only the policy separates the two.
     */
    @Test
    fun `a match-all scan is distinguishable from a filtered one at the same filter count`() {
        createConfig(filterPolicy = ScanFilterPolicy.MATCH_ALL).summary() shouldContain
            "filterPolicy=MATCH_ALL, platformFilters=1/1"

        createConfig(filterPolicy = ScanFilterPolicy.PROXIMITY_PAIRING).summary() shouldContain
            "filterPolicy=PROXIMITY_PAIRING, platformFilters=1/1"
    }

    @Test
    fun `the scan config is logged when the scan starts`() = runTest {
        createScanner()
            .scan(filters = emptySet(), filterPolicy = ScanFilterPolicy.PROXIMITY_PAIRING)
            .launchIn(backgroundScope)
        runCurrent()

        configLines() shouldHaveSize 1
        configLines().single() shouldContain "filterPolicy=PROXIMITY_PAIRING"
    }

    @Test
    fun `a recording started while the scan runs re-logs the config`() = runTest {
        createScanner()
            .scan(filters = emptySet(), filterPolicy = ScanFilterPolicy.MATCH_ALL)
            .launchIn(backgroundScope)
        runCurrent()

        Bugs.isDebug.value = true
        runCurrent()

        configLines() shouldHaveSize 2
        configLines().last().endsWith("(recording started)") shouldBe true
    }

    /**
     * The re-emission suppresses the state it was already logged for, otherwise a scan starting
     * inside a running recording writes the same line twice.
     */
    @Test
    fun `a scan started during a recording logs the config once`() = runTest {
        Bugs.isDebug.value = true

        createScanner()
            .scan(filters = emptySet(), filterPolicy = ScanFilterPolicy.PROXIMITY_PAIRING)
            .launchIn(backgroundScope)
        runCurrent()

        configLines() shouldHaveSize 1
    }
}
