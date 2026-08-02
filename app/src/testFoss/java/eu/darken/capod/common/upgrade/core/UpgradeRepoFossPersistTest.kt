package eu.darken.capod.common.upgrade.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.datastore.value
import eu.darken.capod.common.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The FOSS supporter record is create-only-if-absent: the sponsor-return heuristic can fire again
 * for someone who is already a supporter (the recurring-donation button, or a stale entitlement
 * replay), and a rewrite would move their "supporter since" date — and, for the legacy records
 * every existing supporter has, replace their stored reason too.
 *
 * Driven through a real DataStore on a temp file via [FossCache]'s test seam, because the guarantee
 * is the store transaction's, not the caller's.
 */
class UpgradeRepoFossPersistTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    // One store scope per test: the DataStore keeps its own actor alive on it.
    private var storeScope: CoroutineScope? = null

    @AfterEach
    fun teardown() {
        storeScope?.cancel()
        storeScope = null
    }

    private class Harness(val cache: FossCache, val repo: UpgradeRepoFoss)

    // Unique file name per test method: DataStore forbids two active instances on the same file.
    private fun TestScope.buildHarness(storeName: String): Harness {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { storeScope = it }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tempDir, "$storeName.preferences_pb") },
        )
        val cache = FossCache(dataStore, SerializationModule().json())
        val repo = UpgradeRepoFoss(
            // backgroundScope: the repo's shareIn keeps a collector alive for the scope's lifetime.
            appScope = backgroundScope,
            fossCache = cache,
            webpageTool = mockk<WebpageTool>(relaxed = true),
        )
        return Harness(cache, repo)
    }

    @Test
    fun `persistUpgrade keeps an existing legacy record`() = runTest {
        val harness = buildHarness("legacy_record")
        // A legacy 2022-schema value: a rewrite would corrupt BOTH the date and the reason.
        harness.cache.upgrade.value(
            FossUpgrade(
                upgradedAt = Instant.EPOCH,
                reason = FossUpgrade.Reason.NO_MONEY,
            )
        )

        harness.repo.persistUpgrade() shouldBe false

        harness.cache.upgrade.value() shouldBe FossUpgrade(
            upgradedAt = Instant.EPOCH,
            reason = FossUpgrade.Reason.NO_MONEY,
        )
        harness.repo.upgradeInfo.first().apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.EPOCH
        }
    }

    @Test
    fun `persistUpgrade creates on an empty store`() = runTest {
        val harness = buildHarness("empty_store")
        harness.cache.upgrade.value() shouldBe null

        // Truncated: the record's serializer is epoch-millis, so a nanosecond lower bound can flake
        // when the write lands within the same millisecond.
        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        harness.repo.persistUpgrade() shouldBe true
        val after = Instant.now()

        val created = harness.cache.upgrade.value()
        created shouldNotBe null
        created!!.reason shouldBe FossUpgrade.Reason.DONATED
        (created.upgradedAt >= before) shouldBe true
        (created.upgradedAt <= after) shouldBe true

        // Boolean-proven keep: immune to a timestamp collision between the two writes.
        harness.repo.persistUpgrade() shouldBe false
        harness.cache.upgrade.value() shouldBe created
    }

    @Test
    fun `concurrent persists elect exactly one creator`() = runTest {
        val harness = buildHarness("concurrent")
        harness.cache.upgrade.value() shouldBe null

        val before = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val gate = CompletableDeferred<Unit>()
        val racers = List(2) {
            async(Dispatchers.IO) {
                gate.await()
                harness.repo.persistUpgrade()
            }
        }
        gate.complete(Unit)
        val results = racers.awaitAll()
        val after = Instant.now()

        // Exactly one creator: the loser must report the record it found, not a second creation.
        results.sorted() shouldBe listOf(false, true)

        val record = harness.cache.upgrade.value()
        record shouldNotBe null
        record!!.reason shouldBe FossUpgrade.Reason.DONATED
        (record.upgradedAt >= before) shouldBe true
        (record.upgradedAt <= after) shouldBe true
    }
}
