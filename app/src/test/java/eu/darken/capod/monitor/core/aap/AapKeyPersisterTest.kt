package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.fromHex
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.KeyExchangeResult
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The key fields on [AppleDeviceProfile] are ByteArrays, so a wrong null-handling or
 * reference-comparison here either writes the profile on every connect or stops persisting keys
 * entirely.
 */
class AapKeyPersisterTest : BaseTest() {

    private val address: BluetoothAddress = "AA:BB:CC:DD:EE:FF"
    private val irkHex = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-79"
    private val encHex = "11-22-33-44-55-66-77-88-99-AA-BB-CC-DD-EE-FF-00"

    private val aapManager = mockk<AapConnectionManager>()
    private val profilesRepo = mockk<DeviceProfilesRepo>()

    private lateinit var latestProfile: AppleDeviceProfile

    private suspend fun runPersister(profile: AppleDeviceProfile, keys: KeyExchangeResult, latest: AppleDeviceProfile = profile) {
        latestProfile = latest
        val keysReceived = MutableSharedFlow<Pair<BluetoothAddress, KeyExchangeResult>>(replay = 1)
        keysReceived.tryEmit(address to keys)

        every { aapManager.keysReceived } returns keysReceived
        every { profilesRepo.profiles } returns flowOf(listOf(profile))
        coEvery { profilesRepo.updateAppleProfile(any(), any()) } returns Unit

        AapKeyPersister(aapManager, profilesRepo).monitor().first()
    }

    private fun profile(
        identityKey: ByteArray? = null,
        encryptionKey: ByteArray? = null,
    ) = AppleDeviceProfile(
        label = "Mine",
        model = PodModel.AIRPODS_PRO2_USBC,
        identityKey = identityKey,
        encryptionKey = encryptionKey,
        address = address,
    )

    private fun capturedProfile(): AppleDeviceProfile {
        val captured = slot<(AppleDeviceProfile) -> AppleDeviceProfile>()
        coVerify(exactly = 1) { profilesRepo.updateAppleProfile(any(), capture(captured)) }
        return captured.captured(latestProfile)
    }

    @Test
    fun `saving keys preserves a routing preference written since the profile was read`() = runTest {
        val original = profile()
        val automatic = AapSetting.ConnectionPreference.Mode.AUTOMATIC
        runPersister(
            profile = original,
            keys = KeyExchangeResult(irk = irkHex.fromHex(), encKey = null),
            latest = original.copy(lastRequestedConnectionPreference = automatic),
        )
        capturedProfile().lastRequestedConnectionPreference shouldBe automatic
    }

    @Test
    fun `a first key is persisted`() = runTest {
        runPersister(
            profile = profile(),
            keys = KeyExchangeResult(irk = irkHex.fromHex(), encKey = null),
        )

        capturedProfile().identityKey.contentEquals(irkHex.fromHex()) shouldBe true
    }

    @Test
    fun `a key equal by content is not persisted again`() = runTest {
        runPersister(
            profile = profile(identityKey = irkHex.fromHex(), encryptionKey = encHex.fromHex()),
            keys = KeyExchangeResult(irk = irkHex.fromHex(), encKey = encHex.fromHex()),
        )

        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(any(), any()) }
    }

    @Test
    fun `a missing incoming key leaves the stored one alone`() = runTest {
        runPersister(
            profile = profile(identityKey = irkHex.fromHex(), encryptionKey = encHex.fromHex()),
            keys = KeyExchangeResult(irk = null, encKey = encHex.fromHex()),
        )

        coVerify(exactly = 0) { profilesRepo.updateAppleProfile(any(), any()) }
    }

    @Test
    fun `a changed encryption key is persisted without touching the identity key`() = runTest {
        val newEnc = "00-11-22-33-44-55-66-77-88-99-AA-BB-CC-DD-EE-FF".fromHex()
        runPersister(
            profile = profile(identityKey = irkHex.fromHex(), encryptionKey = encHex.fromHex()),
            keys = KeyExchangeResult(irk = irkHex.fromHex(), encKey = newEnc),
        )

        val written = capturedProfile()
        written.encryptionKey.contentEquals(newEnc) shouldBe true
        written.identityKey.contentEquals(irkHex.fromHex()) shouldBe true
    }
}
