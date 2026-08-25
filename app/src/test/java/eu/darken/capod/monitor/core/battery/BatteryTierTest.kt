package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.ble.BATTERY_UNKNOWN
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BatteryTierTest : BaseTest() {

    @Test
    fun `unknown battery has no tier`() {
        batteryTier(BATTERY_UNKNOWN) shouldBe BatteryTier.UNKNOWN
        batteryTier(-0.01f) shouldBe BatteryTier.UNKNOWN
        batteryTier(-100f) shouldBe BatteryTier.UNKNOWN
    }

    @Test
    fun `non-finite values have no tier`() {
        batteryTier(Float.NaN) shouldBe BatteryTier.UNKNOWN
        batteryTier(Float.POSITIVE_INFINITY) shouldBe BatteryTier.UNKNOWN
        batteryTier(Float.NEGATIVE_INFINITY) shouldBe BatteryTier.UNKNOWN
    }

    @Test
    fun `values at and above full are good`() {
        batteryTier(1f) shouldBe BatteryTier.GOOD
        batteryTier(1.5f) shouldBe BatteryTier.GOOD
        batteryTier(Float.MAX_VALUE) shouldBe BatteryTier.GOOD
    }

    @Test
    fun `the good boundary is exclusive`() {
        batteryTier(0.30f) shouldBe BatteryTier.WARN
        batteryTier(0.30f + 0.0001f) shouldBe BatteryTier.GOOD
        batteryTier(0.30f - 0.0001f) shouldBe BatteryTier.WARN
    }

    @Test
    fun `the warn boundary is inclusive`() {
        batteryTier(0.15f) shouldBe BatteryTier.WARN
        batteryTier(0.15f + 0.0001f) shouldBe BatteryTier.WARN
        batteryTier(0.15f - 0.0001f) shouldBe BatteryTier.CRITICAL
    }

    @Test
    fun `an empty battery is critical`() {
        batteryTier(0f) shouldBe BatteryTier.CRITICAL
        batteryTier(0.05f) shouldBe BatteryTier.CRITICAL
    }
}
