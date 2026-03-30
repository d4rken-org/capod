package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AutoConnectLogicTest : BaseTest() {

    private lateinit var autoConnect: AutoConnect

    @BeforeEach
    fun setup() {
        autoConnect = AutoConnect(
            bluetoothManager = mockk(relaxed = true),
            deviceMonitor = mockk(relaxed = true),
            generalSettings = mockk(relaxed = true),
            reactionSettings = mockk(relaxed = true),
            deviceProfilesRepo = mockk(relaxed = true),
        )
    }

    private fun evaluate(
        mainDeviceAddr: String? = "AA:BB:CC:DD:EE:FF",
        hasBondedDevice: Boolean = true,
        isAlreadyConnected: Boolean = false,
        condition: AutoConnectCondition = AutoConnectCondition.WHEN_SEEN,
        lidState: DualApplePods.LidState? = null,
        isBeingWorn: Boolean = false,
        isEitherPodInEar: Boolean = false,
        onePodMode: Boolean = false,
        supportsEarDetection: Boolean = false,
    ) = autoConnect.evaluateAutoConnect(
        mainDeviceAddr = mainDeviceAddr,
        hasBondedDevice = hasBondedDevice,
        isAlreadyConnected = isAlreadyConnected,
        condition = condition,
        lidState = lidState,
        isBeingWorn = isBeingWorn,
        isEitherPodInEar = isEitherPodInEar,
        onePodMode = onePodMode,
        supportsEarDetection = supportsEarDetection,
    )

    @Nested
    inner class Preconditions {

        @Test
        fun `null main device address - should NOT connect`() {
            evaluate(mainDeviceAddr = null).shouldConnect shouldBe false
        }

        @Test
        fun `empty main device address - should NOT connect`() {
            evaluate(mainDeviceAddr = "").shouldConnect shouldBe false
        }

        @Test
        fun `no bonded device - should NOT connect`() {
            evaluate(hasBondedDevice = false).shouldConnect shouldBe false
        }

        @Test
        fun `already connected - should NOT connect`() {
            evaluate(isAlreadyConnected = true).shouldConnect shouldBe false
        }
    }

    @Nested
    inner class WhenSeenCondition {

        @Test
        fun `WHEN_SEEN and not connected - should connect`() {
            evaluate(condition = AutoConnectCondition.WHEN_SEEN).shouldConnect shouldBe true
        }
    }

    @Nested
    inner class CaseOpenCondition {

        @Test
        fun `CASE_OPEN with lid OPEN - should connect`() {
            evaluate(
                condition = AutoConnectCondition.CASE_OPEN,
                lidState = DualApplePods.LidState.OPEN,
            ).shouldConnect shouldBe true
        }

        @Test
        fun `CASE_OPEN with lid CLOSED - should NOT connect`() {
            evaluate(
                condition = AutoConnectCondition.CASE_OPEN,
                lidState = DualApplePods.LidState.CLOSED,
            ).shouldConnect shouldBe false
        }

        @Test
        fun `CASE_OPEN with lid UNKNOWN - should NOT connect`() {
            evaluate(
                condition = AutoConnectCondition.CASE_OPEN,
                lidState = DualApplePods.LidState.UNKNOWN,
            ).shouldConnect shouldBe false
        }

        @Test
        fun `CASE_OPEN with lid NOT_IN_CASE - should NOT connect`() {
            evaluate(
                condition = AutoConnectCondition.CASE_OPEN,
                lidState = DualApplePods.LidState.NOT_IN_CASE,
            ).shouldConnect shouldBe false
        }

        @Test
        fun `CASE_OPEN with null lidState (unsupported device) - should connect (permissive fallback)`() {
            evaluate(
                condition = AutoConnectCondition.CASE_OPEN,
                lidState = null,
            ).shouldConnect shouldBe true
        }
    }

    @Nested
    inner class InEarCondition {

        @Test
        fun `IN_EAR with both pods in ear (normal mode) - should connect`() {
            evaluate(
                condition = AutoConnectCondition.IN_EAR,
                isBeingWorn = true,
                isEitherPodInEar = true,
                onePodMode = false,
                supportsEarDetection = true,
            ).shouldConnect shouldBe true
        }

        @Test
        fun `IN_EAR with no pods in ear - should NOT connect`() {
            evaluate(
                condition = AutoConnectCondition.IN_EAR,
                isBeingWorn = false,
                isEitherPodInEar = false,
                onePodMode = false,
                supportsEarDetection = true,
            ).shouldConnect shouldBe false
        }

        @Test
        fun `IN_EAR with one pod in ear (normal mode, not both) - should NOT connect`() {
            evaluate(
                condition = AutoConnectCondition.IN_EAR,
                isBeingWorn = false,
                isEitherPodInEar = true,
                onePodMode = false,
                supportsEarDetection = true,
            ).shouldConnect shouldBe false
        }

        @Test
        fun `IN_EAR with one pod in ear (one-pod mode) - should connect`() {
            evaluate(
                condition = AutoConnectCondition.IN_EAR,
                isBeingWorn = false,
                isEitherPodInEar = true,
                onePodMode = true,
                supportsEarDetection = true,
            ).shouldConnect shouldBe true
        }

        @Test
        fun `IN_EAR with unsupported device - should connect (permissive fallback)`() {
            evaluate(
                condition = AutoConnectCondition.IN_EAR,
                isBeingWorn = false,
                isEitherPodInEar = false,
                supportsEarDetection = false,
            ).shouldConnect shouldBe true
        }
    }
}
