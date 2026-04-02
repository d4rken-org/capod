package eu.darken.capod.profiles.core

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.datastore.FakeDataStoreValue

class DeviceProfilesRepoReorderTest : BaseTest() {

    private fun createRepo(
        initialProfiles: List<DeviceProfile>,
        scope: CoroutineScope,
    ): Pair<DeviceProfilesRepo, FakeDataStoreValue<DeviceProfilesContainer>> {
        val fakeProfiles = FakeDataStoreValue(DeviceProfilesContainer(initialProfiles))

        val settings = mockk<DeviceProfilesSettings> {
            every { profiles } returns fakeProfiles.mock
            every { defaultProfileCreated } returns FakeDataStoreValue(true).mock
        }

        val repo = DeviceProfilesRepo(
            scope = scope,
            context = mockk(relaxed = true),
            generalSettings = mockk(relaxed = true),
            settings = settings,
            deviceStateCache = mockk(relaxed = true),
        )

        return repo to fakeProfiles
    }

    @Test
    fun `reorderProfilesById reorders profiles correctly`() = runTest2 {
        val p1 = AppleDeviceProfile(id = "1", label = "First")
        val p2 = AppleDeviceProfile(id = "2", label = "Second")
        val p3 = AppleDeviceProfile(id = "3", label = "Third")

        val (repo, fakeProfiles) = createRepo(listOf(p1, p2, p3), this)

        repo.reorderProfilesById(listOf("3", "1", "2"))

        fakeProfiles.value.profiles.map { it.id } shouldBe listOf("3", "1", "2")
    }

    @Test
    fun `reorderProfilesById preserves profile data`() = runTest2 {
        val p1 = AppleDeviceProfile(id = "1", label = "First", minimumSignalQuality = 0.5f)
        val p2 = AppleDeviceProfile(id = "2", label = "Second", minimumSignalQuality = 0.8f)

        val (repo, fakeProfiles) = createRepo(listOf(p1, p2), this)

        repo.reorderProfilesById(listOf("2", "1"))

        fakeProfiles.value.profiles shouldBe listOf(p2, p1)
    }

    @Test
    fun `reorderProfilesById rejects mismatched IDs`() = runTest2(expectedError = IllegalArgumentException::class) {
        val p1 = AppleDeviceProfile(id = "1", label = "First")
        val p2 = AppleDeviceProfile(id = "2", label = "Second")

        val (repo, _) = createRepo(listOf(p1, p2), this)

        repo.reorderProfilesById(listOf("1", "3"))
    }

    @Test
    fun `reorderProfilesById rejects wrong count`() = runTest2(expectedError = IllegalArgumentException::class) {
        val p1 = AppleDeviceProfile(id = "1", label = "First")
        val p2 = AppleDeviceProfile(id = "2", label = "Second")

        val (repo, _) = createRepo(listOf(p1, p2), this)

        repo.reorderProfilesById(listOf("1"))
    }
}
