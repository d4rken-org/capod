package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PodDeviceTierTest : BaseTest() {

    private fun device(
        profileId: String?,
        isSystemConnected: Boolean = false,
        isLive: Boolean = false,
    ): PodDevice {
        // isLive is derived from ble != null || aap != null. Use a minimal AapPodState so we
        // don't need to fabricate a BLE snapshot for the live case.
        val aap = if (isLive) AapPodState() else null
        return PodDevice(
            profileId = profileId,
            ble = null,
            aap = aap,
            profileModel = PodModel.AIRPODS_PRO,
            isSystemConnected = isSystemConnected,
        )
    }

    @Test
    fun `system connected ranks above live which ranks above offline`() {
        device(profileId = "a", isSystemConnected = true).tierRank() shouldBe 0
        device(profileId = "a", isLive = true).tierRank() shouldBe 1
        device(profileId = "a").tierRank() shouldBe 2
    }

    @Test
    fun `system connected wins regardless of live`() {
        // isSystemConnected true takes precedence even if also live
        device(profileId = "a", isSystemConnected = true, isLive = true).tierRank() shouldBe 0
    }

    @Test
    fun `primaryByTier picks system-connected device first`() {
        val live = device(profileId = "a", isLive = true)
        val systemConnected = device(profileId = "b", isSystemConnected = true)
        val devices = listOf(live, systemConnected)
        devices.primaryByTier(profileOrder = mapOf("a" to 0, "b" to 1)) shouldBe systemConnected
    }

    @Test
    fun `primaryByTier uses profile order as tiebreaker within a tier`() {
        val first = device(profileId = "a", isLive = true)
        val second = device(profileId = "b", isLive = true)
        val devices = listOf(second, first)
        devices.primaryByTier(profileOrder = mapOf("a" to 0, "b" to 1)) shouldBe first
    }

    @Test
    fun `primaryByTier ignores profileless devices`() {
        val anonymous = device(profileId = null, isSystemConnected = true)
        val profiled = device(profileId = "a", isLive = true)
        val devices = listOf(anonymous, profiled)
        devices.primaryByTier(profileOrder = mapOf("a" to 0)) shouldBe profiled
    }

    @Test
    fun `primaryByTier returns null when no profiled devices`() {
        val devices = listOf(device(profileId = null, isLive = true))
        devices.primaryByTier(profileOrder = emptyMap()) shouldBe null
    }

    @Test
    fun `primaryByTier returns null on empty list`() {
        emptyList<PodDevice>().primaryByTier(profileOrder = emptyMap()) shouldBe null
    }

    @Test
    fun `primaryByTier handles missing profile order with stable fallback`() {
        // Profile id not present in map → falls back to Int.MAX_VALUE (last)
        val withOrder = device(profileId = "a", isLive = true)
        val withoutOrder = device(profileId = "z", isLive = true)
        val devices = listOf(withoutOrder, withOrder)
        devices.primaryByTier(profileOrder = mapOf("a" to 0)) shouldBe withOrder
    }
}
