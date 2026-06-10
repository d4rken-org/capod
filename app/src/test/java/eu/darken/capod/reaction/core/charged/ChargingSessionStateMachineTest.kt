package eu.darken.capod.reaction.core.charged

import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Activity
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Input
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Lid
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Output
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Phase
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.Slot
import eu.darken.capod.reaction.core.charged.ChargingSessionStateMachine.SlotData
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ChargingSessionStateMachineTest : BaseTest() {

    private val machine = ChargingSessionStateMachine()

    private fun live(threshold: Float = 1.0f, vararg slots: Pair<Slot, SlotData>) =
        Input.LiveUpdate(slots = slots.toMap(), threshold = threshold)

    private fun charging(battery: Float) = SlotData(battery = battery, isCharging = true)
    private fun idle(battery: Float) = SlotData(battery = battery, isCharging = false)

    @Test
    fun `no charging slots keeps machine idle`() {
        machine.process(live(1.0f, Slot.LEFT to idle(0.5f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.IDLE
    }

    @Test
    fun `dual pods and case charge to full fires once`() {
        machine.process(
            live(1.0f, Slot.LEFT to charging(0.5f), Slot.RIGHT to charging(0.6f), Slot.CASE to charging(0.7f))
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING

        machine.process(
            live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(1.0f), Slot.CASE to charging(1.0f))
        ) shouldBe Output.NONE

        machine.process(
            live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f), Slot.CASE to charging(1.0f))
        ) shouldBe Output.SHOW
        machine.phase shouldBe Phase.FIRED

        // Same data again — no duplicate fire.
        machine.process(
            live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f), Slot.CASE to charging(1.0f))
        ) shouldBe Output.NONE
    }

    @Test
    fun `slot stopping to charge at threshold counts as complete not unplug`() {
        machine.process(live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f)))
        // Firmware flips isCharging off once a pod hits 100%.
        machine.process(live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to charging(0.9f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        machine.process(live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `slot stopping to charge below threshold resets the session`() {
        machine.process(live(1.0f, Slot.LEFT to charging(0.5f)))
        machine.process(live(1.0f, Slot.LEFT to idle(0.6f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.IDLE
    }

    @Test
    fun `notification cancels when battery discharges below threshold after firing`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(0.99f)))
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW

        // Charging flag dropping at full is NOT an unplug signal — notification stays.
        machine.process(live(1.0f, Slot.HEADSET to idle(1.0f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED

        // Battery dropping below threshold proves the device is off power and in use.
        machine.process(live(1.0f, Slot.HEADSET to idle(0.97f))) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.IDLE
    }

    @Test
    fun `new charging session after firing cancels and re-arms`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(live(1.0f, Slot.HEADSET to charging(0.4f))) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.IDLE
        machine.process(live(1.0f, Slot.HEADSET to charging(0.4f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `idle non-session slot below threshold does not cancel after firing`() {
        // Regression: pods charging in an open case whose own battery sits below the threshold
        // but isn't charging. The case carries no signal and must not flap SHOW/CANCEL.
        machine.process(
            live(0.6f, Slot.LEFT to charging(0.7f), Slot.RIGHT to charging(0.7f), Slot.CASE to idle(0.45f))
        ) shouldBe Output.SHOW
        machine.process(
            live(0.6f, Slot.LEFT to charging(0.7f), Slot.RIGHT to charging(0.7f), Slot.CASE to idle(0.45f))
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
    }

    @Test
    fun `case starting to charge below threshold after firing starts a new session`() {
        machine.process(live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f))) shouldBe Output.SHOW
        machine.process(
            live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f), Slot.CASE to charging(0.5f))
        ) shouldBe Output.CANCEL
        machine.process(
            live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f), Slot.CASE to charging(1.0f))
        ) shouldBe Output.SHOW
    }

    @Test
    fun `re-plug near full after firing cancels then fires on the next update`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        // Re-plugged while slightly drained: the old notification is cancelled first…
        machine.process(live(1.0f, Slot.HEADSET to charging(0.95f))) shouldBe Output.CANCEL
        // …and the new session starts (and can fire) on the following update.
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `custom threshold uses at-least semantics across coarse jumps`() {
        // Public BLE reports in 10% steps; 0.7 → 0.9 may skip the 0.8 threshold entirely.
        machine.process(live(0.8f, Slot.LEFT to charging(0.7f), Slot.RIGHT to charging(0.7f)))
        machine.process(live(0.8f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f))) shouldBe Output.SHOW
    }

    @Test
    fun `slot joining mid-session must also reach threshold`() {
        machine.process(live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f)))
        machine.process(
            live(
                1.0f,
                Slot.LEFT to charging(1.0f),
                Slot.RIGHT to charging(1.0f),
                Slot.CASE to charging(0.5f),
            )
        ) shouldBe Output.NONE
        machine.process(
            live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f), Slot.CASE to charging(1.0f))
        ) shouldBe Output.SHOW
    }

    @Test
    fun `stale data suspends and resuming continues the session`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(0.8f)))
        machine.process(Input.StaleUpdate) shouldBe Output.NONE
        machine.phase shouldBe Phase.SUSPENDED
        machine.process(Input.StaleUpdate) shouldBe Output.NONE

        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `suspend after firing keeps notification and cancels on discharged resume`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(Input.StaleUpdate) shouldBe Output.NONE
        machine.process(live(1.0f, Slot.HEADSET to idle(0.8f))) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.IDLE
    }

    @Test
    fun `battery regression during suspension starts a fresh session`() {
        machine.process(live(0.8f, Slot.HEADSET to charging(0.75f)))
        machine.process(Input.StaleUpdate)
        // Device was unplugged and used while we were blind: battery well below the highwater.
        machine.process(live(0.8f, Slot.HEADSET to charging(0.4f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        // The old 0.75 highwater is gone — 0.4 must climb to 0.8 again before firing.
        machine.process(live(0.8f, Slot.HEADSET to charging(0.7f))) shouldBe Output.NONE
        machine.process(live(0.8f, Slot.HEADSET to charging(0.8f))) shouldBe Output.SHOW
    }

    @Test
    fun `single step BLE flicker does not count as regression`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(0.8f)))
        machine.process(Input.StaleUpdate)
        // Exactly one public-BLE step (0.1) below the mark — tolerated, session continues.
        machine.process(live(1.0f, Slot.HEADSET to charging(0.7f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `session slot missing from an update neither completes nor unplugs`() {
        machine.process(live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f)))
        // Right slot vanishes (e.g. AAP only pushed a partial battery update).
        machine.process(live(1.0f, Slot.LEFT to charging(1.0f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        machine.process(live(1.0f, Slot.LEFT to idle(1.0f), Slot.RIGHT to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `reset before firing is silent`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(0.5f)))
        machine.process(Input.Reset) shouldBe Output.NONE
        machine.phase shouldBe Phase.IDLE
    }

    @Test
    fun `reset after firing cancels the notification`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(Input.Reset) shouldBe Output.CANCEL
    }

    @Test
    fun `reset while suspended from fired cancels the notification`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(Input.StaleUpdate)
        machine.process(Input.Reset) shouldBe Output.CANCEL
    }

    @Test
    fun `reset while suspended from charging is silent`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(0.5f)))
        machine.process(Input.StaleUpdate)
        machine.process(Input.Reset) shouldBe Output.NONE
    }

    @Test
    fun `already full slot fires immediately when charging starts`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `wearing a pod after firing dismisses without re-firing`() {
        machine.process(live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f))) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.LEFT)),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED

        // Still full, pod back in the charging case — latched, no second notification.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f)),
                threshold = 1.0f,
            )
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `lid movement after firing dismisses`() {
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.NONE
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.CLOSED),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `lid unknown at fire becomes baseline instead of dismissing`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        // First definite lid sighting after fire establishes the baseline silently…
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
        // …and only a subsequent change dismisses.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.CLOSED),
            )
        ) shouldBe Output.CANCEL
    }

    @Test
    fun `pod taken out of case after firing dismisses`() {
        // BLE-only path: removing a pod doesn't produce an in-ear or AAP DISCONNECTED signal,
        // but the out-of-case pod broadcasts lid state NOT_IN_CASE — that change is activity.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.NOT_IN_CASE),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `session slot going disconnected after firing dismisses`() {
        machine.process(live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f))) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(disconnectedSlots = setOf(Slot.LEFT)),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `worn while charging at fire time does not insta-dismiss`() {
        // AirPods Max can be worn while cable-charging: worn when the notification fires and
        // staying worn is not activity — taking them off is.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.HEADSET)),
            )
        ) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.HEADSET)),
            )
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = emptySet()),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `steady one-pod wear with lid flapping never dismisses but second pod does`() {
        // The hardware scenario: LEFT worn, RIGHT charging in the open case. Both pods
        // broadcast, dedup alternates their frames, so the lid flaps OPEN/NOT_IN_CASE and
        // the worn set stays {LEFT}. None of that is new activity after the fire.
        fun frame(lid: Lid, battery: Float) = Input.LiveUpdate(
            slots = mapOf(Slot.RIGHT to charging(battery)),
            threshold = 0.8f,
            activity = Activity(wornSlots = setOf(Slot.LEFT), lid = lid),
        )
        machine.process(frame(Lid.OPEN, 0.6f)) shouldBe Output.NONE
        machine.process(frame(Lid.NOT_IN_CASE, 0.7f)) shouldBe Output.NONE
        machine.process(frame(Lid.OPEN, 0.7f)) shouldBe Output.NONE
        machine.process(frame(Lid.NOT_IN_CASE, 0.8f)) shouldBe Output.SHOW

        // Post-fire flapping continues — both lid values are in the union baseline.
        machine.process(frame(Lid.OPEN, 0.8f)) shouldBe Output.NONE
        machine.process(frame(Lid.NOT_IN_CASE, 0.8f)) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED

        // Wearing the freshly charged second pod is new activity.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.RIGHT to idle(0.8f)),
                threshold = 0.8f,
                activity = Activity(wornSlots = setOf(Slot.LEFT, Slot.RIGHT), lid = Lid.NOT_IN_CASE),
            )
        ) shouldBe Output.CANCEL
        machine.phase shouldBe Phase.DISMISSED
    }

    @Test
    fun `instant fire after a reset keeps the lid alternation in its baseline`() {
        // Step 3 hardware path: left worn, right charging, user lowers the threshold to an
        // already-reached value. The lid alternates OPEN/NOT_IN_CASE the whole time; the reset
        // must not throw away that window or the next (instant) fire flaps itself away.
        fun frame(lid: Lid, charging: Boolean) = Input.LiveUpdate(
            slots = mapOf(Slot.RIGHT to SlotData(0.9f, charging)),
            threshold = 0.8f,
            activity = Activity(wornSlots = setOf(Slot.LEFT), lid = lid),
        )
        // Ambient alternation observed before the settings change.
        machine.process(frame(Lid.OPEN, true))
        machine.process(frame(Lid.NOT_IN_CASE, true))
        machine.process(frame(Lid.OPEN, true))

        machine.process(Input.Reset) shouldBe Output.NONE

        // Instant fire on the next frame after the reset.
        machine.process(frame(Lid.NOT_IN_CASE, true)) shouldBe Output.SHOW
        // The alternate lid value must already be in the baseline — no false dismiss.
        machine.process(frame(Lid.OPEN, true)) shouldBe Output.NONE
        machine.process(frame(Lid.NOT_IN_CASE, true)) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
    }

    @Test
    fun `lid closing still dismisses despite flap-tolerant baseline`() {
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.NONE
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.OPEN),
            )
        ) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.CLOSED),
            )
        ) shouldBe Output.CANCEL
    }

    @Test
    fun `case-only session ignores worn pods but dismisses on lid movement`() {
        // Scope=CASE feeds only the case slot; wearing pods says nothing about the case charge.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.CASE to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = emptySet(), lid = Lid.OPEN),
            )
        ) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.CASE to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.LEFT, Slot.RIGHT), lid = Lid.OPEN),
            )
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.CASE to charging(1.0f)),
                threshold = 1.0f,
                activity = Activity(lid = Lid.CLOSED),
            )
        ) shouldBe Output.CANCEL
    }

    @Test
    fun `slot disconnected in the firing frame is baseline not activity`() {
        machine.process(live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f)))
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.LEFT to idle(1.0f), Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(disconnectedSlots = setOf(Slot.LEFT)),
            )
        ) shouldBe Output.SHOW
        // LEFT was already out at fire time — only RIGHT leaving the case is new activity.
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(disconnectedSlots = setOf(Slot.LEFT)),
            )
        ) shouldBe Output.NONE
        machine.phase shouldBe Phase.FIRED
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.RIGHT to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(disconnectedSlots = setOf(Slot.LEFT, Slot.RIGHT)),
            )
        ) shouldBe Output.CANCEL
    }

    @Test
    fun `dismissed re-arms through a new below-threshold charge`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.HEADSET)),
            )
        ) shouldBe Output.CANCEL
        // Drained and plugged back in → fresh session that can fire again.
        machine.process(live(1.0f, Slot.HEADSET to charging(0.5f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.CHARGING
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
    }

    @Test
    fun `dismissed survives staleness and resets silently`() {
        machine.process(live(1.0f, Slot.HEADSET to charging(1.0f))) shouldBe Output.SHOW
        machine.process(
            Input.LiveUpdate(
                slots = mapOf(Slot.HEADSET to idle(1.0f)),
                threshold = 1.0f,
                activity = Activity(wornSlots = setOf(Slot.HEADSET)),
            )
        ) shouldBe Output.CANCEL
        machine.process(Input.StaleUpdate) shouldBe Output.NONE
        machine.process(live(1.0f, Slot.HEADSET to idle(1.0f))) shouldBe Output.NONE
        machine.phase shouldBe Phase.DISMISSED
        machine.process(Input.Reset) shouldBe Output.NONE
    }

    @Test
    fun `non-charging slots do not join the session`() {
        // Case sits at 40% but isn't charging — only the pods gate the notification.
        machine.process(
            live(1.0f, Slot.LEFT to charging(0.9f), Slot.RIGHT to charging(0.9f), Slot.CASE to idle(0.4f))
        )
        machine.process(
            live(1.0f, Slot.LEFT to charging(1.0f), Slot.RIGHT to charging(1.0f), Slot.CASE to idle(0.4f))
        ) shouldBe Output.SHOW
    }
}
