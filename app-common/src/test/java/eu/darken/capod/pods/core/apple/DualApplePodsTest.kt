package eu.darken.capod.pods.core.apple

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DualApplePodsTest : BaseAirPodsTest() {

    @Test
    fun `test bit mapping`() = runTest {
        create<DualAirPods>("07 19 01 0E 20 54 AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {

            rawPrefix shouldBe 0x01.toUByte()
            rawDeviceModel shouldBe 0x0e20.toUShort()
            rawStatus shouldBe 0x54.toUByte()
            rawPodsBattery shouldBe 0xAA.toUByte()
            rawFlags shouldBe 0xB.toUShort()
            rawCaseBattery shouldBe 0x5.toUShort()
            rawCaseLidState shouldBe 0x31.toUByte()
            rawDeviceColor shouldBe 0x00.toUByte()
            rawSuffix shouldBe 0x00.toUByte()
        }
    }

    @Test
    fun `test AirPodDevice - active microphone`() = runTest {
        create<DualAirPods>("07 19 01 0E 20 >2B< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00101011
            // --^-----
            isLeftPodMicrophone shouldBe true
            isRightPodMicrophone shouldBe false
        }
        create<DualAirPods>("07 19 01 0E 20 >0B< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00001011
            // --^-----
            isLeftPodMicrophone shouldBe false
            isRightPodMicrophone shouldBe true
        }
    }

    @Test
    fun `test AirPodDevice - left pod ear status`() = runTest {
        // Left Pod primary
        create<DualAirPods>("07 19 01 0E 20 >22< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100010
            // 765432¹0
            isLeftPodInEar shouldBe true
        }
        create<DualAirPods>("07 19 01 0E 20 >20< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100000
            // 765432¹0
            isLeftPodInEar shouldBe false
        }

        // Right Pod is primary
        create<DualAirPods>("07 19 01 0E 20 >09< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00001001
            // 7654³210
            isLeftPodInEar shouldBe true
        }
        create<DualAirPods>("07 19 01 0E 20 >20< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000001
            // 7654³210
            isLeftPodInEar shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - right pod ear status`() = runTest {
        // Left Pod primary
        create<DualAirPods>("07 19 01 0E 20 >29< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00101001
            // 7654³210
            isRightPodInEar shouldBe true
        }
        create<DualAirPods>("07 19 01 0E 20 >21< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00100001
            // 7654³210
            isRightPodInEar shouldBe false
        }

        // Right Pod is primary
        create<DualAirPods>("07 19 01 0E 20 >03< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000011
            // 765432¹0
            isRightPodInEar shouldBe true
        }
        create<DualAirPods>("07 19 01 0E 20 >01< AA B5 31 00 00 E0 0C A7 8A 60 4B D3 7D F4 60 4F 2C 73 E9 A7 F4") {
            // 00000001
            // 765432¹0
            isRightPodInEar shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - battery status`() = runTest {
        // Right Pod is primary
        create<DualAirPods>("07 19 01 0E 20 0B >98< 94 52 00 05 09 73 3C 3D F9 2C 3E B3 DD 76 02 DD 4E 16 FD FB") {
            // 88 10001000
            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.8f
        }
        // Left Pod primary
        create<DualAirPods>("07 19 01 0E 20 2B >89< 94 52 00 05 09 73 3C 3D F9 2C 3E B3 DD 76 02 DD 4E 16 FD FB") {
            // F8 11111000
            batteryLeftPodPercent shouldBe 0.9f
            batteryRightPodPercent shouldBe 0.8f
        }
    }

    @Test
    fun `test AirPodDevice - pod charging`() = runTest {
        /**
         * Right pod is charging
         */
        // This is the left
        create<DualAirPods>("07 19 01 0E 20 51 89 >94< 52 00 00 F4 89 82 6D 3E 27 7F 26 62 57 D0 E2 A6 49 E9 35") {
            // 1001 0100
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true
        }
        // This is the right
        create<DualAirPods>("07 19 01 0E 20 31 98 >A4< 01 00 00 31 B9 A0 C4 80 CD D1 CF B9 3A 9A 6D 48 31 08 EB") {
            // 1010 0100
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe true
        }

        /**
         * Left pod is charging
         */
        // This is the left
        create<DualAirPods>("07 19 01 0E 20 71 98 >94< 52 00 05 A5 37 31 B2 BD 42 68 0C 64 FD 00 99 4A E5 3E F4") {
            // 1001 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe false
        }
        // This is the right
        create<DualAirPods>("07 19 01 0E 20 11 89 >A4< 04 00 04 BA 79 1B C0 65 69 C6 9F 19 6E 37 7D 6D 86 8D D9") {
            // 1010 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe false
        }

        // Both charging
        create<DualAirPods>("07 19 01 0E 20 55 88 >B4< 59 00 05 4B FC DF 68 28 A5 45 52 65 9C FE 51 86 3A B5 DB") {
            // 1011 0100
            isLeftPodCharging shouldBe true
            isRightPodCharging shouldBe true
        }
        // Both not charging
        create<DualAirPods>("07 19 01 0E 20 00 F8 >8F< 03 00 05 4C 0F A0 C4 05 24 DD EB AF 92 99 FD 54 B1 06 48") {
            // 1000 1111
            isLeftPodCharging shouldBe false
            isRightPodCharging shouldBe false
        }
    }

    @Test
    fun `test AirPodDevice - case charging`() = runTest {
        create<DualAirPods>("07 19 01 0E 20 75 99 >B4< 31 00 05 77 C8 BA 0C 4E 1F BE AD 70 C5 40 71 D2 E9 17 A2") {
            // 0011 0011
            isCaseCharging shouldBe false
        }
        create<DualAirPods>("07 19 01 0E 20 75 A9 >F4< 51 00 05 A0 37 92 35 49 79 CC DC 27 94 8E FB 72 12 94 52") {
            // 0101 0011
            isCaseCharging shouldBe true
        }
    }

    @Test
    fun `test AirPodDevice - case lid test`() = runTest {
        // Lid open
        create<DualAirPods>("07 19 01 0E 20 55 AA B4 >31< 00 00 A1 D0 BD 82 D3 52 86 CA FC 11 62 DC 42 C6 92 8E") {
            // 31 0011 0001
            caseLidState shouldBe DualAirPods.LidState.OPEN
        }
        // Lid open, left pod in case
        create<DualAirPods>("07 19 01 0E 20 51 9A 93 >31< 00 00 95 D0 A5 D7 E3 F4 F1 38 38 99 61 3B 57 95 37 B7") {
            // 31 0011 0001
            caseLidState shouldBe DualAirPods.LidState.OPEN
        }
        // Lid open, left pod in case
        create<DualAirPods>("07 19 01 0E 20 71 A9 92 31 00 00 DB 48 7F 32 8C CE 80 6F D9 27 98 D6 76 45 9D 62") {
            // 31 0011 0001
            caseLidState shouldBe DualAirPods.LidState.OPEN
        }


        // Lid just closed
        create<DualAirPods>("07 19 01 0E 20 55 AA B4 >39< 00 00 08 A6 DB 99 E0 5E 14 85 E5 C2 0B 68 D7 FF C3 A1") {
            // 39 0011 1001
            caseLidState shouldBe DualAirPods.LidState.CLOSED
        }
        // Lid closed
        create<DualAirPods>("07 19 01 0E 20 55 AA B4 38 00 00 F3 F7 08 3B 98 09 C0 DD E4 BD BD 84 55 56 8B 81") {
            // 38 0011 1000
            caseLidState shouldBe DualAirPods.LidState.CLOSED
        }
        // Lid closed, right pod in case
        create<DualAirPods>("07 19 01 0E 20 51 9A 93 >38< 00 00 3A D8 85 76 B0 91 48 31 DA FF 6C 4A 2B C2 67 F4") {
            // 38 0011 1000
            caseLidState shouldBe DualAirPods.LidState.CLOSED
        }
        // Lid closed, left pod in case
        create<DualAirPods>("07 19 01 0E 20 71 A9 92 38 00 00 44 91 C4 8B 85 98 DD 55 4E 6A CA BC B5 CA 8D 37") {
            // 38 0011 1000
            caseLidState shouldBe DualAirPods.LidState.CLOSED
        }
    }

    @Test
    fun `test AirPodDevice - connection state`() = runTest {
        // Disconnected
        create<DualAirPods>("07 19 01 0E 20 2B AA 8F 01 00 >00< 62 D4 BB F1 A7 F8 64 98 D2 C8 BD 7B 3A EF 2E 15") {
            // 31 0011 0001
            state shouldBe DualAirPods.ConnectionState.DISCONNECTED
        }
        // Connected idle
        create<DualAirPods>("07 19 01 0E 20 2B AA 8F 01 00 >04< 1D 69 69 9C C2 51 F3 1F BF 6E 45 DA 90 4A A3 E3") {
            // 39 0011 1001
            state shouldBe DualAirPods.ConnectionState.IDLE
        }
        // Connected and playing music
        create<DualAirPods>("07 19 01 0E 20 2B A9 8F 01 00 >05< 14 F7 CB 49 9F D3 B3 22 77 D2 22 F1 74 8C AC A6") {
            // 38 0011 1000
            state shouldBe DualAirPods.ConnectionState.MUSIC
        }
        // Connected and call active
        create<DualAirPods>("07 19 01 0E 20 2B 99 8F 01 00 >06< 0F 4B 43 25 E0 4A 73 63 14 22 C2 3C 89 13 BD 97") {
            // 38 0011 1000
            state shouldBe DualAirPods.ConnectionState.CALL
        }
        // Connected and call active
        create<DualAirPods>("07 19 01 0E 20 2B 99 8F 01 00 >07< E7 DF 76 44 85 B5 30 F4 95 14 02 DC A1 A4 8A 09") {
            // 38 0011 1000
            state shouldBe DualAirPods.ConnectionState.RINGING
        }
        // Switching?
        create<DualAirPods>("07 19 01 0E 20 2B 99 8F 01 00 >09< 10 30 EE F3 41 B5 D8 9F A3 B0 B4 17 9F 85 97 5F") {
            // 38 0011 1000
            state shouldBe DualAirPods.ConnectionState.HANGING_UP
        }
    }

}