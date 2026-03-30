package eu.darken.capod.pods.core.apple.protocol.aap

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AapPodStateTest : BaseTest() {

    @Test
    fun `setting lookup returns typed setting`() {
        val state = AapPodState(
            connectionState = AapConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(AncModeValue.ON, listOf(AncModeValue.ON, AncModeValue.TRANSPARENCY)),
            )
        )
        val anc = state.setting<AapSetting.AncMode>()
        anc.shouldNotBeNull()
        anc.current shouldBe AncModeValue.ON
    }

    @Test
    fun `setting lookup returns null for missing type`() {
        val state = AapPodState(connectionState = AapConnectionState.READY)
        state.setting<AapSetting.AncMode>().shouldBeNull()
    }

    @Test
    fun `withSetting merges into existing settings`() {
        val state = AapPodState(
            connectionState = AapConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(AncModeValue.ON, listOf(AncModeValue.ON)),
            )
        )
        val updated = state.withSetting(
            AapSetting.ConversationalAwareness::class,
            AapSetting.ConversationalAwareness(true),
        )
        // Original setting preserved
        updated.setting<AapSetting.AncMode>().shouldNotBeNull()
        // New setting added
        updated.setting<AapSetting.ConversationalAwareness>()!!.enabled shouldBe true
    }

    @Test
    fun `withSetting replaces existing setting of same type`() {
        val state = AapPodState(
            connectionState = AapConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(AncModeValue.ON, listOf(AncModeValue.ON)),
            )
        )
        val updated = state.withSetting(
            AapSetting.AncMode::class,
            AapSetting.AncMode(AncModeValue.TRANSPARENCY, listOf(AncModeValue.ON, AncModeValue.TRANSPARENCY)),
        )
        updated.setting<AapSetting.AncMode>()!!.current shouldBe AncModeValue.TRANSPARENCY
        updated.settings.size shouldBe 1
    }

    @Test
    fun `default state is disconnected with no data`() {
        val state = AapPodState()
        state.connectionState shouldBe AapConnectionState.DISCONNECTED
        state.deviceInfo.shouldBeNull()
        state.settings shouldBe emptyMap()
    }
}
