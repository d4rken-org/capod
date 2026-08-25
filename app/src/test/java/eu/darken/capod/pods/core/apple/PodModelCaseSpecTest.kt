package eu.darken.capod.pods.core.apple

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PodModelCaseSpecTest : BaseTest() {

    @Test
    fun `placeholder models carry no case spec`() {
        PodModel.entries
            .filter { it == PodModel.UNKNOWN || it.name.startsWith("FAKE_") }
            .filter { it.caseSpec != null }
            .shouldBeEmpty()
    }

    @Test
    fun `a case spec implies a case`() {
        PodModel.entries
            .filter { it.caseSpec != null }
            .filter { !it.features.hasCase }
            .shouldBeEmpty()
    }

    @Test
    fun `every case spec is usable`() {
        PodModel.entries.mapNotNull { it.caseSpec }.forEach {
            it.fullPairRecharges.isFinite() shouldBe true
            it.fullPairRecharges shouldBeGreaterThan 0f
        }
    }

    @Test
    fun `the published figures divide out as recorded`() {
        // hoursWithCase / hoursListening - 1
        PodModel.AIRPODS_GEN1.caseSpec?.fullPairRecharges shouldBe 3.8f
        PodModel.AIRPODS_GEN1.caseSpec?.isLowerBound shouldBe true
        PodModel.AIRPODS_GEN2.caseSpec?.isLowerBound shouldBe true
        PodModel.AIRPODS_GEN3.caseSpec?.fullPairRecharges shouldBe (30f / 6f - 1f)
        PodModel.AIRPODS_GEN3.caseSpec?.isLowerBound shouldBe false
        PodModel.AIRPODS_GEN4.caseSpec?.fullPairRecharges shouldBe (30f / 5f - 1f)
        PodModel.AIRPODS_GEN4_ANC.caseSpec?.fullPairRecharges shouldBe (20f / 4f - 1f)
        PodModel.AIRPODS_PRO2.caseSpec?.fullPairRecharges shouldBe (30f / 6f - 1f)
        PodModel.AIRPODS_PRO2_USBC.caseSpec?.fullPairRecharges shouldBe (30f / 6f - 1f)
        PodModel.AIRPODS_PRO3.caseSpec?.fullPairRecharges shouldBe (24f / 8f - 1f)
    }
}
