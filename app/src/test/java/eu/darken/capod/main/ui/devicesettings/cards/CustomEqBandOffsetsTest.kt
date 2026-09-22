package eu.darken.capod.main.ui.devicesettings.cards

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CustomEqBandOffsetsTest : BaseTest() {

    private fun eq(low: Int, mid: Int, high: Int) = AapSetting.CustomEq(
        enabled = true,
        low = low,
        mid = mid,
        high = high,
    )

    @Test fun `neutral is 50`() {
        CUSTOM_EQ_NEUTRAL shouldBe 50
    }

    @Test fun `flat eq has no deviating bands`() {
        customEqBandOffsets(eq(50, 50, 50)).shouldContainExactly(emptyList())
    }

    @Test fun `offsets are signed distances from neutral`() {
        customEqBandOffsets(eq(62, 50, 42)).shouldContainExactly(
            CustomEqBand.LOW to 12,
            CustomEqBand.HIGH to -8,
        )
    }

    @Test fun `neutral bands are omitted`() {
        customEqBandOffsets(eq(50, 70, 50)).shouldContainExactly(CustomEqBand.MID to 20)
    }

    @Test fun `band order is low mid high`() {
        customEqBandOffsets(eq(0, 100, 1)).shouldContainExactly(
            CustomEqBand.LOW to -50,
            CustomEqBand.MID to 50,
            CustomEqBand.HIGH to -49,
        )
    }

    @Test fun `disabled eq still reports its bands`() {
        val disabled = AapSetting.CustomEq(enabled = false, low = 60, mid = 50, high = 50)
        customEqBandOffsets(disabled).shouldContainExactly(CustomEqBand.LOW to 10)
    }
}
