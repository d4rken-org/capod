package eu.darken.capod.pods.core.apple.ble

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.DeviceProfile
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import java.util.UUID

class BlePodSnapshotTest : BaseTest() {

    private fun fakeSnapshot(
        rssi: Int,
        reliability: Float = 0f,
        seenFirstAt: Instant = Instant.now(),
    ): BlePodSnapshot = object : BlePodSnapshot {
        override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(UUID.randomUUID())
        override val model: PodModel = PodModel.UNKNOWN
        override val seenLastAt: Instant = seenFirstAt
        override val seenFirstAt: Instant = seenFirstAt
        override val seenCounter: Int = 1
        override val scanResult: BleScanResult = BleScanResult(
            receivedAt = seenFirstAt,
            address = "AA:BB:CC:DD:EE:FF",
            rssi = rssi,
            generatedAtNanos = 0L,
            manufacturerSpecificData = emptyMap(),
        )
        override val reliability: Float = reliability
        override val meta: BlePodSnapshot.Meta = object : BlePodSnapshot.Meta {
            override val profile: DeviceProfile? = null
        }
    }

    // --- rssiQuality — pure RSSI mapping for display bars ---

    @Test
    fun `rssiQuality at -30 dBm maps to 1_0 (excellent, 4 bars)`() {
        fakeSnapshot(rssi = -30).rssiQuality shouldBe 1.0f
    }

    @Test
    fun `rssiQuality at -50 dBm maps to ~0_71 (3 bars)`() {
        fakeSnapshot(rssi = -50).rssiQuality shouldBe (0.714f plusOrMinus 0.01f)
    }

    @Test
    fun `rssiQuality at -65 dBm maps to 0_50 (2 bars)`() {
        fakeSnapshot(rssi = -65).rssiQuality shouldBe (0.50f plusOrMinus 0.01f)
    }

    @Test
    fun `rssiQuality at -85 dBm maps to ~0_21 (1 bar)`() {
        fakeSnapshot(rssi = -85).rssiQuality shouldBe (0.214f plusOrMinus 0.01f)
    }

    @Test
    fun `rssiQuality at -100 dBm maps to 0_0 (edge of range)`() {
        fakeSnapshot(rssi = -100).rssiQuality shouldBe 0.0f
    }

    @Test
    fun `rssiQuality clamps lower bound for weaker than -100 dBm`() {
        fakeSnapshot(rssi = -150).rssiQuality shouldBe 0.0f
    }

    @Test
    fun `rssiQuality clamps upper bound for stronger than -30 dBm`() {
        fakeSnapshot(rssi = 0).rssiQuality shouldBe 1.0f
        fakeSnapshot(rssi = -10).rssiQuality shouldBe 1.0f
    }

    // --- signalQuality — composite for matching/sorting ---
    // Formula: (sqRssi + max(BASE, reliability) + sqAge) / 2
    // BASE_CONFIDENCE = 0, age=0 for fresh snapshots

    @Test
    fun `signalQuality at -30 dBm cold start (reliability=0, age=0) equals rssiQuality half`() {
        // (1.0 + 0 + 0) / 2 = 0.5
        fakeSnapshot(rssi = -30, reliability = 0f).signalQuality shouldBe (0.5f plusOrMinus 0.01f)
    }

    @Test
    fun `signalQuality at -100 dBm cold start is 0`() {
        // (0 + 0 + 0) / 2 = 0
        fakeSnapshot(rssi = -100, reliability = 0f).signalQuality shouldBe 0.0f
    }

    @Test
    fun `signalQuality at -30 dBm established (reliability=0_85) approaches 0_925`() {
        // (1.0 + 0.85 + 0) / 2 = 0.925
        fakeSnapshot(rssi = -30, reliability = 0.85f).signalQuality shouldBe (0.925f plusOrMinus 0.01f)
    }

    @Test
    fun `signalQuality at -50 dBm established (reliability=0_85) approaches 0_78`() {
        // (0.714 + 0.85 + 0) / 2 = 0.782
        fakeSnapshot(rssi = -50, reliability = 0.85f).signalQuality shouldBe (0.782f plusOrMinus 0.01f)
    }

    @Test
    fun `signalQuality at -100 dBm with reliability 0 stays below AppleFactory default 0_15 threshold`() {
        // Verify filter behavior: a weak-signal fresh scan is still filtered out
        val q = fakeSnapshot(rssi = -100, reliability = 0f).signalQuality
        (q < 0.15f) shouldBe true
    }
}
