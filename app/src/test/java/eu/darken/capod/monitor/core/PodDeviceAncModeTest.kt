package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PodDeviceAncModeTest : BaseTest() {

    private val allModes = listOf(
        AapSetting.AncMode.Value.OFF,
        AapSetting.AncMode.Value.ON,
        AapSetting.AncMode.Value.TRANSPARENCY,
        AapSetting.AncMode.Value.ADAPTIVE,
    )

    @Test
    fun `cycle mask hides OFF when OFF is not allowed`() {
        visibleAncModes(
            supportedModes = allModes,
            currentMode = AapSetting.AncMode.Value.ON,
            cycleMask = 0x0E,
            allowOffEnabled = false,
        ) shouldContainExactly listOf(
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
            AapSetting.AncMode.Value.ADAPTIVE,
        )
    }

    @Test
    fun `allow off keeps OFF visible even when cycle mask excludes it`() {
        visibleAncModes(
            supportedModes = allModes,
            currentMode = AapSetting.AncMode.Value.ON,
            cycleMask = 0x0E,
            allowOffEnabled = true,
        ) shouldContainExactly allModes
    }

    @Test
    fun `current OFF stays visible even when OFF is otherwise hidden`() {
        visibleAncModes(
            supportedModes = allModes,
            currentMode = AapSetting.AncMode.Value.OFF,
            cycleMask = 0x0E,
            allowOffEnabled = false,
        ) shouldContainExactly allModes
    }

    @Test
    fun `null cycle mask shows all supported modes`() {
        visibleAncModes(
            supportedModes = allModes,
            currentMode = AapSetting.AncMode.Value.ON,
            cycleMask = null,
            allowOffEnabled = false,
        ) shouldContainExactly allModes
    }

    @Test
    fun `cycle mask with OFF bit set includes OFF`() {
        visibleAncModes(
            supportedModes = allModes,
            currentMode = AapSetting.AncMode.Value.ON,
            cycleMask = 0x0F,
            allowOffEnabled = false,
        ) shouldContainExactly allModes
    }

    @Test
    fun `models with listening mode cycle fall back to default mask when setting is absent`() {
        resolvedAncCycleMask(
            hasListeningModeCycle = true,
            reportedCycleMask = null,
        ) shouldBe 0x0E
    }

    @Test
    fun `models without listening mode cycle keep cycle mask null`() {
        resolvedAncCycleMask(
            hasListeningModeCycle = false,
            reportedCycleMask = null,
        ) shouldBe null
    }

    @Test
    fun `reported cycle mask wins over fallback`() {
        resolvedAncCycleMask(
            hasListeningModeCycle = true,
            reportedCycleMask = 0x0A,
        ) shouldBe 0x0A
    }
}
