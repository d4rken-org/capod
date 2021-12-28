package eu.darken.cap.pods.core.airpods

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import eu.darken.cap.pods.core.PodDevice
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import testhelper.BaseTest

abstract class BaseAirPodsTest : BaseTest() {

    @MockK lateinit var scanResult: ScanResult
    @MockK lateinit var scanRecord: ScanRecord
    @MockK lateinit var device: BluetoothDevice

    val factory = AirPodsFactory(
        proximityPairingDecoder = ProximityPairing.Decoder(),
        continuityProtocolDecoder = ContinuityProtocol.Decoder(),
    )

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { scanResult.scanRecord } returns scanRecord
        every { scanResult.rssi } returns -66
        every { scanResult.timestampNanos } returns 136136027721826
        every { scanResult.device } returns device
        every { device.address } returns "77:49:4C:D8:25:0C"
    }

    suspend inline fun <reified T : PodDevice?> create(hex: String, block: T.() -> Unit) {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        mockData(bytes)
        block.invoke(factory.create(scanResult) as T)
    }

    fun mockData(hex: String) {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return mockData(bytes)
    }

    fun mockData(data: ByteArray) {
        every { scanRecord.getManufacturerSpecificData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER) } returns data
    }
}