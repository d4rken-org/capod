package eu.darken.capod.main.ui.tile

import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.seconds

class AncTileSendCoordinatorTest : BaseTest() {

    private val address = "00:11:22:33:44:55"
    private val off = AapSetting.AncMode.Value.OFF
    private val on = AapSetting.AncMode.Value.ON
    private val tx = AapSetting.AncMode.Value.TRANSPARENCY
    private val ad = AapSetting.AncMode.Value.ADAPTIVE
    private val visible = listOf(off, on, tx, ad)

    @Test
    fun `pending target is visible immediately and survives service restart state`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        val rendered = coordinator.applyPendingTarget(active(current = off, pending = null))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        pickNextMode(rendered.visible, rendered.current, rendered.pending) shouldBe ad
    }

    @Test
    fun `replacing pending send dispatches only latest target`() = runTest {
        val aapManager = mockk<AapConnectionManager>(relaxed = true)
        val coordinator = coordinator(aapManager)

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)
        coordinator.pendingModes.value[address] shouldBe tx
        advanceTimeBy(999)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }

        coordinator.scheduleSetAncMode(address, ad, debounce = 1.seconds)
        coordinator.pendingModes.value[address] shouldBe ad
        advanceTimeBy(999)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 0) { aapManager.sendCommand(address, AapCommand.SetAncMode(tx)) }
        coVerify(exactly = 1) { aapManager.sendCommand(address, AapCommand.SetAncMode(ad)) }
    }

    @Test
    fun `applying device pending confirmation is pure and keeps rendered pending mode`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        val rendered = coordinator.applyPendingTarget(active(current = off, pending = tx))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        coordinator.pendingModes.value[address] shouldBe tx
    }

    @Test
    fun `acknowledging device pending confirmation clears process target`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        coordinator.acknowledgeDeviceState(active(current = off, pending = tx))

        coordinator.pendingModes.value[address] shouldBe null
    }

    @Test
    fun `applying device current confirmation is pure`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        val rendered = coordinator.applyPendingTarget(active(current = tx, pending = null))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe null
        coordinator.pendingModes.value[address] shouldBe tx
    }

    @Test
    fun `acknowledging device current confirmation clears process target`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        coordinator.acknowledgeDeviceState(active(current = tx, pending = null))

        coordinator.pendingModes.value[address] shouldBe null
    }

    @Test
    fun `target matching current is kept while device reports different pending mode`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)

        val rendered = coordinator.applyPendingTarget(active(current = tx, pending = off))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe tx
        coordinator.pendingModes.value[address] shouldBe tx
    }

    @Test
    fun `applying target filtered out of visible modes is pure`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, off, debounce = 1.seconds)

        val rendered = coordinator.applyPendingTarget(active(current = on, pending = null, visible = listOf(on, tx, ad)))
        rendered.shouldBeInstanceOf<AncTileState.Active>()
        rendered.pending shouldBe null
        coordinator.pendingModes.value[address] shouldBe off
    }

    @Test
    fun `acknowledging target filtered out of visible modes clears process target`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, off, debounce = 1.seconds)

        coordinator.acknowledgeDeviceState(active(current = on, pending = null, visible = listOf(on, tx, ad)))

        coordinator.pendingModes.value[address] shouldBe null
    }

    @Test
    fun `pending target clears after timeout without confirmation`() = runTest {
        val coordinator = coordinator()

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds, timeout = 5.seconds)
        coordinator.pendingModes.value[address] shouldBe tx

        advanceTimeBy(5_000)
        runCurrent()

        coordinator.pendingModes.value[address] shouldBe null
    }

    @Test
    fun `send failure clears pending target`() = runTest {
        val aapManager = mockk<AapConnectionManager>(relaxed = true)
        coEvery {
            aapManager.sendCommand(address, AapCommand.SetAncMode(tx))
        } throws IllegalStateException("not connected")
        val coordinator = coordinator(aapManager)

        coordinator.scheduleSetAncMode(address, tx, debounce = 1.seconds)
        coordinator.pendingModes.value[address] shouldBe tx

        advanceTimeBy(1_000)
        runCurrent()

        coordinator.pendingModes.value[address] shouldBe null
    }

    private fun TestScope.coordinator(
        aapManager: AapConnectionManager = mockk(relaxed = true),
    ): AncTileSendCoordinator = AncTileSendCoordinator(
        appScope = backgroundScope,
        aapManager = aapManager,
    )

    private fun active(
        current: AapSetting.AncMode.Value,
        pending: AapSetting.AncMode.Value?,
        visible: List<AapSetting.AncMode.Value> = this.visible,
    ) = AncTileState.Active(
        current = current,
        pending = pending,
        visible = visible,
        deviceLabel = "Pods",
        deviceAddress = address,
    )
}
