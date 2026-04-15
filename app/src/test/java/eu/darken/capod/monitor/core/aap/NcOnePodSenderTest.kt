package eu.darken.capod.monitor.core.aap

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NcOnePodSenderTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var aapManager: AapConnectionManager
    private lateinit var profilesRepo: DeviceProfilesRepo

    private lateinit var profilesFlow: MutableStateFlow<List<DeviceProfile>>
    private lateinit var allStatesFlow: MutableStateFlow<Map<String, AapPodState>>

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testAddress2 = "11:22:33:44:55:66"

    private val proProfile = AppleDeviceProfile(
        label = "AirPods Pro 3",
        model = PodModel.AIRPODS_PRO3,
        address = testAddress,
        onePodMode = false,
    )

    private val nonNcProfile = AppleDeviceProfile(
        label = "AirPods Gen 3",
        model = PodModel.UNKNOWN,
        address = testAddress2,
        onePodMode = true,
    )

    @BeforeEach
    fun setup() {
        profilesFlow = MutableStateFlow(emptyList())
        allStatesFlow = MutableStateFlow(emptyMap())

        aapManager = mockk(relaxed = true) {
            every { allStates } returns allStatesFlow
        }

        profilesRepo = mockk(relaxUnitFun = true) {
            every { profiles } returns profilesFlow
        }
    }

    private fun createSender() = NcOnePodSender(
        aapManager = aapManager,
        profilesRepo = profilesRepo,
    )

    @Test
    fun `sends enabled when READY and onePodMode is true`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(proProfile.copy(onePodMode = true))
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        }

        job.cancel()
    }

    @Test
    fun `sends disabled when READY and onePodMode is false`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(proProfile.copy(onePodMode = false))
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(false))
        }

        job.cancel()
    }

    @Test
    fun `does not send when device does not support NC one airpod`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(nonNcProfile)
        allStatesFlow.value = mapOf(
            testAddress2 to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { aapManager.sendCommand(any(), any<AapCommand.SetNcWithOneAirPod>()) }

        job.cancel()
    }

    @Test
    fun `does not send when connection is not READY`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(proProfile.copy(onePodMode = true))
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.CONNECTING)
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { aapManager.sendCommand(any(), any<AapCommand.SetNcWithOneAirPod>()) }

        job.cancel()
    }

    @Test
    fun `sends again when onePodMode toggles`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )

        // Enable
        profilesFlow.value = listOf(proProfile.copy(onePodMode = true))
        advanceUntilIdle()

        // Disable
        profilesFlow.value = listOf(proProfile.copy(onePodMode = false))
        advanceUntilIdle()

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(false))
        }

        job.cancel()
    }

    @Test
    fun `does not resend for unrelated state changes`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(proProfile.copy(onePodMode = true))
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        // Unrelated AAP state change (e.g. battery update) — same READY, same profile
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                lastMessageAt = java.time.Instant.now(),
            )
        )
        advanceUntilIdle()

        // Should only have sent once — distinctUntilChanged filters the second emission
        coVerify(exactly = 1) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        }

        job.cancel()
    }

    @Test
    fun `handles send failure without crashing the flow`() = runTest(testDispatcher) {
        coEvery {
            aapManager.sendCommand(any(), any<AapCommand.SetNcWithOneAirPod>())
        } throws RuntimeException("Connection lost")

        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(proProfile.copy(onePodMode = true))
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        // Flow should survive the exception
        coVerify(exactly = 1) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        }

        job.cancel()
    }

    @Test
    fun `does not send to unmatched addresses`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        // Profile for testAddress, but AAP connection on a different address
        profilesFlow.value = listOf(proProfile)
        allStatesFlow.value = mapOf(
            "XX:XX:XX:XX:XX:XX" to AapPodState(connectionState = AapPodState.ConnectionState.READY)
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { aapManager.sendCommand(any(), any<AapCommand.SetNcWithOneAirPod>()) }

        job.cancel()
    }

    @Test
    fun `only sends to NC-capable device when multiple devices connected`() = runTest(testDispatcher) {
        val sender = createSender()
        val job = launch { sender.monitor().toList() }

        profilesFlow.value = listOf(
            proProfile.copy(onePodMode = true),
            nonNcProfile,
        )
        allStatesFlow.value = mapOf(
            testAddress to AapPodState(connectionState = AapPodState.ConnectionState.READY),
            testAddress2 to AapPodState(connectionState = AapPodState.ConnectionState.READY),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            aapManager.sendCommand(testAddress, AapCommand.SetNcWithOneAirPod(true))
        }
        coVerify(exactly = 0) {
            aapManager.sendCommand(testAddress2, any<AapCommand.SetNcWithOneAirPod>())
        }

        job.cancel()
    }
}
