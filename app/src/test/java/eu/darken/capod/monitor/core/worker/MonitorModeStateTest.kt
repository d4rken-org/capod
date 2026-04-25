package eu.darken.capod.monitor.core.worker

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class MonitorModeStateTest : BaseTest() {

    private val addressA: BluetoothAddress = "AA:BB:CC:DD:EE:01"
    private val addressB: BluetoothAddress = "AA:BB:CC:DD:EE:02"

    private val profileA = AppleDeviceProfile(
        label = "AirPods A",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = addressA,
    )
    private val profileB = AppleDeviceProfile(
        label = "AirPods B",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = addressB,
    )
    private val noAddressProfile = AppleDeviceProfile(
        label = "Profile no address",
        model = PodModel.AIRPODS_PRO2_USBC,
        address = null,
    )

    private val aapStateA = AapPodState(
        connectionState = AapPodState.ConnectionState.READY,
        lastMessageAt = Instant.parse("2026-04-25T12:00:00Z"),
    )

    private fun mockDevice(addr: BluetoothAddress): BluetoothDevice2 = mockk {
        every { address } returns addr
    }

    @Test
    fun `empty profiles - hasProfiles false and addresses empty`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        state.hasProfiles shouldBe false
        state.knownAddresses shouldBe emptySet()
        state.connectedAddresses shouldBe emptySet()
        state.hasAapSession shouldBe false
        state.mode shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `profile with valid address - hasProfiles true and address present`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileA),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        state.hasProfiles shouldBe true
        state.knownAddresses shouldBe setOf(addressA)
    }

    @Test
    fun `profile with null address - hasProfiles true but knownAddresses empty`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(noAddressProfile),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        state.hasProfiles shouldBe true
        state.knownAddresses shouldBe emptySet()
    }

    @Test
    fun `mixed profiles - hasProfiles true and only addressed profile contributes`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileA, noAddressProfile),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        state.hasProfiles shouldBe true
        state.knownAddresses shouldBe setOf(addressA)
    }

    @Test
    fun `connectedDevices addresses derived from BluetoothDevice2 address`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            devices = listOf(mockDevice(addressA), mockDevice(addressB)),
            aapStates = emptyMap(),
        )
        state.connectedAddresses shouldBe setOf(addressA, addressB)
    }

    @Test
    fun `aapStates non-empty - hasAapSession true`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            devices = emptyList(),
            aapStates = mapOf(addressA to aapStateA),
        )
        state.hasAapSession shouldBe true
    }

    @Test
    fun `aapStates empty - hasAapSession false`() {
        val state = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        state.hasAapSession shouldBe false
    }

    @Test
    fun `equal inputs produce equal states - data class equality enables distinctUntilChanged`() {
        val first = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileA, profileB),
            devices = listOf(mockDevice(addressA)),
            aapStates = mapOf(addressA to aapStateA),
        )
        val second = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileA, profileB),
            devices = listOf(mockDevice(addressA)),
            aapStates = mapOf(addressA to aapStateA),
        )
        first shouldBe second
    }

    @Test
    fun `reordered profile list with same addresses produces equal knownAddresses`() {
        val first = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileA, profileB),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        val second = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = listOf(profileB, profileA),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        first.knownAddresses shouldBe second.knownAddresses
        first shouldBe second
    }

    @Test
    fun `mode change produces unequal states`() {
        val auto = buildMonitorModeState(
            mode = MonitorMode.AUTOMATIC,
            profiles = emptyList(),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        val manual = buildMonitorModeState(
            mode = MonitorMode.MANUAL,
            profiles = emptyList(),
            devices = emptyList(),
            aapStates = emptyMap(),
        )
        (auto == manual) shouldBe false
    }
}
