package eu.darken.capod.common.bluetooth

import dagger.Reusable
import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.debug.autoreport.DebugSettings
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random

@Reusable
class FakeBleData @Inject constructor(
    private val debugSettings: DebugSettings,
) {

    fun maybeAddfakeData(originals: List<BleScanResult>): List<BleScanResult> {
        if (!debugSettings.showFakeData.value) return originals
        return originals + getFakeData()
    }

    fun getFakeData(): Collection<BleScanResult> {
        val fakeDevices = mutableListOf<BleScanResult>()
        // AirPods Gen1
        BleScanResult(
            receivedAt = Instant.now(),
            address = "78:73:AF:B4:85:22",
            rssi = Random.nextInt(100) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 100,
            manufacturerSpecificData = mapOf(76 to "07 19 01 02 20 75 AA B6 31 00 05 9C 5A A4 5D C0 2C A0 B4 6F B9 ED 8E CE 03 97 CA".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }

        // AirPods Gen2
        BleScanResult(
            receivedAt = Instant.now(),
            address = "78:73:FF:B4:85:5E",
            rssi = Random.nextInt(100) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 100,
            manufacturerSpecificData = mapOf(76 to "07 19 01 0F 20 75 AA B6 31 00 05 9C 5A A4 5D C0 2C A0 B4 6F B9 ED 8E CE 03 97 CA".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }

        // AirPods Gen3
        BleScanResult(
            receivedAt = Instant.now(),
            address = "4E:9E:D1:49:D2:6D",
            rssi = Random.nextInt(15, 75) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 200,
            manufacturerSpecificData = mapOf(76 to "07 19 01 13 20 55 AF 56 31 00 06 6F E4 DF 10 AF 10 60 81 03 3B 76 D9 C7 11 22 88".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }
        // AirPods Max
        BleScanResult(
            receivedAt = Instant.now(),
            address = "7E:E5:C7:65:D2:B5",
            rssi = Random.nextInt(15, 75) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 300,
            manufacturerSpecificData = mapOf(76 to "07 19 01 0A 20 02 05 80 04 0F 44 A7 60 9B F8 3C FD B1 D8 1C 61 EA 82 60 A3 2C 4E".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }
        // BeatsFlex
        BleScanResult(
            receivedAt = Instant.now(),
            address = "5E:9E:D1:49:D2:6D",
            rssi = Random.nextInt(15, 75) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 400,
            manufacturerSpecificData = mapOf(76 to "07 19 01 10 20 0A F4 8F 00 01 00 C4 71 9F 9C EF A2 E3 BA 66 FE 1D 45 9F C9 2F A0".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }

        // Tws i99999
        BleScanResult(
            receivedAt = Instant.now(),
            address = "5E:9E:D1:29:D2:6D",
            rssi = Random.nextInt(15, 75) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 400,
            manufacturerSpecificData = mapOf(76 to "07 13 01 02 20 71 AA 37 32 00 10 00 64 64 FF 00 00 00 00 00 00".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }

        // Unknown Device
        BleScanResult(
            receivedAt = Instant.now(),
            address = "6E:9E:D1:49:D2:6D",
            rssi = Random.nextInt(15, 75) * -1,
            generatedAtNanos = SystemClockWrap.elapsedRealtimeNanos + 500,
            manufacturerSpecificData = mapOf(76 to "07 19 01 FF 20 0A F4 8F 00 01 00 C4 71 9F 9C EF A2 E3 BA 66 FE 1D 45 9F C9 2F A0".hexToByteArray())
        ).run {
            if (Random.nextBoolean()) {
                fakeDevices.add(this)
            }
        }

        return fakeDevices
    }


    private fun String.hexToByteArray(): ByteArray {
        val trimmed = this
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        return trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

}