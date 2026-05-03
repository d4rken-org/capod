package eu.darken.capod.main.ui.tile

import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AncTileStateMapperTest : BaseTest() {

    private val noPermissions = emptySet<Permission>()
    private val supportedModes = listOf(
        AapSetting.AncMode.Value.OFF,
        AapSetting.AncMode.Value.ON,
        AapSetting.AncMode.Value.TRANSPARENCY,
        AapSetting.AncMode.Value.ADAPTIVE,
    )

    private fun activeDevice(
        currentMode: AapSetting.AncMode.Value = AapSetting.AncMode.Value.ON,
        pendingMode: AapSetting.AncMode.Value? = null,
        connectionState: AapPodState.ConnectionState = AapPodState.ConnectionState.READY,
        model: PodModel = PodModel.AIRPODS_PRO,
    ): PodDevice {
        val ancSetting = AapSetting.AncMode(current = currentMode, supported = supportedModes)
        return PodDevice(
            profileId = "p1",
            ble = null,
            aap = AapPodState(
                connectionState = connectionState,
                settings = mapOf(AapSetting.AncMode::class to ancSetting),
                pendingAncMode = pendingMode,
            ),
            profileModel = model,
        )
    }

    @Test
    fun `not pro returns NotPro regardless of other state`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = false,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.NotPro
    }

    @Test
    fun `missing scan permission returns PermissionRequired`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = setOf(Permission.BLUETOOTH_SCAN),
        ) shouldBe AncTileState.PermissionRequired
    }

    @Test
    fun `missing BLUETOOTH_CONNECT returns PermissionRequired`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = setOf(Permission.BLUETOOTH_CONNECT),
        ) shouldBe AncTileState.PermissionRequired
    }

    @Test
    fun `missing non-blocking permission does not flip state`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = setOf(Permission.POST_NOTIFICATIONS),
        ).shouldBeInstanceOf<AncTileState.Active>()
    }

    @Test
    fun `bluetooth disabled returns BluetoothOff`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = true,
            isBluetoothEnabled = false,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.BluetoothOff
    }

    @Test
    fun `null device returns NoDevice`() {
        AncTileStateMapper.map(
            device = null,
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.NoDevice
    }

    @Test
    fun `device without ANC support returns NoAncSupport`() {
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = null,
            profileModel = PodModel.AIRPODS_GEN1,
        )
        AncTileStateMapper.map(
            device = device,
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.NoAncSupport
    }

    @Test
    fun `cached device with no AAP session returns NotConnected`() {
        // ANC-capable model but aap == null → user sees "Disconnected", not "Connecting forever".
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = null,
            profileModel = PodModel.AIRPODS_PRO,
        )
        AncTileStateMapper.map(
            device = device,
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.NotConnected
    }

    @Test
    fun `aap connected but not ready returns Connecting`() {
        AncTileStateMapper.map(
            device = activeDevice(connectionState = AapPodState.ConnectionState.HANDSHAKING),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.Connecting
    }

    @Test
    fun `aap ready without AncMode setting returns Connecting`() {
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = AapPodState(connectionState = AapPodState.ConnectionState.READY),
            profileModel = PodModel.AIRPODS_PRO,
        )
        AncTileStateMapper.map(
            device = device,
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.Connecting
    }

    @Test
    fun `fully ready device returns Active with current and visible modes`() {
        val state = AncTileStateMapper.map(
            device = activeDevice(currentMode = AapSetting.AncMode.Value.TRANSPARENCY),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        )
        state.shouldBeInstanceOf<AncTileState.Active>()
        state.current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
        state.visible shouldBe supportedModes
    }

    @Test
    fun `pending mode is propagated to Active`() {
        val state = AncTileStateMapper.map(
            device = activeDevice(
                currentMode = AapSetting.AncMode.Value.OFF,
                pendingMode = AapSetting.AncMode.Value.TRANSPARENCY,
            ),
            isPro = true,
            isBluetoothEnabled = true,
            missingPermissions = noPermissions,
        )
        state.shouldBeInstanceOf<AncTileState.Active>()
        state.pending shouldBe AapSetting.AncMode.Value.TRANSPARENCY
    }

    @Test
    fun `precedence pro gating wins over bluetooth off`() {
        AncTileStateMapper.map(
            device = activeDevice(),
            isPro = false,
            isBluetoothEnabled = false,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.NotPro
    }

    @Test
    fun `precedence bluetooth off wins over no device`() {
        AncTileStateMapper.map(
            device = null,
            isPro = true,
            isBluetoothEnabled = false,
            missingPermissions = noPermissions,
        ) shouldBe AncTileState.BluetoothOff
    }

    @Test
    fun `precedence permission required wins over bluetooth off`() {
        AncTileStateMapper.map(
            device = null,
            isPro = true,
            isBluetoothEnabled = false,
            missingPermissions = setOf(Permission.BLUETOOTH_SCAN),
        ) shouldBe AncTileState.PermissionRequired
    }
}
