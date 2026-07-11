package eu.darken.capod.common.upgrade.core

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.upgrade.core.client.ItemAlreadyOwnedBillingException
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.File
import java.time.Duration

class UpgradeRepoGplayTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var billingDataFlow: MutableSharedFlow<BillingData>
    private lateinit var billingDataRepo: BillingDataRepo
    private lateinit var billingCache: BillingCache

    private var dsCounter = 0

    @BeforeEach
    fun setup() {
        billingDataFlow = MutableSharedFlow()
        billingDataRepo = mockk {
            every { billingData } returns billingDataFlow
        }
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir, "test_billing_cache_${dsCounter++}.preferences_pb") }
        )
        billingCache = mockk {
            every { lastProStateAt } returns dataStore.createValue("gplay.cache.lastProAt", 0L)
            every { lastProStateSku } returns dataStore.createValue("gplay.cache.lastProSku", "")
        }
    }

    private fun createRepo(scope: TestScope): UpgradeRepoGplay {
        return UpgradeRepoGplay(
            scope = scope,
            billingDataRepo = billingDataRepo,
            billingCache = billingCache,
        )
    }

    private fun mockPurchase(productId: String, purchaseTime: Long = 1000L): Purchase = mockk {
        every { products } returns listOf(productId)
        every { this@mockk.purchaseTime } returns purchaseTime
    }

    @Test
    fun `no purchases and no grace period - not pro`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = createRepo(testScope)

        // Collect emissions in background before emitting data
        val emissions = mutableListOf<eu.darken.capod.common.upgrade.UpgradeRepo.Info>()
        val job = testScope.launch { repo.upgradeInfo.toList(emissions) }

        billingDataFlow.emit(BillingData(purchases = emptyList()))

        // First emission is onStart, second is billing data
        emissions.size shouldBe 2
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
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - 60 * 60 * 1000L

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
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - 60 * 60 * 1000L

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
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L

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
        coEvery { billingDataRepo.refresh() } returns BillingData(
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
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking =
            System.currentTimeMillis() - UpgradeRepoGplay.GRACE_PERIOD_MS - 1_000L
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `restore keeps pro within grace when the query errors`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } throws RuntimeException("Play unavailable")
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - 1_000L
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
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
        // 20 days ago: past the 7-day subscription window, but within the 30-day IAP window.
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - Duration.ofDays(20).toMillis()
        billingCache.lastProStateSku.valueBlocking = CapodSku.Iap.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe true

        testScope.cancel()
    }

    @Test
    fun `subscription grace expires after the short window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - Duration.ofDays(20).toMillis()
        billingCache.lastProStateSku.valueBlocking = CapodSku.Sub.PRO_UPGRADE.id
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `legacy install without a recorded SKU gets the short window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
        billingCache.lastProStateAt.valueBlocking = System.currentTimeMillis() - Duration.ofDays(20).toMillis()
        val repo = createRepo(testScope)

        repo.restorePurchaseNow().isPro shouldBe false

        testScope.cancel()
    }

    @Test
    fun `confirmed pro purchase records the SKU for the grace window`() = runTest2 {
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        coEvery { billingDataRepo.refresh() } returns BillingData(
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
        coEvery { billingDataRepo.refresh() } returns BillingData(
            purchases = listOf(mockPurchase(CapodSku.Sub.PRO_UPGRADE.id))
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
        coEvery { billingDataRepo.refresh() } returns BillingData(
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
        coEvery { billingDataRepo.refresh() } returns BillingData(
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
        coEvery { billingDataRepo.refresh() } returns BillingData(purchases = emptyList())
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
}
