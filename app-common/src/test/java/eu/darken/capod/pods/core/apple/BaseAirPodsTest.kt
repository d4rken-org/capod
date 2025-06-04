package eu.darken.capod.pods.core.apple

import dagger.BindsInstance
import dagger.Component
import eu.darken.capod.common.SystemClockWrap
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.serialization.SerializationModule
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.ContinuityProtocol
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import testhelpers.preferences.mockFlowPreference
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
                @BindsInstance generalSettings: GeneralSettings,
            ): AppleFactoryTestComponent
        }
    }

    private val baseBleScanResult = BleScanResult(
        receivedAt = Instant.now(),
        address = "77:49:4C:D8:25:0C",
        rssi = -66,
        generatedAtNanos = 136136027721826,
        manufacturerSpecificData = emptyMap()
    )

    val generalSettings = mockk<GeneralSettings>().apply {
        every { mainDeviceIdentityKey } returns mockFlowPreference(null)
        every { mainDeviceEncryptionKey } returns mockFlowPreference(null)
    }

    val factory: AppleFactory = DaggerBaseAirPodsTest_AppleFactoryTestComponent.factory().create(
        generalSettings = generalSettings
    ).appleFactory

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(SystemClockWrap)
        every { SystemClockWrap.elapsedRealtimeNanos } returns 1000L
    }

    suspend inline fun <reified T : PodDevice?> create(hex: String, block: T.() -> Unit) {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = mockData(bytes)
        block.invoke(factory.create(result) as T)
    }

    fun mockData(hex: String): BleScanResult {
        val trimmed = hex
            .replace(" ", "")
            .replace(">", "")
            .replace("<", "")
        require(trimmed.length % 2 == 0) { "Not a HEX string" }
        val bytes = trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return mockData(bytes)
    }

    fun mockData(data: ByteArray): BleScanResult = baseBleScanResult.copy(
        manufacturerSpecificData = mutableMapOf<Int, ByteArray>().apply {
            this[ContinuityProtocol.APPLE_COMPANY_IDENTIFIER] = data
        }
    )
}