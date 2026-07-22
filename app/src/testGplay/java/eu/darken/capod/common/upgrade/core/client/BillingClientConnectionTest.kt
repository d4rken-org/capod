package eu.darken.capod.common.upgrade.core.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.capod.common.upgrade.core.CapodSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingClientConnectionTest : BaseTest() {

    @AfterEach
    fun teardown() {
        unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
    }

    private fun mockPurchase(
        productId: String = CapodSku.Iap.PRO_UPGRADE.id,
        purchaseTime: Long = 1_000,
        state: Int = Purchase.PurchaseState.PURCHASED,
        token: String = "token-$productId-$purchaseTime",
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { this@mockk.purchaseTime } returns purchaseTime
        every { purchaseState } returns state
        every { purchaseToken } returns token
    }

    private class Harness(
        val purchasesGlobal: MutableStateFlow<Collection<Purchase>> = MutableStateFlow(emptySet()),
        var generation: Long = 0L,
    ) {
        val client = mockk<BillingClient>(relaxed = true)
        val freshObservations = MutableSharedFlow<FreshPurchases>(replay = 1, extraBufferCapacity = 16)
        val freshFailures = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
        val connection = BillingClientConnection(
            client = client,
            purchasesGlobal = purchasesGlobal,
            freshObservations = freshObservations,
            freshFailuresGlobal = freshFailures,
            purchaseFailuresGlobal = MutableSharedFlow(),
            listenerGeneration = { generation },
        )

        init {
            mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }

        fun okResult(purchases: List<Purchase>) = PurchasesResult(
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build(),
            purchases.toList(),
        )

        fun errorResult() = PurchasesResult(
            BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.ERROR).build(),
            emptyList(),
        )
    }

    @Test
    fun `pending purchases are filtered out of the push-based purchases flow`() = runTest2 {
        // A PENDING purchase (e.g. a slow cash/deferred payment) must never reach the entitlement
        // layer — only PURCHASED grants Pro. This guards the #628 fix at the connection boundary.
        val pending = mockPurchase(purchaseTime = 2_000, state = Purchase.PurchaseState.PENDING)
        val purchased = mockPurchase(purchaseTime = 1_000, state = Purchase.PurchaseState.PURCHASED)

        val harness = Harness(purchasesGlobal = MutableStateFlow(listOf(pending, purchased)))

        harness.connection.purchases.first() shouldBe listOf(purchased)
    }

    @Test
    fun `refresh with both queries OK is a full snapshot`() = runTest2 {
        val harness = Harness()
        val owned = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            harness.okResult(listOf(owned)),
            harness.okResult(emptyList()),
        )

        val fresh = harness.connection.refreshPurchases()

        fresh.purchases shouldBe listOf(owned)
        fresh.isFullSnapshot shouldBe true
        harness.freshObservations.first() shouldBe fresh
    }

    @Test
    fun `refresh with a partial failure is presence-only`() = runTest2 {
        // One type failed — the surviving type's authoritative purchase suppresses the error, but
        // absence was NOT proven, so this refresh must not claim full-snapshot provenance (it
        // could otherwise start a bogus unconfirmed episode for the failed type's entitlement).
        val harness = Harness()
        val ownedSub = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            harness.errorResult(),
            harness.okResult(listOf(ownedSub)),
        )

        val fresh = harness.connection.refreshPurchases()

        fresh.purchases shouldBe listOf(ownedSub)
        fresh.isFullSnapshot shouldBe false
    }

    @Test
    fun `a purchase event racing the refresh downgrades the snapshot`() = runTest2 {
        // The listener bumped its generation while the queries were in flight: the purchase it
        // carries may be missing from our (older) query results, so an empty combined result must
        // not claim to prove absence.
        val harness = Harness()
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
            harness.okResult(emptyList())
        } coAndThen {
            harness.generation += 1
            harness.okResult(emptyList())
        }

        val fresh = harness.connection.refreshPurchases()

        fresh.purchases shouldBe emptyList()
        fresh.isFullSnapshot shouldBe false
    }

    @Test
    fun `an inconclusive refresh throws and reports a fresh failure`() = runTest2 {
        val harness = Harness()
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returnsMany listOf(
            harness.errorResult(),
            harness.okResult(emptyList()),
        )

        shouldThrow<BillingResultException> {
            harness.connection.refreshPurchases()
        }

        // The failure event is what starts the unconfirmed-episode clock during outages.
        harness.freshFailures.first() shouldBe Unit
    }

    @Test
    fun `querySubscriptions commits fresh state and supersedes stale listener records`() = runTest2 {
        // The stale in-session record (isAutoRenewing=true from the purchase moment) and the
        // fresh query record share a token — after the query, only the fresh one may survive,
        // or ownership mapping would read "still renewing" forever.
        val stale = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, token = "shared-token")
        val fresh = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, token = "shared-token")
        val harness = Harness(purchasesGlobal = MutableStateFlow(listOf(stale)))
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
            harness.okResult(listOf(fresh))

        val result = harness.connection.querySubscriptions()

        result shouldContainExactly listOf(fresh)
        // Stale record superseded: the combined purchases view only contains the fresh record.
        harness.connection.purchases.first() shouldContainExactly listOf(fresh)
        // Presence-only provenance: a SUBS query proves nothing about the IAP.
        harness.freshObservations.first().isFullSnapshot shouldBe false
    }

    @Test
    fun `a subscription purchased while the query runs is still seen by the gate`() = runTest2 {
        // The listener publishes a brand-new sub AND bumps the generation mid-query: the (older,
        // empty) query result must neither prune nor hide it — over-blocking the switch is safe,
        // missing a renewing sub is not.
        val racing = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, token = "race-token")
        val harness = Harness()
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
            harness.purchasesGlobal.value = listOf(racing)
            harness.generation += 1
            harness.okResult(emptyList())
        }

        val result = harness.connection.querySubscriptions()

        result shouldContainExactly listOf(racing)
        harness.connection.purchases.first() shouldContainExactly listOf(racing)
    }

    @Test
    fun `a conclusive empty SUBS query prunes stale listener sub records`() = runTest2 {
        // No purchase event raced the query, and the query proved absence for subscriptions —
        // the stale in-session record (e.g. from a refunded purchase) must not resurrect Pro or
        // block the switch until process restart.
        val stale = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, token = "stale-token")
        val harness = Harness(purchasesGlobal = MutableStateFlow(listOf(stale)))
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
            harness.okResult(emptyList())

        val result = harness.connection.querySubscriptions()

        result shouldBe emptyList()
        harness.connection.purchases.first() shouldBe emptyList()
    }

    @Test
    fun `a conclusive empty full refresh prunes stale listener records`() = runTest2 {
        val stale = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id, token = "stale-token")
        val harness = Harness(purchasesGlobal = MutableStateFlow(listOf(stale)))
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
            harness.okResult(emptyList())

        val fresh = harness.connection.refreshPurchases()

        fresh.isFullSnapshot shouldBe true
        harness.connection.purchases.first() shouldBe emptyList()
    }

    @Test
    fun `a raced refresh keeps newer listener records`() = runTest2 {
        // Generation changed mid-refresh: the listener's record is newer than the query result,
        // so nothing may be pruned and the snapshot must not claim to prove absence.
        val newer = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, token = "new-token")
        val harness = Harness()
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
            harness.purchasesGlobal.value = listOf(newer)
            harness.generation += 1
            harness.okResult(emptyList())
        }

        val fresh = harness.connection.refreshPurchases()

        fresh.isFullSnapshot shouldBe false
        harness.connection.purchases.first() shouldContainExactly listOf(newer)
    }

    @Test
    fun `querySubscriptions failure propagates and reports a fresh failure`() = runTest2 {
        val harness = Harness()
        coEvery { harness.client.queryPurchasesAsync(any<QueryPurchasesParams>()) } returns
            harness.errorResult()

        shouldThrow<BillingResultException> {
            harness.connection.querySubscriptions()
        }

        harness.freshFailures.first() shouldBe Unit
    }

    @Test
    fun `combines both product types, newest first`() {
        val older = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id, purchaseTime = 1_000)
        val newer = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id, purchaseTime = 2_000)

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(older)),
            subs = Result.success(listOf(newer)),
        ) shouldBe listOf(newer, older)
    }

    @Test
    fun `a single product-type failure does not mask a pro purchase found by the other`() {
        val owned = mockPurchase(CapodSku.Iap.PRO_UPGRADE.id)

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(owned)),
            subs = Result.failure(RuntimeException("SUBS query failed")),
        ) shouldBe listOf(owned)

        val ownedSub = mockPurchase(CapodSku.Sub.PRO_UPGRADE.id)
        BillingClientConnection.combinePurchaseResults(
            iaps = Result.failure(RuntimeException("IAP query failed")),
            subs = Result.success(listOf(ownedSub)),
        ) shouldBe listOf(ownedSub)
    }

    @Test
    fun `an unknown purchase does not suppress the other product-type's failure`() {
        // An unknown/legacy product is discarded by BillingData later — it must not hide that the
        // other product type couldn't be verified, or a restore would wrongly report "not owned".
        val unknown = mockPurchase("some.legacy.product")

        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.success(listOf(unknown)),
                subs = Result.failure(RuntimeException("SUBS query failed")),
            )
        }
    }

    @Test
    fun `unknown purchases are returned as-is when both queries succeed`() {
        val unknown = mockPurchase("some.legacy.product")

        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(listOf(unknown)),
            subs = Result.success(emptyList()),
        ) shouldBe listOf(unknown)
    }

    @Test
    fun `both product types empty returns empty`() {
        BillingClientConnection.combinePurchaseResults(
            iaps = Result.success(emptyList()),
            subs = Result.success(emptyList()),
        ) shouldBe emptyList()
    }

    @Test
    fun `nothing found but a query failed rethrows the error`() {
        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.success(emptyList()),
                subs = Result.failure(RuntimeException("SUBS query failed")),
            )
        }

        shouldThrow<RuntimeException> {
            BillingClientConnection.combinePurchaseResults(
                iaps = Result.failure(RuntimeException("IAP query failed")),
                subs = Result.success(emptyList()),
            )
        }
    }
}
