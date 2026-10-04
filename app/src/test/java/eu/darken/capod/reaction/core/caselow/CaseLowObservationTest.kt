package eu.darken.capod.reaction.core.caselow

import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.devices.SingleApplePods
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CaseLowObservationTest : BaseTest() {

    private fun dualPods(
        bothPodsInCase: Boolean = true,
        hasCaseContext: Boolean = true,
        advertCase: Float? = 0.15f,
        caseCharging: Boolean = false,
    ) = mockk<DualApplePods>(relaxed = true) {
        every { areBothPodsInCase } returns bothPodsInCase
        every { this@mockk.hasCaseContext } returns hasCaseContext
        every { advertBatteryCasePercent } returns advertCase
        every { batteryCasePercent } returns 0.9f
        every { isCaseCharging } returns caseCharging
    }

    private fun device(ble: BlePodSnapshot?) = PodDevice(
        profileId = "p",
        ble = ble,
        aap = null,
        profileModel = PodModel.AIRPODS_PRO3,
    )

    @Test
    fun `maps both-in-case, advert case percent and charging`() {
        device(dualPods(bothPodsInCase = true, advertCase = 0.15f, caseCharging = true))
            .caseLowObservation() shouldBe CaseLowObservation(
            bothPodsInCase = true,
            casePercent = 15,
            caseCharging = true,
        )

        device(dualPods(bothPodsInCase = false, advertCase = 0.5f, caseCharging = false))
            .caseLowObservation() shouldBe CaseLowObservation(
            bothPodsInCase = false,
            casePercent = 50,
            caseCharging = false,
        )
    }

    @Test
    fun `rounds fractional percent`() {
        device(dualPods(advertCase = 0.196f)).caseLowObservation()?.casePercent shouldBe 20
    }

    @Test
    fun `without case context percent and charging are unknown`() {
        device(dualPods(bothPodsInCase = false, hasCaseContext = false, advertCase = 0.3f, caseCharging = true))
            .caseLowObservation() shouldBe CaseLowObservation(
            bothPodsInCase = false,
            casePercent = null,
            caseCharging = null,
        )
    }

    @Test
    fun `null or negative advert percent is unknown`() {
        device(dualPods(advertCase = null)).caseLowObservation()?.casePercent shouldBe null
        device(dualPods(advertCase = -1f)).caseLowObservation()?.casePercent shouldBe null
    }

    @Test
    fun `no observation without a dual pods advert`() {
        device(null).caseLowObservation() shouldBe null
        device(mockk<SingleApplePods>(relaxed = true)).caseLowObservation() shouldBe null
    }
}
