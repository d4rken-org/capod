package eu.darken.capod.main.ui.overview

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID

class OverviewKeysTest : BaseTest() {

    private fun device(profileId: String?, identifier: BlePodSnapshot.Id? = null): PodDevice {
        val bleSnapshot: BlePodSnapshot? = identifier?.let { id ->
            mockk(relaxed = true) {
                every { this@mockk.identifier } returns id
            }
        }
        return PodDevice(profileId = profileId, ble = bleSnapshot, aap = null)
    }

    @Test
    fun `profiledDeviceKey returns plain key for non-duplicate profile`() {
        val d = device(profileId = "p1", identifier = BlePodSnapshot.Id(UUID.randomUUID()))
        profiledDeviceKey(d, index = 0, duplicateProfileIds = emptySet()) shouldBe "profiled:p1"
    }

    @Test
    fun `profiledDeviceKey disambiguates duplicates with identifier`() {
        val id1 = BlePodSnapshot.Id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val id2 = BlePodSnapshot.Id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val d1 = device(profileId = "p1", identifier = id1)
        val d2 = device(profileId = "p1", identifier = id2)

        val k1 = profiledDeviceKey(d1, index = 0, duplicateProfileIds = setOf("p1"))
        val k2 = profiledDeviceKey(d2, index = 1, duplicateProfileIds = setOf("p1"))

        k1 shouldBe "profiled:p1:$id1"
        k2 shouldBe "profiled:p1:$id2"
        (k1 == k2) shouldBe false
    }

    @Test
    fun `profiledDeviceKey falls back to index when no BLE snapshot is present`() {
        val d1 = device(profileId = "p1")
        val d2 = device(profileId = "p1")
        val k1 = profiledDeviceKey(d1, index = 0, duplicateProfileIds = setOf("p1"))
        val k2 = profiledDeviceKey(d2, index = 1, duplicateProfileIds = setOf("p1"))

        k1 shouldBe "profiled:p1:idx:0"
        k2 shouldBe "profiled:p1:idx:1"
        (k1 == k2) shouldBe false
    }

    @Test
    fun `unmatchedDeviceKey uses identifier when present`() {
        val id = BlePodSnapshot.Id(UUID.fromString("00000000-0000-0000-0000-0000000000aa"))
        val d = device(profileId = null, identifier = id)
        unmatchedDeviceKey(d, index = 5) shouldBe "unmatched:$id"
    }

    @Test
    fun `unmatchedDeviceKey falls back to index when no BLE snapshot is present`() {
        val d1 = device(profileId = null)
        val d2 = device(profileId = null)
        val k1 = unmatchedDeviceKey(d1, index = 0)
        val k2 = unmatchedDeviceKey(d2, index = 1)

        k1 shouldBe "unmatched:idx:0"
        k2 shouldBe "unmatched:idx:1"
        (k1 == k2) shouldBe false
    }
}
