package eu.darken.capod.monitor.core.battery

import eu.darken.capod.pods.core.apple.PodModel
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

class BatteryHealthTest : BaseTest() {

    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun rate(
        fractionPerHour: Float,
        updateCount: Int = BatteryHealth.MIN_UPDATE_COUNT,
        updatedAt: Instant = now,
    ) = DrainProfile.LearnedRate(
        fractionPerHour = fractionPerHour,
        sampleCount = 10,
        updateCount = updateCount,
        updatedAt = updatedAt,
    )

    @Test
    fun `runtime is the ratio of rated to learned drain`() {
        // Pro 2 is rated 6h (0.1667/hr); a pod that only manages 3h (0.3333/hr) is at ~50%.
        val profile = DrainProfile(listeningRates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)))
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldNotBeNull().left?.percent shouldBe 50
    }

    @Test
    fun `runtime is computed per pod`() {
        // A replaced right earbud (or single-pod listening habits) makes the sides genuinely
        // diverge — each pod gets its own figure instead of one masking the other.
        val profile = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 3f), // 3h of a 6h rating -> 50%
                "UNKNOWN/RIGHT" to rate(1f / 6f), // full rated life -> 100%
            )
        )
        val health = BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldNotBeNull()
        health.left?.percent shouldBe 50
        health.right?.percent shouldBe 100
        health.headset shouldBe null
    }

    @Test
    fun `runtime is capped at 100`() {
        // Idle-heavy usage drains slower than the listening rating — never report over 100%.
        val profile = DrainProfile(listeningRates = mapOf("UNKNOWN/LEFT" to rate(0.05f)))
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldNotBeNull().left?.percent shouldBe 100
    }

    @Test
    fun `runtime uses the median across a pod's learned rates`() {
        // Three qualifying LEFT entries at 100% / 50% / 25% equivalent -> the median (50%) wins,
        // so a single gentle idle session can't inflate the figure and one hard session can't
        // tank it.
        val profile = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 6f),
                "ON/LEFT" to rate(1f / 3f),
                "OFF/LEFT" to rate(1f / 1.5f),
            )
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldNotBeNull().left?.percent shouldBe 50
    }

    @Test
    fun `rates without enough accumulated sessions are ignored`() {
        val profile = DrainProfile(
            listeningRates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_UPDATE_COUNT - 1))
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldBeNull()
    }

    @Test
    fun `models without a rating have no runtime figure`() {
        val profile = DrainProfile(listeningRates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)))
        BatteryHealth.estimate(profile, PodModel.UNKNOWN, now).shouldBeNull()
    }

    @Test
    fun `no profile or no qualifying rates yields null`() {
        BatteryHealth.estimate(null, PodModel.AIRPODS_PRO2, now).shouldBeNull()
        BatteryHealth.estimate(DrainProfile(), PodModel.AIRPODS_PRO2, now).shouldBeNull()
    }

    @Test
    fun `rates learned on different hardware are ignored`() {
        val profile = DrainProfile(
            model = PodModel.AIRPODS_PRO.name,
            listeningRates = mapOf("UNKNOWN/LEFT" to rate(1f / 3f)),
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldBeNull()
    }

    @Test
    fun `malformed bucket keys and broken rates are skipped`() {
        val profile = DrainProfile(
            listeningRates = mapOf(
                "GARBAGE/LEFT" to rate(1f / 3f), // unrecognized bucket
                "UNKNOWN" to rate(1f / 3f), // no slot at all
                "UNKNOWN/" to rate(1f / 3f), // blank slot
                "UNKNOWN/CASE" to rate(1f / 3f), // not an estimated slot
                "UNKNOWN/LEFT/EXTRA" to rate(1f / 3f), // extra path component
                "UNKNOWN/LEFT" to rate(0f), // non-positive rate
                "UNKNOWN/RIGHT" to rate(Float.NaN), // non-finite rate
            )
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now).shouldBeNull()
    }

    @Test
    fun `mode-specific rates are judged against their own rating`() {
        // AirPods 4 ANC: 4h with ANC on, 5h off. A 2h runtime learned with ANC ON is 50% of the
        // ON rating — not 40% of the OFF one.
        val profile = DrainProfile(listeningRates = mapOf("ON/LEFT" to rate(0.5f)))
        BatteryHealth.estimate(profile, PodModel.AIRPODS_GEN4_ANC, now).shouldNotBeNull().left?.percent shouldBe 50
    }

    @Test
    fun `headset slot yields a headset figure`() {
        // AirPods Max rated 20h; managing only 10h -> 50%.
        val profile = DrainProfile(listeningRates = mapOf("ON/HEADSET" to rate(0.1f)))
        val health = BatteryHealth.estimate(profile, PodModel.AIRPODS_MAX, now).shouldNotBeNull()
        health.headset?.percent shouldBe 50
        health.left shouldBe null
    }

    @Test
    fun `a reading is promotable with enough recent sessions`() {
        val profile = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT),
            )
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe true
    }

    @Test
    fun `sessions are summed across a pod's qualifying rates`() {
        // Neither entry alone clears the promote floor, together they do.
        val half = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT / 2
        val profile = DrainProfile(
            listeningRates = mapOf(
                "ON/LEFT" to rate(1f / 3f, updateCount = half),
                "OFF/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT - half),
            )
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe true
    }

    @Test
    fun `exactly the promote session floor is enough`() {
        val atFloor = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT),
            )
        )
        BatteryHealth.estimate(atFloor, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe true

        val belowFloor = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT - 1),
            )
        )
        BatteryHealth.estimate(belowFloor, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe false
    }

    @Test
    fun `only the newest qualifying rate decides staleness`() {
        val stale = now.minus(BatteryHealth.MAX_PROMOTE_AGE).minusSeconds(1)
        val profile = DrainProfile(
            listeningRates = mapOf(
                "ON/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT, updatedAt = stale),
                "OFF/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT, updatedAt = now),
            )
        )
        BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe true
    }

    @Test
    fun `a reading exactly at the age limit still promotes`() {
        val atLimit = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(
                    1f / 3f,
                    updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT,
                    updatedAt = now.minus(BatteryHealth.MAX_PROMOTE_AGE),
                ),
            )
        )
        BatteryHealth.estimate(atLimit, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe true

        val pastLimit = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(
                    1f / 3f,
                    updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT,
                    updatedAt = now.minus(BatteryHealth.MAX_PROMOTE_AGE).minus(Duration.ofSeconds(1)),
                ),
            )
        )
        BatteryHealth.estimate(pastLimit, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().left?.isPromotable shouldBe false
    }

    @Test
    fun `lowestPromotable picks the worst pod that has enough evidence`() {
        val perPod = BatteryHealth.PerPod(
            left = BatteryHealth.Reading(percent = 60, isPromotable = true),
            right = BatteryHealth.Reading(percent = 40, isPromotable = true),
        )
        perPod.lowestPromotable shouldBe BatteryHealth.SlotReading(
            slot = BatteryHealth.Slot.RIGHT,
            reading = BatteryHealth.Reading(percent = 40, isPromotable = true),
        )
    }

    @Test
    fun `lowestPromotable ignores readings without enough evidence`() {
        val perPod = BatteryHealth.PerPod(
            left = BatteryHealth.Reading(percent = 20, isPromotable = false),
            headset = BatteryHealth.Reading(percent = 70, isPromotable = true),
        )
        perPod.lowestPromotable shouldBe BatteryHealth.SlotReading(
            slot = BatteryHealth.Slot.HEADSET,
            reading = BatteryHealth.Reading(percent = 70, isPromotable = true),
        )
    }

    @Test
    fun `lowestPromotable is null when nothing qualifies`() {
        BatteryHealth.PerPod(
            left = BatteryHealth.Reading(percent = 20, isPromotable = false),
        ).lowestPromotable.shouldBeNull()
        BatteryHealth.PerPod().lowestPromotable.shouldBeNull()
    }

    @Test
    fun `exactly the low runtime threshold counts as low`() {
        // Pro 2 rated 6h; 3h learned -> 50%, the threshold itself must still warn.
        val profile = DrainProfile(
            listeningRates = mapOf(
                "UNKNOWN/LEFT" to rate(1f / 3f, updateCount = BatteryHealth.MIN_PROMOTE_UPDATE_COUNT),
            )
        )
        val lowest = BatteryHealth.estimate(profile, PodModel.AIRPODS_PRO2, now)
            .shouldNotBeNull().lowestPromotable.shouldNotBeNull()
        lowest.reading.percent shouldBe BatteryHealth.LOW_RUNTIME_PERCENT
        (lowest.reading.percent <= BatteryHealth.LOW_RUNTIME_PERCENT) shouldBe true
    }
}
