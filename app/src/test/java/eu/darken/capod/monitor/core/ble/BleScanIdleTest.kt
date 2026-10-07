package eu.darken.capod.monitor.core.ble

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BleScanIdleTest {
    @Test
    fun `scanning requires permissions Bluetooth and either foreground UI or connected Pods`() {
        shouldScanForPods(true, true, false, false) shouldBe false
        shouldScanForPods(true, true, false, false, retainBackgroundScan = true) shouldBe true
        shouldScanForPods(true, true, false, true) shouldBe true
        shouldScanForPods(true, true, true, false) shouldBe true
        shouldScanForPods(false, true, true, true) shouldBe false
        shouldScanForPods(true, false, true, true) shouldBe false
    }
}
