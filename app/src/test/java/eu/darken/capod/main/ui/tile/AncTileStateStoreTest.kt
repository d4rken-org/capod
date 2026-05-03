package eu.darken.capod.main.ui.tile

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.PermissionTool
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class AncTileStateStoreTest : BaseTest() {

    private val address = "00:11:22:33:44:55"
    private val off = AapSetting.AncMode.Value.OFF
    private val on = AapSetting.AncMode.Value.ON
    private val tx = AapSetting.AncMode.Value.TRANSPARENCY
    private val ad = AapSetting.AncMode.Value.ADAPTIVE
    private val visible = listOf(off, on, tx, ad)

    @Test
    fun `current state survives service listener gap`() = runTest {
        val devices = MutableStateFlow(listOf(activeDevice(currentMode = off)))
        val store = store(devices = devices)
        val listener = collectState(store)
        runCurrent()

        val activeBeforeGap = store.currentState()
        activeBeforeGap.shouldBeInstanceOf<AncTileState.Active>()
        activeBeforeGap.current shouldBe off

        listener.cancel()
        runCurrent()

        val activeAfterGap = store.currentState()
        activeAfterGap.shouldBeInstanceOf<AncTileState.Active>()
        activeAfterGap.current shouldBe off
    }

    @Test
    fun `current state warms without service listener`() = runTest {
        val store = store(devices = MutableStateFlow(listOf(activeDevice(currentMode = off))))

        runCurrent()

        val current = store.currentState()
        current.shouldBeInstanceOf<AncTileState.Active>()
        current.current shouldBe off
    }

    @Test
    fun `current state reports connecting while live device is not ready`() = runTest {
        val devices = MutableStateFlow(listOf(connectingDevice()))
        val store = store(devices = devices)
        runCurrent()

        store.currentState() shouldBe AncTileState.Connecting
    }

    @Test
    fun `current state overlays coordinator target before collected state updates`() = runTest {
        val coordinator = coordinator()
        val store = store(
            devices = MutableStateFlow(listOf(activeDevice(currentMode = off))),
            sendCoordinator = coordinator,
        )
        val listener = collectState(store)
        runCurrent()

        coordinator.scheduleSetAncMode(address, on, debounce = 1.seconds)

        val current = store.currentState()
        current.shouldBeInstanceOf<AncTileState.Active>()
        current.pending shouldBe on

        listener.cancel()
    }

    @Test
    fun `device confirmation clears coordinator target through state store`() = runTest {
        val devices = MutableStateFlow(listOf(activeDevice(currentMode = off)))
        val coordinator = coordinator()
        val store = store(
            devices = devices,
            sendCoordinator = coordinator,
        )
        runCurrent()

        coordinator.scheduleSetAncMode(address, on, debounce = 1.seconds)
        coordinator.pendingModes.value[address] shouldBe on

        devices.value = listOf(activeDevice(currentMode = on))
        runCurrent()

        coordinator.pendingModes.value[address] shouldBe null
        val current = store.currentState()
        current.shouldBeInstanceOf<AncTileState.Active>()
        current.current shouldBe on
        current.pending shouldBe null
    }

    private fun TestScope.collectState(store: AncTileStateStore): Job = launch {
        store.state.collect {}
    }

    private fun TestScope.store(
        devices: MutableStateFlow<List<PodDevice>>,
        profiles: MutableStateFlow<List<DeviceProfile>> = MutableStateFlow(emptyList()),
        isPro: MutableStateFlow<Boolean> = MutableStateFlow(true),
        bluetoothEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
        missingPermissions: MutableStateFlow<Set<Permission>> = MutableStateFlow(emptySet()),
        sendCoordinator: AncTileSendCoordinator = coordinator(),
    ): AncTileStateStore {
        val deviceMonitor = mockk<DeviceMonitor> {
            every { this@mockk.devices } returns devices
        }
        val profilesRepo = mockk<DeviceProfilesRepo> {
            every { this@mockk.profiles } returns profiles
        }
        val upgradeRepo = mockk<UpgradeRepo> {
            every { upgradeInfo } returns MutableStateFlow(upgradeInfo(isPro.value))
        }
        val bluetoothManager = mockk<BluetoothManager2> {
            every { isBluetoothEnabled } returns bluetoothEnabled
        }
        val permissionTool = mockk<PermissionTool> {
            every { this@mockk.missingPermissions } returns missingPermissions
        }

        return AncTileStateStore(
            appScope = backgroundScope,
            deviceMonitor = deviceMonitor,
            profilesRepo = profilesRepo,
            upgradeRepo = upgradeRepo,
            bluetoothManager = bluetoothManager,
            permissionTool = permissionTool,
            sendCoordinator = sendCoordinator,
        )
    }

    private fun TestScope.coordinator(
        aapManager: AapConnectionManager = mockk(relaxed = true),
    ): AncTileSendCoordinator = AncTileSendCoordinator(
        appScope = backgroundScope,
        aapManager = aapManager,
    )

    private fun activeDevice(
        currentMode: AapSetting.AncMode.Value,
        pendingMode: AapSetting.AncMode.Value? = null,
    ): PodDevice {
        val ancSetting = AapSetting.AncMode(current = currentMode, supported = visible)
        return PodDevice(
            profileId = "p1",
            ble = null,
            aap = AapPodState(
                connectionState = AapPodState.ConnectionState.READY,
                settings = mapOf(AapSetting.AncMode::class to ancSetting),
                pendingAncMode = pendingMode,
            ),
            profileAddress = address,
            profileModel = PodModel.AIRPODS_PRO,
        )
    }

    private fun connectingDevice(): PodDevice = PodDevice(
        profileId = "p1",
        ble = null,
        aap = AapPodState(connectionState = AapPodState.ConnectionState.HANDSHAKING),
        profileAddress = address,
        profileModel = PodModel.AIRPODS_PRO,
    )

    private fun upgradeInfo(isPro: Boolean) = object : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
        override val isPro: Boolean = isPro
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }
}
