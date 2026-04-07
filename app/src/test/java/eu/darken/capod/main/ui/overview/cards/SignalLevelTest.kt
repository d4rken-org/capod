package eu.darken.capod.main.ui.overview.cards

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SignalLevelTest : BaseTest() {

    @Test
    fun `not live always returns DISCONNECTED regardless of quality`() {
        signalLevelOf(signalQuality = 1.0f, isLive = false) shouldBe SignalLevel.DISCONNECTED
        signalLevelOf(signalQuality = 0.5f, isLive = false) shouldBe SignalLevel.DISCONNECTED
        signalLevelOf(signalQuality = 0.0f, isLive = false) shouldBe SignalLevel.DISCONNECTED
        signalLevelOf(signalQuality = Float.NaN, isLive = false) shouldBe SignalLevel.DISCONNECTED
    }

    @Test
    fun `exact boundaries map upward`() {
        signalLevelOf(signalQuality = 0.20f, isLive = true) shouldBe SignalLevel.BARS_1
        signalLevelOf(signalQuality = 0.40f, isLive = true) shouldBe SignalLevel.BARS_2
        signalLevelOf(signalQuality = 0.60f, isLive = true) shouldBe SignalLevel.BARS_3
        signalLevelOf(signalQuality = 0.80f, isLive = true) shouldBe SignalLevel.BARS_4
    }

    @Test
    fun `just below boundaries fall into lower bucket`() {
        signalLevelOf(signalQuality = 0.199f, isLive = true) shouldBe SignalLevel.BARS_0
        signalLevelOf(signalQuality = 0.399f, isLive = true) shouldBe SignalLevel.BARS_1
        signalLevelOf(signalQuality = 0.599f, isLive = true) shouldBe SignalLevel.BARS_2
        signalLevelOf(signalQuality = 0.799f, isLive = true) shouldBe SignalLevel.BARS_3
    }

    @Test
    fun `zero quality maps to BARS_0`() {
        signalLevelOf(signalQuality = 0.0f, isLive = true) shouldBe SignalLevel.BARS_0
    }

    @Test
    fun `full quality maps to BARS_4`() {
        signalLevelOf(signalQuality = 1.0f, isLive = true) shouldBe SignalLevel.BARS_4
    }

    @Test
    fun `non-finite live inputs fall back to BARS_0`() {
        signalLevelOf(signalQuality = Float.NaN, isLive = true) shouldBe SignalLevel.BARS_0
        signalLevelOf(signalQuality = Float.POSITIVE_INFINITY, isLive = true) shouldBe SignalLevel.BARS_0
        signalLevelOf(signalQuality = Float.NEGATIVE_INFINITY, isLive = true) shouldBe SignalLevel.BARS_0
    }

    @Test
    fun `out of range values are clamped`() {
        signalLevelOf(signalQuality = -0.5f, isLive = true) shouldBe SignalLevel.BARS_0
        signalLevelOf(signalQuality = -0.001f, isLive = true) shouldBe SignalLevel.BARS_0
        signalLevelOf(signalQuality = 1.7f, isLive = true) shouldBe SignalLevel.BARS_4
        signalLevelOf(signalQuality = 99f, isLive = true) shouldBe SignalLevel.BARS_4
    }
}
