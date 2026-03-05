package eu.darken.capod.common.upgrade.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.android.billingclient.api.Purchase
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
}
