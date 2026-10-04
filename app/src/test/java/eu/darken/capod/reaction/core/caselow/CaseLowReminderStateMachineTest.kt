package eu.darken.capod.reaction.core.caselow

import eu.darken.capod.reaction.core.caselow.CaseLowReminderStateMachine.Input
import eu.darken.capod.reaction.core.caselow.CaseLowReminderStateMachine.Output
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CaseLowReminderStateMachineTest : BaseTest() {

    private val machine = CaseLowReminderStateMachine()

    private fun update(
        bothPodsInCase: Boolean,
        casePercent: Int? = 15,
        caseCharging: Boolean? = false,
        threshold: Int = 20,
    ) = Input.Update(
        observation = CaseLowObservation(
            bothPodsInCase = bothPodsInCase,
            casePercent = casePercent,
            caseCharging = caseCharging,
        ),
        thresholdPercent = threshold,
    )

    private fun podsOut() = update(bothPodsInCase = false, casePercent = null, caseCharging = null)

    @Test
    fun `pods out then in with low non-charging case fires once`() {
        machine.process(podsOut()) shouldBe Output.None
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)
        machine.isArmed shouldBe false

        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.None
    }

    @Test
    fun `first observation with both pods already in the case does not fire`() {
        machine.process(update(bothPodsInCase = true, casePercent = 10)) shouldBe Output.None
        machine.isArmed shouldBe false
        machine.process(update(bothPodsInCase = true, casePercent = 10)) shouldBe Output.None
    }

    @Test
    fun `charging case does not fire and disarms`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 10, caseCharging = true)) shouldBe Output.Cancel
        machine.isArmed shouldBe false

        machine.process(update(bothPodsInCase = true, casePercent = 10, caseCharging = false)) shouldBe Output.None
    }

    @Test
    fun `case above threshold does not fire and disarms`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 30)) shouldBe Output.Cancel
        machine.isArmed shouldBe false

        machine.process(update(bothPodsInCase = true, casePercent = 20)) shouldBe Output.None
    }

    @Test
    fun `case exactly at threshold fires`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 20, threshold = 20)) shouldBe Output.Show(20)
    }

    @Test
    fun `unknown percent waits for the next advert with data`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = null)) shouldBe Output.None
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, casePercent = 10)) shouldBe Output.Show(10)
    }

    @Test
    fun `unknown charging state waits for the next advert with data`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, caseCharging = null)) shouldBe Output.None
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, caseCharging = false)) shouldBe Output.Show(15)
    }

    @Test
    fun `taking pods out again re-arms and fires on the next insertion`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)

        machine.process(podsOut()) shouldBe Output.None
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, casePercent = 12)) shouldBe Output.Show(12)
    }

    @Test
    fun `fresh tracker cancels once on charging regardless of placement`() {
        machine.process(update(bothPodsInCase = false, casePercent = 10, caseCharging = true)) shouldBe Output.Cancel
        machine.process(update(bothPodsInCase = false, casePercent = 10, caseCharging = true)) shouldBe Output.None
        machine.process(update(bothPodsInCase = true, casePercent = 10, caseCharging = true)) shouldBe Output.None
    }

    @Test
    fun `fresh tracker cancels once on above threshold regardless of placement`() {
        machine.process(update(bothPodsInCase = true, casePercent = 80)) shouldBe Output.Cancel
        machine.process(update(bothPodsInCase = true, casePercent = 80)) shouldBe Output.None
        machine.process(update(bothPodsInCase = false, casePercent = 80)) shouldBe Output.None
    }

    @Test
    fun `cancel is emitted again after a later show`() {
        machine.process(update(bothPodsInCase = true, casePercent = 80)) shouldBe Output.Cancel

        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)

        machine.process(update(bothPodsInCase = true, casePercent = 15, caseCharging = true)) shouldBe Output.Cancel
        machine.process(update(bothPodsInCase = true, casePercent = 25, caseCharging = true)) shouldBe Output.None
    }

    @Test
    fun `cancel evidence while pods are out keeps the tracker armed`() {
        machine.process(update(bothPodsInCase = false, casePercent = 80)) shouldBe Output.Cancel
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)
    }

    @Test
    fun `no observation changes nothing`() {
        machine.process(podsOut())

        machine.process(Input.NoObservation) shouldBe Output.None
        machine.isArmed shouldBe true

        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)
    }

    @Test
    fun `reset after a show cancels and disarms`() {
        machine.process(podsOut())
        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.Show(15)
        machine.process(podsOut())

        machine.process(Input.Reset) shouldBe Output.Cancel
        machine.isArmed shouldBe false

        machine.process(update(bothPodsInCase = true, casePercent = 15)) shouldBe Output.None
    }

    @Test
    fun `reset without a shown reminder emits nothing`() {
        machine.process(podsOut())

        machine.process(Input.Reset) shouldBe Output.None
        machine.isArmed shouldBe false
    }

    @Test
    fun `reset restores the owed cancel`() {
        machine.process(update(bothPodsInCase = true, casePercent = 80)) shouldBe Output.Cancel
        machine.process(Input.Reset) shouldBe Output.None

        machine.process(update(bothPodsInCase = true, casePercent = 80)) shouldBe Output.Cancel
    }
}
