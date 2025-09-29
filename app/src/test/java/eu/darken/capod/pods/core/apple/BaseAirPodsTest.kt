package eu.darken.capod.pods.core.apple

import dagger.BindsInstance
import dagger.Component
import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.serialization.SerializationModule
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import java.time.Instant
import javax.inject.Singleton

abstract class BaseAirPodsTest : BaseTest() {
    @Singleton
    @Component(modules = [AppleFactoryModule::class, SerializationModule::class])
    interface AppleFactoryTestComponent {

        val appleFactory: AppleFactory

        @Component.Factory
        interface Factory {
            fun create(
                @BindsInstance profilesRepo: DeviceProfilesRepo,
            ): AppleFactoryTestComponent
        }
    }

    val profileList = mutableListOf<DeviceProfile>()
    val profileRepo = mockk<DeviceProfilesRepo>().apply {
        every { profiles } returns flowOf(profileList)
    }

    private fun hexToByteArray(hex: String): ByteArray = hex
        .replace(">", "")
        .replace("<", "")
        .fromHex()

    fun cleanKey(key: String): ByteArray = hexToByteArray(key)
        .also { require(it.size == 16) { "Not a valid key: ${it.size} byte" } }

    val factory: AppleFactory = DaggerBaseAirPodsTest_AppleFactoryTestComponent.factory().create(
        profilesRepo = profileRepo
    ).appleFactory

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(SystemClockWrap)
        every { SystemClockWrap.elapsedRealtimeNanos } returns 1000L
    }

    internal suspend inline fun <reified T : PodDevice?> create(
        hex: String,
        address: String = "77:49:4C:D8:25:0C",
        block: T.() -> Unit
    ) {
        val result = BleScanResult(
            receivedAt = Instant.now(),
            address = address,
            rssi = -66,
            generatedAtNanos = 136136027721826,
            manufacturerSpecificData = mutableMapOf<Int, ByteArray>().apply {
                this[ContinuityProtocol.APPLE_COMPANY_IDENTIFIER] = hexToByteArray(hex)
            }
        )

        block.invoke(factory.create(result) as T)
    }
}