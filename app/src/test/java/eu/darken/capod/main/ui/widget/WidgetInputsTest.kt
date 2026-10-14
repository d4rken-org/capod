package eu.darken.capod.main.ui.widget

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.profiles.core.ProfileId
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class WidgetInputsTest : BaseComposeRobolectricTest() {

    private val deviceA = mockk<PodDevice>()
    private val deviceB = mockk<PodDevice>()
    private val estimateA = mockk<BatteryEstimate>()

    private val configA = WidgetConfig(profileId = PROFILE_A)
    private val configB = WidgetConfig(profileId = PROFILE_B)

    private val config = MutableStateFlow(configA)
    private val pro = MutableStateFlow(false)

    private val labelFlows: Map<ProfileId, MutableSharedFlow<String?>> = mapOf(
        PROFILE_A to MutableStateFlow<String?>("Label A"),
        PROFILE_B to MutableSharedFlow<String?>(replay = 1),
    )
    private val initialLabels: Map<ProfileId, String?> = mapOf(PROFILE_A to "Label A")

    private val deviceFlows: Map<ProfileId, MutableSharedFlow<PodDevice?>> = mapOf(
        PROFILE_A to MutableStateFlow<PodDevice?>(deviceA),
        PROFILE_B to MutableSharedFlow<PodDevice?>(replay = 1),
    )
    private val initialDevices: Map<ProfileId, PodDevice?> = mapOf(PROFILE_A to deviceA)

    private val estimateFlows: Map<ProfileId, MutableSharedFlow<BatteryEstimate?>> = mapOf(
        PROFILE_A to MutableStateFlow<BatteryEstimate?>(estimateA),
        PROFILE_B to MutableSharedFlow<BatteryEstimate?>(replay = 1),
    )

    /** Every value the composable returned, in composition order. */
    private val seen = mutableListOf<WidgetInputs>()
    private val latest: WidgetInputs get() = seen.last()

    private fun setInputs(
        configFlow: Flow<WidgetConfig> = config,
        initialConfig: WidgetConfig? = config.value,
    ) {
        composeRule.setContent {
            seen += rememberWidgetInputs(
                configFlow = configFlow,
                initialConfig = { initialConfig },
                proFlow = pro,
                initialPro = { pro.value },
                labelFlow = { labelFlows.getValue(it) },
                initialLabel = { initialLabels[it] },
                deviceFlow = { deviceFlows.getValue(it) },
                initialDevice = { initialDevices[it] },
                estimateFlow = { estimateFlows.getValue(it) },
                retryDelay = 10.milliseconds,
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `config emission updates the returned config`() {
        setInputs()
        latest.config shouldBe configA

        val themed = configA.copy(theme = WidgetTheme(backgroundAlpha = 100, showDeviceLabel = false))
        config.value = themed
        composeRule.waitForIdle()

        latest.config shouldBe themed
    }

    @Test
    fun `pro flip updates the returned isPro`() {
        setInputs()
        latest.isPro shouldBe false

        pro.value = true
        composeRule.waitForIdle()

        latest.isPro shouldBe true
    }

    @Test
    fun `renamed profile updates the returned label`() {
        setInputs()
        latest.profileLabel shouldBe "Label A"

        labelFlows.getValue(PROFILE_A).tryEmit("Renamed") shouldBe true
        composeRule.waitForIdle()

        latest.profileLabel shouldBe "Renamed"
    }

    @Test
    fun `profile switch never pairs the new config with the old profile's state`() {
        setInputs()
        latest.device shouldBe deviceA
        latest.profileLabel shouldBe "Label A"
        latest.rawEstimate shouldBe estimateA

        config.value = configB
        composeRule.waitForIdle()

        latest.config shouldBe configB
        seen.filter { it.config?.profileId == PROFILE_B }.forEach {
            it.device.shouldBeNull()
            it.profileLabel.shouldBeNull()
            it.rawEstimate.shouldBeNull()
        }

        deviceFlows.getValue(PROFILE_B).tryEmit(deviceB) shouldBe true
        labelFlows.getValue(PROFILE_B).tryEmit("Label B") shouldBe true
        composeRule.waitForIdle()

        latest.config shouldBe configB
        latest.device shouldBe deviceB
        latest.profileLabel shouldBe "Label B"
    }

    @Test
    fun `config read failure keeps the last config and recovers after re-subscribing`() {
        val subscriptions = AtomicInteger()
        val failFirst = CompletableDeferred<Unit>()
        val emitSecond = CompletableDeferred<Unit>()
        val configA2 = configA.copy(theme = WidgetTheme(backgroundAlpha = 42))
        val failingConfig = flow {
            if (subscriptions.incrementAndGet() == 1) {
                emit(configA)
                failFirst.await()
                throw IOException("read failed")
            } else {
                emitSecond.await()
                emit(configA2)
                awaitCancellation()
            }
        }

        setInputs(configFlow = failingConfig, initialConfig = null)
        seen.first().config.shouldBeNull()
        latest.config shouldBe configA

        failFirst.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { subscriptions.get() >= 2 }
        composeRule.waitForIdle()
        latest.config shouldBe configA

        emitSecond.complete(Unit)
        composeRule.waitUntil(timeoutMillis = 5_000) { latest.config == configA2 }
        latest.config shouldBe configA2
    }

    companion object {
        private const val PROFILE_A = "profile-a"
        private const val PROFILE_B = "profile-b"
    }
}
