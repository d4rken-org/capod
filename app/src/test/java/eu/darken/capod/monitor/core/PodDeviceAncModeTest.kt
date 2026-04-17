package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
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

    // -- PodDevice.visibleAncModes extension: the null-coalesce lives here --

    private fun deviceWith(
        allowOffSetting: AapSetting.AllowOffOption? = null,
        learnedAllowOffEnabled: Boolean? = null,
        currentMode: AapSetting.AncMode.Value = AapSetting.AncMode.Value.ON,
    ): PodDevice {
        val ancSetting = AapSetting.AncMode(current = currentMode, supported = allModes)
        val settings: Map<kotlin.reflect.KClass<out AapSetting>, AapSetting> = buildMap {
            put(AapSetting.AncMode::class, ancSetting)
            if (allowOffSetting != null) put(AapSetting.AllowOffOption::class, allowOffSetting)
        }
        return PodDevice(
            profileId = null,
            ble = null,
            aap = AapPodState(settings = settings),
            profileModel = PodModel.AIRPODS_PRO,
            profileLearnedAllowOffEnabled = learnedAllowOffEnabled,
        )
    }

    @Test
    fun `unknown AllowOffOption is treated as allowed — OFF visible by default`() {
        val device = deviceWith(allowOffSetting = null, learnedAllowOffEnabled = null)
        device.visibleAncModes shouldContainExactly allModes
    }

    @Test
    fun `confirmed AllowOffOption=false hides OFF`() {
        val device = deviceWith(
            allowOffSetting = AapSetting.AllowOffOption(enabled = false),
            learnedAllowOffEnabled = null,
        )
        device.visibleAncModes shouldContainExactly listOf(
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
            AapSetting.AncMode.Value.ADAPTIVE,
        )
    }

    @Test
    fun `profile-learned AllowOffEnabled=false acts as fallback and hides OFF`() {
        val device = deviceWith(allowOffSetting = null, learnedAllowOffEnabled = false)
        device.visibleAncModes shouldContainExactly listOf(
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
            AapSetting.AncMode.Value.ADAPTIVE,
        )
    }

    @Test
    fun `live AAP AllowOffOption overrides profile fallback`() {
        val device = deviceWith(
            allowOffSetting = AapSetting.AllowOffOption(enabled = true),
            learnedAllowOffEnabled = false,
        )
        device.visibleAncModes shouldContainExactly allModes
    }

    @Test
    fun `profile-learned ListeningModeCycle mask is used when AAP state has none`() {
        val ancSetting = AapSetting.AncMode(current = AapSetting.AncMode.Value.ON, supported = allModes)
        val device = PodDevice(
            profileId = null,
            ble = null,
            aap = AapPodState(settings = mapOf(AapSetting.AncMode::class to ancSetting)),
            profileModel = PodModel.AIRPODS_PRO,
            profileLearnedAllowOffEnabled = true,
            profileLastRequestedListeningModeCycleMask = 0x0F,
        )
        device.listeningModeCycle?.modeMask shouldBe 0x0F
        device.resolvedAncCycleMask shouldBe 0x0F
    }

    @Test
    fun `live AAP ListeningModeCycle overrides profile fallback`() {
        val ancSetting = AapSetting.AncMode(current = AapSetting.AncMode.Value.ON, supported = allModes)
        val device = PodDevice(
            profileId = null,
            ble = null,
            aap = AapPodState(
                settings = mapOf(
                    AapSetting.AncMode::class to ancSetting,
                    AapSetting.ListeningModeCycle::class to AapSetting.ListeningModeCycle(modeMask = 0x0A),
                ),
            ),
            profileModel = PodModel.AIRPODS_PRO,
            profileLastRequestedListeningModeCycleMask = 0x0F,
        )
        device.listeningModeCycle?.modeMask shouldBe 0x0A
    }
}
