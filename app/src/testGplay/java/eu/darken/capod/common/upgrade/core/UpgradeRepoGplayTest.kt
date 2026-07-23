package eu.darken.capod.common.upgrade.core

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.upgrade.core.client.ItemAlreadyOwnedBillingException
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import eu.darken.capod.common.upgrade.core.data.FreshBillingData
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.TestTimeSource
import testhelpers.coroutine.runTest2
import java.io.File
import java.io.IOException
import java.time.Duration

class UpgradeRepoGplayTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var billingDataFlow: MutableSharedFlow<BillingData>
    private lateinit var freshDataFlow: MutableSharedFlow<FreshBillingData>
    private lateinit var refreshFailuresFlow: MutableSharedFlow<Unit>
    private lateinit var billingDataRepo: BillingDataRepo
    private lateinit var billingCache: BillingCache
    private lateinit var timeSource: TestTimeSource

    private var dsCounter = 0

    private fun now(): Long = timeSource.currentTimeMillis()

    @BeforeEach
    fun setup() {
        billingDataFlow = MutableSharedFlow()
        freshDataFlow = MutableSharedFlow()
        refreshFailuresFlow = MutableSharedFlow()
        timeSource = TestTimeSource()
        billingDataRepo = mockk {
            every { billingData } returns billingDataFlow
            every { freshBillingData } returns freshDataFlow
            every { refreshFailures } returns refreshFailuresFlow
            every { purchaseFailures } returns emptyFlow()
        }
        // Eager dispatcher: the cache's suspend writes must complete synchronously, or the
        // virtual-time test clock races ahead of real-IO DataStore writes (and virtual timeouts
        // fire mid-write).
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
            produceFile = { File(tempDir, "test_billing_cache_${dsCounter++}.preferences_pb") }
        )
        billingCache = BillingCache(dataStore)
    }

    private fun createRepo(scope: TestScope): UpgradeRepoGplay {
        return UpgradeRepoGplay(
            scope = scope,
            billingDataRepo = billingDataRepo,
            billingCache = billingCache,
            timeSource = timeSource,
        )
    }

    private fun mockPurchase(productId: String, purchaseTime: Long = 1000L): Purchase = mockk {
        every { products } returns listOf(productId)
        every { this@mockk.purchaseTime } returns purchaseTime
    }

    private fun freshData(purchases: Collection<Purchase>, isFullSnapshot: Boolean = true) =
        FreshBillingData(data = BillingData(purchases = purchases), isFullSnapshot = isFullSnapshot)

    @Test
    fun `no purchases and no grace period - not pro`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        // Collect emissions in background before emitting data
        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billingDataFlow.emit(BillingData(purchases = emptyList()))

        // The startup ticks (grace-deadline onStart + the initial cache read) plus the empty
        // billing emission all map to the same not-pro state — assert the contract, not the
        // incidental emission count.
        val info = emissions.last()
        info.isPro shouldBe false
        info.error.shouldBeNull()

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `IAP purchase - is pro with hasIap`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        val purchase = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)
        billingDataFlow.emit(BillingData(purchases = listOf(purchase)))

        val info = emissions.last() as UpgradeRepoGplay.Info
        info.isPro shouldBe true
        info.hasIap shouldBe true
        info.hasSub shouldBe false

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `subscription purchase - is pro with hasSub`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        val purchase = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        billingDataFlow.emit(BillingData(purchases = listOf(purchase)))

        val info = emissions.last() as UpgradeRepoGplay.Info
        info.isPro shouldBe true
        info.hasSub shouldBe true
        info.hasIap shouldBe false

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `within grace period - onStart emits pro`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        // Set last pro state to 1 hour ago (within 7-day window)
        billingCache.lastProStateAt.valueBlocking = now() - 60 * 60 * 1000L

        val repo = createRepo(testScope)

        // The very first emission (onStart) should already be pro due to grace period
        val info = repo.upgradeInfo.first()
        info.isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `within grace period - billing data confirms grace`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        // Set last pro state to 1 hour ago (within 7-day window)
        billingCache.lastProStateAt.valueBlocking = now() - 60 * 60 * 1000L

        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billingDataFlow.emit(BillingData(purchases = emptyList()))

        val info = emissions.last()
        info.isPro shouldBe true

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `grace period expired - not pro`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))

        // Set last pro state to 8 days ago (beyond 7-day window)
        billingCache.lastProStateAt.valueBlocking = now() - 8 * 24 * 60 * 60 * 1000L

        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billingDataFlow.emit(BillingData(purchases = emptyList()))

        val info = emissions.last()
        info.isPro shouldBe false

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `upgradedAt returns correct instant from purchase`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        val purchaseTime = 1709553600000L
        val purchase = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id, purchaseTime)
        billingDataFlow.emit(BillingData(purchases = listOf(purchase)))

        val info = emissions.last() as UpgradeRepoGplay.Info
        info.upgradedAt.shouldNotBeNull()
        info.upgradedAt!!.toEpochMilli() shouldBe purchaseTime

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `Info type is GPLAY`() {
        val info = UpgradeRepoGplay.Info(billingData = null)
        info.type shouldBe eu.darken.capod.common.upgrade.UpgradeRepo.Type.GPLAY
    }

    @Test
    fun `restore returns pro when a purchase is found`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true
        // A confirmed pro purchase must refresh the grace timestamp.
        billingCache.lastProStateAt.valueBlocking shouldBeGreaterThan 0L

        testScope.cancel()
    }

    @Test
    fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = now() - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = now() - UpgradeRepoGplay.GRACE_PERIOD_MS - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `restore keeps pro within grace when the query errors`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } throws RuntimeException("Play unavailable")
        billingCache.lastProStateAt.valueBlocking = now() - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `restore rethrows the error when it happens outside grace`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } throws RuntimeException("Play unavailable")
        val repo = createRepo(testScope)

        shouldThrow<RuntimeException> {
            repo.restorePurchaseNow()
        }

        testScope.cancel()
    }

    @Test
    fun `permanent IAP keeps grace well beyond the subscription window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        // 20 days ago: past the 7-day subscription window, but within the 30-day IAP window.
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofDays(20).toMillis()
        billingCache.lastProStateSku.valueBlocking = CapodSku.Iap.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `subscription grace expires after the short window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofDays(20).toMillis()
        billingCache.lastProStateSku.valueBlocking = CapodSku.Sub.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `legacy install without a recorded SKU gets the short window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofDays(20).toMillis()
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `confirmed pro purchase records the SKU for the grace window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true
        billingCache.lastProStateSku.valueBlocking shouldBe CapodSku.Iap.PRO_UPGRADE.id

        testScope.cancel()
    }

    @Test
    fun `an IAP anchor is not downgraded by data that only shows a subscription`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // Fresh connections start with empty query caches — a failed IAP query plus an owned
        // subscription must not shrink the 30d window of an owner whose IAP was never disproven.
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)),
            isFullSnapshot = false,
        )
        billingCache.lastProStateSku.valueBlocking = CapodSku.Iap.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true
        billingCache.lastProStateSku.valueBlocking shouldBe CapodSku.Iap.PRO_UPGRADE.id

        testScope.cancel()
    }

    @Test
    fun `a subscription anchor is upgraded when an IAP purchase is confirmed`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        billingCache.lastProStateSku.valueBlocking = CapodSku.Sub.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true
        billingCache.lastProStateSku.valueBlocking shouldBe CapodSku.Iap.PRO_UPGRADE.id

        testScope.cancel()
    }

    @Test
    fun `IAP grace window is longer than the subscription window`() {
        (UpgradeRepoGplay.GRACE_PERIOD_IAP_MS > UpgradeRepoGplay.GRACE_PERIOD_MS) shouldBe true
        UpgradeRepoGplay.GRACE_PERIOD_IAP_MS shouldBe Duration.ofDays(30).toMillis()
        UpgradeRepoGplay.GRACE_PERIOD_MS shouldBe Duration.ofDays(7).toMillis()
    }

    @Test
    fun `preferredProSku prefers the permanent IAP when both are owned`() {
        val iap = PurchasedSku(CapodSku.Iap.PRO_UPGRADE, mockk<Purchase>())
        val sub = PurchasedSku(CapodSku.Sub.PRO_UPGRADE, mockk<Purchase>())

        UpgradeRepoGplay.preferredProSku(listOf(sub, iap))?.id shouldBe CapodSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(iap))?.id shouldBe CapodSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(sub))?.id shouldBe CapodSku.Sub.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(emptyList()) shouldBe null
    }

    @Test
    fun `already-owned buy attempt silently restores the purchase instead of erroring`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.startBillingFlow(any(), any(), any()) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        val repo = createRepo(testScope)

        // Restore succeeds -> no exception surfaces.
        repo.launchBillingFlow(mockk<Activity>(), CapodSku.Iap.PRO_UPGRADE)

        testScope.cancel()
    }

    @Test
    fun `already-owned buy attempt falls back to the error when restore finds nothing`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.startBillingFlow(any(), any(), any()) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingDataRepo.refresh() } returns freshData(purchases = emptyList())
        val repo = createRepo(testScope)

        // Grace expired -> the restore can't rescue the entitlement either.
        shouldThrow<ItemAlreadyOwnedBillingException> {
            repo.launchBillingFlow(mockk<Activity>(), CapodSku.Iap.PRO_UPGRADE)
        }

        testScope.cancel()
    }

    @Test
    fun `already-owned buy attempt falls back to the error when restore itself errors`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.startBillingFlow(any(), any(), any()) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingDataRepo.refresh() } throws RuntimeException("Play unavailable")
        val repo = createRepo(testScope)

        shouldThrow<ItemAlreadyOwnedBillingException> {
            repo.launchBillingFlow(mockk<Activity>(), CapodSku.Iap.PRO_UPGRADE)
        }

        testScope.cancel()
    }

    @Test
    fun `already-owned recovery requires the LAUNCHED SKU, a different owned SKU does not reconcile`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.startBillingFlow(any(), any(), any()) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        // Launching the IAP, but the restore only returns the subscription — that doesn't explain
        // the already-owned launch failure, so the user still needs the dialog with restore tips.
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id))
        )
        val repo = createRepo(testScope)

        shouldThrow<ItemAlreadyOwnedBillingException> {
            repo.launchBillingFlow(mockk<Activity>(), CapodSku.Iap.PRO_UPGRADE)
        }

        testScope.cancel()
    }

    private fun mockBillingResult(code: Int): BillingResult = mockk {
        every { responseCode } returns code
        every { debugMessage } returns "mock"
    }

    @Test
    fun `fresh observations stamp the grace cache via the persistent collector`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        createRepo(testScope)

        // No upgradeInfo collection at all — the init collector alone must stamp.
        freshDataFlow.emit(freshData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))))

        billingCache.lastProStateAt.valueBlocking shouldBeGreaterThan 0L
        billingCache.lastProStateSku.valueBlocking shouldBe CapodSku.Iap.PRO_UPGRADE.id

        testScope.cancel()
    }

    @Test
    fun `the reactive mapping is read-only, only the collector stamps`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // Count stamps via a mocked cache: the cold freshBillingData flow delivers one pro
        // observation to the init collector (1 stamp), and collecting upgradeInfo runs the
        // mapping on top (onStart-null + pro data) — if the mapping still stamped, the count
        // would exceed 1.
        val proData = BillingData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)))
        val cache = mockk<BillingCache>(relaxed = true) {
            every { lastProStateAt } returns mockk { every { flow } returns flowOf(0L) }
            every { lastProStateSku } returns mockk { every { flow } returns flowOf("") }
        }
        val repo = UpgradeRepoGplay(
            scope = testScope,
            billingDataRepo = mockk {
                every { billingData } returns flowOf(proData)
                every { freshBillingData } returns flowOf(FreshBillingData(proData, isFullSnapshot = true))
                every { refreshFailures } returns emptyFlow()
                every { purchaseFailures } returns emptyFlow()
            },
            billingCache = cache,
            timeSource = timeSource,
        )

        repo.upgradeInfo.first { it.isPro }.isPro shouldBe true

        coVerify(exactly = 1) { cache.stampLastProState(any(), any()) }

        testScope.cancel()
    }

    @Test
    fun `an observation emitted before the recorder subscribes still stamps via replay`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // The connect-time query can complete before UpgradeRepoGplay is constructed — the
        // observation stream carries replay=1 so that first Pro observation isn't lost.
        val replayingFresh = MutableSharedFlow<FreshBillingData>(replay = 1)
        replayingFresh.tryEmit(freshData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))))
        every { billingDataRepo.freshBillingData } returns replayingFresh

        createRepo(testScope)

        billingCache.lastProStateAt.valueBlocking shouldBeGreaterThan 0L

        testScope.cancel()
    }

    @Test
    fun `equal consecutive fresh observations both stamp`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // Purchase equality dedupes the state flows for a steady owner — the observation stream
        // must not dedupe, or a long-lived process would stop refreshing the grace timestamp.
        val proData = BillingData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)))
        val proFresh = FreshBillingData(proData, isFullSnapshot = true)
        val cache = mockk<BillingCache>(relaxed = true) {
            every { lastProStateAt } returns mockk { every { flow } returns flowOf(0L) }
            every { lastProStateSku } returns mockk { every { flow } returns flowOf("") }
        }
        UpgradeRepoGplay(
            scope = testScope,
            billingDataRepo = mockk {
                every { billingData } returns emptyFlow()
                every { freshBillingData } returns flowOf(proFresh, proFresh)
                every { refreshFailures } returns emptyFlow()
                every { purchaseFailures } returns emptyFlow()
            },
            billingCache = cache,
            timeSource = timeSource,
        )

        coVerify(exactly = 2) { cache.stampLastProState(any(), any()) }

        testScope.cancel()
    }

    @Test
    fun `the same failure instance delivered twice triggers two restores`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        // Play reuses static BillingResult instances — a repeat of the same object must still
        // trigger (a conflating/deduping state flow would drop it).
        val sameInstance = mockBillingResult(BillingResponseCode.ITEM_ALREADY_OWNED)
        every { billingDataRepo.purchaseFailures } returns flowOf(sameInstance, sameInstance)

        createRepo(testScope)

        coVerify(exactly = 2) { billingDataRepo.refresh() }

        testScope.cancel()
    }

    @Test
    fun `async already-owned purchase event triggers a silent restore`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        every { billingDataRepo.purchaseFailures } returns
            flowOf(mockBillingResult(BillingResponseCode.ITEM_ALREADY_OWNED))

        createRepo(testScope)

        coVerify(exactly = 1) { billingDataRepo.refresh() }

        testScope.cancel()
    }

    @Test
    fun `other async purchase failures do not trigger a restore`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        every { billingDataRepo.purchaseFailures } returns
            flowOf(mockBillingResult(BillingResponseCode.DEVELOPER_ERROR))

        createRepo(testScope)

        coVerify(exactly = 0) { billingDataRepo.refresh() }

        testScope.cancel()
    }

    // --- Unconfirmed-episode clock ---

    @Test
    fun `a full snapshot without purchases starts the unconfirmed episode`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))

        billingCache.proUnconfirmedAt.valueBlocking shouldBe now()

        testScope.cancel()
    }

    @Test
    fun `presence-only data without purchases does not start an episode`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // A push payload or partial/raced query proves nothing about absence — it must never
        // start the episode clock.
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = false))

        billingCache.proUnconfirmedAt.valueBlocking shouldBe 0L

        testScope.cancel()
    }

    @Test
    fun `a confirmed purchase atomically closes the open episode`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        billingCache.lastProStateAt.valueBlocking = now() - 2_000L
        billingCache.proUnconfirmedAt.valueBlocking = now() - 1_000L
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))))

        billingCache.proUnconfirmedAt.valueBlocking shouldBe 0L
        billingCache.lastProStateAt.valueBlocking shouldBe now()

        testScope.cancel()
    }

    @Test
    fun `episode start is set-if-unset, follow-up failures keep the original stamp`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))
        val episodeStart = billingCache.proUnconfirmedAt.valueBlocking
        episodeStart shouldBe now()

        timeSource.advanceBy(Duration.ofHours(6))
        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))

        // Pushing the stamp forward would keep resetting the 24h diagnostics threshold.
        billingCache.proUnconfirmedAt.valueBlocking shouldBe episodeStart

        testScope.cancel()
    }

    @Test
    fun `refresh failures start the episode clock`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // Most refresh failures are swallowed by their pipelines (initial query timeout,
        // foreground refresh errors) — the failure event stream must still start the episode,
        // or a sustained outage would show "confirming..." forever.
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        createRepo(testScope)

        refreshFailuresFlow.emit(Unit)

        billingCache.proUnconfirmedAt.valueBlocking shouldBe now()

        testScope.cancel()
    }

    @Test
    fun `no episode moments after a confirmation`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // A confirmation and a conflicting empty snapshot within the same minute is emission
        // reordering around a racing purchase event, not a real unconfirmed state.
        billingCache.lastProStateAt.valueBlocking = now() - 10_000L
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))

        billingCache.proUnconfirmedAt.valueBlocking shouldBe 0L

        testScope.cancel()
    }

    @Test
    fun `grace expires while the flow stays collected`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // billingData is equality-deduped and kept hot by a process-lifetime subscriber — the
        // deadline tick must flip isPro without any new billing emission.
        billingCache.lastProStateAt.valueBlocking =
            now() - UpgradeRepoGplay.GRACE_PERIOD_MS + Duration.ofMinutes(1).toMillis()
        val repo = createRepo(testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billingDataFlow.emit(BillingData(purchases = emptyList()))
        emissions.last().isPro shouldBe true

        timeSource.advanceBy(Duration.ofMinutes(2))
        testScope.testScheduler.advanceTimeBy(Duration.ofMinutes(2).toMillis())
        testScope.testScheduler.runCurrent()

        emissions.last().isPro shouldBe false

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `no episode without a prior confirmation`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // Never-Pro users have no entitlement to be "unconfirmed" about.
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))
        refreshFailuresFlow.emit(Unit)

        billingCache.proUnconfirmedAt.valueBlocking shouldBe 0L

        testScope.cancel()
    }

    @Test
    fun `a corrupt future episode stamp is repaired`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        // A stamp from the future (clock rollback, corrupt write) would push the diagnostics
        // threshold out indefinitely — it must be replaced, not trusted.
        billingCache.proUnconfirmedAt.valueBlocking = now() + Duration.ofDays(2).toMillis()
        createRepo(testScope)

        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))

        billingCache.proUnconfirmedAt.valueBlocking shouldBe now()

        testScope.cancel()
    }

    @Test
    fun `proUnconfirmedSince exposes the cached episode start`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        repo.proUnconfirmedSince.first() shouldBe 0L

        billingCache.lastProStateAt.valueBlocking = now() - Duration.ofMinutes(10).toMillis()
        freshDataFlow.emit(freshData(purchases = emptyList(), isFullSnapshot = true))

        repo.proUnconfirmedSince.first() shouldBe now()

        testScope.cancel()
    }

    // --- Storage-failure resilience (P1) ---

    // A cache whose reads/writes fail like a full or corrupt DataStore.
    private fun failingCache(): BillingCache = mockk(relaxed = true) {
        every { lastProStateAt } returns mockk { every { flow } returns flow { throw IOException("disk full") } }
        every { lastProStateSku } returns mockk { every { flow } returns flow { throw IOException("disk full") } }
        every { proUnconfirmedAt } returns mockk { every { flow } returns flow { throw IOException("disk full") } }
        coEvery { stampLastProState(any(), any()) } throws IOException("disk full")
        coEvery { recordProUnconfirmed(any()) } throws IOException("disk full")
    }

    private fun repoWith(cache: BillingCache, scope: TestScope): UpgradeRepoGplay = UpgradeRepoGplay(
        scope = scope,
        billingDataRepo = billingDataRepo,
        billingCache = cache,
        timeSource = timeSource,
    )

    @Test
    fun `a known purchase is pro even when the grace cache is unreadable and unwritable`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // restorePurchaseNow maps the fresh data directly: mapped-first must return Pro without
        // touching the cache, and the best-effort stamp must swallow the write failure.
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))
        )
        val repo = repoWith(failingCache(), testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `unknown-only purchases still fall through to the grace check`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // A purchase list of only products this app doesn't recognize maps to zero upgrades — it
        // must NOT masquerade as a confirmed purchase, but fall through to grace for a recent owner.
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase("some.unknown.product"))
        )
        billingCache.lastProStateAt.valueBlocking = now() - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `unknown-only purchases without recent grace are not pro`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns freshData(
            purchases = listOf(mockPurchase("some.unknown.product"))
        )
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `a confirmed purchase surfaces on upgradeInfo even when the grace cache is unreadable`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        // The pre-data null placeholder and the empty snapshot both hit the (unreadable) grace probe
        // first; the mapping must NOT throw and loop there, or a real Pro purchase arriving behind
        // them would never be processed. mapped-first must surface the purchase regardless.
        val billing = MutableSharedFlow<BillingData>(replay = 1)
        every { billingDataRepo.billingData } returns billing
        val repo = repoWith(failingCache(), testScope)

        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billing.emit(BillingData(purchases = emptyList()))
        emissions.last().isPro shouldBe false

        billing.emit(BillingData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id))))
        emissions.last().isPro shouldBe true

        job.cancel()
        testScope.cancel()
    }

    @Test
    fun `a persistently failing grace cache degrades to not-pro without erroring or terminating`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        every { billingDataRepo.billingData } returns flowOf(BillingData(purchases = emptyList()))
        val repo = repoWith(failingCache(), testScope)

        // The grace probe reads the broken cache but is guarded+bounded, so the mapping never throws:
        // the flow emits a plain not-pro Info (no error) instead of terminating. The companion test
        // above proves it keeps processing a later confirmed purchase, so the flow stays alive.
        val info = repo.upgradeInfo.first()
        info.isPro shouldBe false
        info.error.shouldBeNull()

        testScope.cancel()
    }

    @Test
    fun `wasEverPro falls back to false when its cache read fails`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = repoWith(failingCache(), testScope)

        repo.wasEverPro.first() shouldBe false

        testScope.cancel()
    }

    @Test
    fun `proUnconfirmedSince falls back to 0 when its cache read fails`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = repoWith(failingCache(), testScope)

        repo.proUnconfirmedSince.first() shouldBe 0L

        testScope.cancel()
    }

    @Test
    fun `retryDelayMs grows and caps at five minutes`() {
        UpgradeRepoGplay.retryDelayMs(0) shouldBe 30_000L
        UpgradeRepoGplay.retryDelayMs(1) shouldBe 60_000L
        UpgradeRepoGplay.retryDelayMs(2) shouldBe 120_000L
        UpgradeRepoGplay.retryDelayMs(3) shouldBe 240_000L
        UpgradeRepoGplay.retryDelayMs(4) shouldBe 300_000L
        UpgradeRepoGplay.retryDelayMs(100) shouldBe 300_000L
        UpgradeRepoGplay.retryDelayMs(Long.MAX_VALUE) shouldBe 300_000L
    }

    // --- autoRestoreBusy gate (P2) ---

    @Test
    fun `autoRestoreBusy rises during the invisible already-owned restore and falls after`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } coAnswers {
            delay(1_000)
            freshData(purchases = listOf(mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)))
        }
        every { billingDataRepo.purchaseFailures } returns
            flowOf(mockBillingResult(BillingResponseCode.ITEM_ALREADY_OWNED))

        // The event fires on construction; the restore then suspends in refresh().
        val repo = createRepo(testScope)

        val states = mutableListOf<Boolean>()
        val job = testScope.launch { repo.autoRestoreBusy.toList(states) }
        testScope.testScheduler.runCurrent()
        states.last() shouldBe true

        testScope.testScheduler.advanceTimeBy(1_100)
        testScope.testScheduler.runCurrent()
        states.last() shouldBe false

        job.cancel()
        testScope.cancel()
    }
}
