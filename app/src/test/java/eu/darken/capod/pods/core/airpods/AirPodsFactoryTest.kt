package eu.darken.capod.pods.core.airpods

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import eu.darken.capod.pods.core.DualPods
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.airpods.models.AirPodsGen1
import eu.darken.capod.pods.core.airpods.models.AirPodsPro
import eu.darken.capod.pods.core.airpods.models.UnknownAppleDevice
import eu.darken.capod.pods.core.airpods.protocol.ContinuityProtocol
import eu.darken.capod.pods.core.airpods.protocol.ProximityPairing
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AirPodsFactoryTest : BaseTest() {

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

    private suspend inline fun <reified T : PodDevice?> create(hex: String, block: T.() -> Unit) {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        mockData(bytes)
        block.invoke(factory.create(scanResult) as T)
    }

    private fun mockData(hex: String) {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return mockData(bytes)
    }

    private fun mockData(data: ByteArray) {
        every { scanRecord.getManufacturerSpecificData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER) } returns data
    }

    @Test
    fun `test AirPodDevice - active microphone`() = runBlockingTest {
        create<DualApplePods>("07 19 01 0E 20 >2B< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00101011
            // --^-----
            microPhonePod shouldBe DualPods.Pod.LEFT
        }
        create<DualApplePods>("07 19 01 0E 20 >0B< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00001011
            // --^-----
            microPhonePod shouldBe DualPods.Pod.RIGHT
        }
    }

    @Test
    fun `test AirPodDevice - left pod ear status`() = runBlockingTest {
        // Left Pod primary
        create<DualApplePods>("07 19 01 0E 20 >22< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100010
            // 765432¹0
            isLeftPodInEar shouldBe true
        }
        create<DualApplePods>("07 19 01 0E 20 >20< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100000
            // 765432¹0
            isLeftPodInEar shouldBe false
        }

        // Right Pod is primary
        create<DualApplePods>("07 19 01 0E 20 >09< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00001001
            // 7654³210
            isLeftPodInEar shouldBe true
        }
        create<DualApplePods>("07 19 01 0E 20 >20< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000001
            // 7654³210
            isLeftPodInEar shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - right pod ear status`() = runBlockingTest {
        // Left Pod primary
        create<DualApplePods>("07 19 01 0E 20 >29< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00101001
            // 7654³210
            isRightPodInEar shouldBe true
        }
        create<DualApplePods>("07 19 01 0E 20 >21< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100001
            // 7654³210
            isRightPodInEar shouldBe false
        }

        // Right Pod is primary
        create<DualApplePods>("07 19 01 0E 20 >03< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000011
            // 765432¹0
            isRightPodInEar shouldBe true
        }
        create<DualApplePods>("07 19 01 0E 20 >01< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000001
            // 765432¹0
            isRightPodInEar shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - battery status`() = runBlockingTest {
        // Right Pod is primary
        create<DualApplePods>("07 19 01 0E 20 0B >98< 94 52 00 05 09 73 3C 3D F9 2C 3E B3 DD 76 02 DD 4E 16 FD FB") {
            // 88 10001000
            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.8f
        }
        // Left Pod primary
        create<DualApplePods>("07 19 01 0E 20 2B >89< 94 52 00 05 09 73 3C 3D F9 2C 3E B3 DD 76 02 DD 4E 16 FD FB") {
            // F8 11111000
            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.8f
        }
    }

    @Test
    fun `test AirPodDevice - pod charging`() = runBlockingTest {
        /**
         * Right pod is charging
         */
        // This is the left
        create<DualApplePods>("07 19 01 0E 20 51 89 >94< 52 00 00 F4 89 82 6D 3E 27 7F 26 62 57 D0 E2 A6 49 E9 35") {
            // 1001 0100
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true
        }
        // This is the right
        create<DualApplePods>("07 19 01 0E 20 31 98 >A4< 01 00 00 31 B9 A0 C4 80 CD D1 CF B9 3A 9A 6D 48 31 08 EB") {
            // 1010 0100
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true
        }

        /**
         * Left pod is charging
         */
        // This is the left
        create<DualApplePods>("07 19 01 0E 20 71 98 >94< 52 00 05 A5 37 31 B2 BD 42 68 0C 64 FD 00 99 4A E5 3E F4") {
            // 1001 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe false
        }
        // This is the right
        create<DualApplePods>("07 19 01 0E 20 11 89 >A4< 04 00 04 BA 79 1B C0 65 69 C6 9F 19 6E 37 7D 6D 86 8D D9") {
            // 1010 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe false
        }

        // Both charging
        create<DualApplePods>("07 19 01 0E 20 55 88 >B4< 59 00 05 4B FC DF 68 28 A5 45 52 65 9C FE 51 86 3A B5 DB") {
            // 1011 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
        }
        // Both not charging
        create<DualApplePods>("07 19 01 0E 20 00 F8 >8F< 03 00 05 4C 0F A0 C4 05 24 DD EB AF 92 99 FD 54 B1 06 48") {
            // 1000 1111
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - case charging`() = runBlockingTest {
        create<DualApplePods>("07 19 01 0E 20 75 99 >B4< 31 00 05 77 C8 BA 0C 4E 1F BE AD 70 C5 40 71 D2 E9 17 A2") {
            // 0011 0011
            isCaseCharging shouldBe false
        }
        create<DualApplePods>("07 19 01 0E 20 75 A9 >F4< 51 00 05 A0 37 92 35 49 79 CC DC 27 94 8E FB 72 12 94 52") {
            // 0101 0011
            isCaseCharging shouldBe true
        }
    }

    @Test
    fun `test AirPodDevice - case lid test`() = runBlockingTest {
        // Lid open
        create<DualApplePods>("07 19 01 0E 20 55 AA B4 >31< 00 00 A1 D0 BD 82 D3 52 86 CA FC 11 62 DC 42 C6 92 8E") {
            // 31 0011 0001
            caseLidState shouldBe DualApplePods.LidState.OPEN
        }
        // Lid just closed
        create<DualApplePods>("07 19 01 0E 20 55 AA B4 >39< 00 00 08 A6 DB 99 E0 5E 14 85 E5 C2 0B 68 D7 FF C3 A1") {
            // 39 0011 1001
            caseLidState shouldBe DualApplePods.LidState.UNKNOWN
        }
        // Lid closed
        create<DualApplePods>("07 19 01 0E 20 55 AA B4 38 00 00 F3 F7 08 3B 98 09 C0 DD E4 BD BD 84 55 56 8B 81") {
            // 38 0011 1000
            caseLidState shouldBe DualApplePods.LidState.CLOSED
        }
    }

    @Test
    fun `test AirPodDevice - connection state`() = runBlockingTest {
        // Disconnected
        create<DualApplePods>("07 19 01 0E 20 2B AA 8F 01 00 >00< 62 D4 BB F1 A7 F8 64 98 D2 C8 BD 7B 3A EF 2E 15") {
            // 31 0011 0001
            connectionState shouldBe DualApplePods.ConnectionState.DISCONNECTED
        }
        // Connected idle
        create<DualApplePods>("07 19 01 0E 20 2B AA 8F 01 00 >04< 1D 69 69 9C C2 51 F3 1F BF 6E 45 DA 90 4A A3 E3") {
            // 39 0011 1001
            connectionState shouldBe DualApplePods.ConnectionState.IDLE
        }
        // Connected and playing music
        create<DualApplePods>("07 19 01 0E 20 2B A9 8F 01 00 >05< 14 F7 CB 49 9F D3 B3 22 77 D2 22 F1 74 8C AC A6") {
            // 38 0011 1000
            connectionState shouldBe DualApplePods.ConnectionState.MUSIC
        }
        // Connected and call active
        create<DualApplePods>("07 19 01 0E 20 2B 99 8F 01 00 >06< 0F 4B 43 25 E0 4A 73 63 14 22 C2 3C 89 13 BD 97") {
            // 38 0011 1000
            connectionState shouldBe DualApplePods.ConnectionState.CALL
        }
        // Connected and call active
        create<DualApplePods>("07 19 01 0E 20 2B 99 8F 01 00 >07< E7 DF 76 44 85 B5 30 F4 95 14 02 DC A1 A4 8A 09") {
            // 38 0011 1000
            connectionState shouldBe DualApplePods.ConnectionState.RINGING
        }
        // Switching?
        create<DualApplePods>("07 19 01 0E 20 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            // 38 0011 1000
            connectionState shouldBe DualApplePods.ConnectionState.HANGING_UP
        }
    }

    @Test
    fun `create AirPodsGen1`() = runBlockingTest {
        create<DualApplePods>("07 19 01 02 20 55 AA 56 31 00 00 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88") {
            this shouldBe instanceOf<AirPodsGen1>()
        }
    }

    @Test
    fun `create AirPodsPro`() = runBlockingTest {
        create<DualApplePods>("07 19 01 0E 20 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            this shouldBe instanceOf<AirPodsPro>()
        }
    }

    @Test
    fun `unknown AppleDevice`() = runBlockingTest {
        create<ApplePods>("07 19 01 FF FF 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            this shouldBe instanceOf<UnknownAppleDevice>()
        }
    }

    @Test
    fun `invalid data`() = runBlockingTest {
        create<PodDevice?>("abcd") {
            this shouldBe null
        }
    }
}