package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BatteryHealthTest : BaseTest() {

    private fun rate(fractionPerHour: Float, updateCount: Int = BatteryHealth.MIN_UPDATE_COUNT) =
        DrainProfile.LearnedRate(
            fractionPerHour = fractionPerHour,
            sampleCount = 10,
            updateCount = updateCount,
            updatedAt = Instant.EPOCH,
        )

    @Test
    fun `health is the ratio of rated to learned drain`() {
        // Pro 2 is rated 6h (0.1667/hr); a pod that only manages 3h (0.3333/hr) is at ~50%.
        val profile = DrainProfile(rates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)))
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2) shouldBe 50
    }

    @Test
    fun `health is capped at 100`() {
        // Idle-heavy usage drains slower than the listening rating — never report over-health.
        val profile = DrainProfile(rates = mapOf("UNKNOWN/LEFT" to rate(0.05f)))
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2) shouldBe 100
    }

    @Test
    fun `health uses the median across learned rates`() {
        // Three qualifying entries at 100% / 50% / 25% equivalent -> the median (50%) wins, so a
        // single gentle idle session can't inflate the figure and one hard session can't tank it.
        val profile = DrainProfile(
            rates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 6f),
                "UNKNOWN/RIGHT" to rate(1f / 3f),
                "OFF/LEFT" to rate(1f / 1.5f),
            )
        )
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2) shouldBe 50
    }

    @Test
    fun `rates without enough accumulated sessions are ignored`() {
        val profile = DrainProfile(
            rates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_UPDATE_COUNT - 1))
        )
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2).shouldBeNull()
    }

    @Test
    fun `models without a rating have no health`() {
        val profile = DrainProfile(rates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)))
        BatteryHealth.estimatePercent(profile, PodModel.UNKNOWN).shouldBeNull()
    }

    @Test
    fun `no profile or no qualifying rates yields null`() {
        BatteryHealth.estimatePercent(null, PodModel.AIRPODS_PRO2).shouldBeNull()
        BatteryHealth.estimatePercent(DrainProfile(), PodModel.AIRPODS_PRO2).shouldBeNull()
    }

    @Test
    fun `rates learned on different hardware are ignored`() {
        val profile = DrainProfile(
            model = PodModel.AIRPODS_PRO.name,
            rates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)),
        )
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2).shouldBeNull()
    }

    @Test
    fun `malformed bucket keys and broken rates are skipped`() {
        val profile = DrainProfile(
            rates = mapOf(
                "GARBAGE/LEFT" to rate(1f / 3f), // unrecognized bucket
                "UNKNOWN" to rate(1f / 3f), // no slot at all
                "UNKNOWN/" to rate(1f / 3f), // blank slot
                "UNKNOWN/CASE" to rate(1f / 3f), // not an estimated slot
                "UNKNOWN/LEFT/EXTRA" to rate(1f / 3f), // extra path component
                "UNKNOWN/LEFT" to rate(0f), // non-positive rate
                "UNKNOWN/RIGHT" to rate(Float.NaN), // non-finite rate
            )
        )
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_PRO2).shouldBeNull()
    }

    @Test
    fun `mode-specific rates are judged against their own rating`() {
        // AirPods 4 ANC: 4h with ANC on, 5h off. A 2h runtime learned with ANC ON is 50% of the
        // ON rating — not 40% of the OFF one.
        val profile = DrainProfile(rates = mapOf("ON/LEFT" to rate(0.5f)))
        BatteryHealth.estimatePercent(profile, PodModel.AIRPODS_GEN4_ANC) shouldBe 50
    }
}
