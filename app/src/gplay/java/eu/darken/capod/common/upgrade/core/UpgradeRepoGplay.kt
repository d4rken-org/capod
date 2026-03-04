package eu.darken.capod.common.upgrade.core

import android.app.Activity
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.core.data.BillingData
import eu.darken.capod.common.upgrade.core.data.BillingDataRepo
import eu.darken.capod.common.upgrade.core.data.PurchasedSku
import eu.darken.capod.common.upgrade.core.data.Sku
import eu.darken.capod.common.upgrade.core.data.SkuDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.valueBlocking

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingDataRepo: BillingDataRepo,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    private var lastProStateAt: Long
        get() = billingCache.lastProStateAt.valueBlocking
        set(value) { billingCache.lastProStateAt.valueBlocking = value }

    override val upgradeInfo: Flow<UpgradeRepo.Info> = billingDataRepo.billingData
        .map { data ->
            val now = System.currentTimeMillis()
            val proSku = data.getProSku()
            log(TAG) { "now=$now, lastProStateAt=$lastProStateAt, data=${data}" }
            when {
                proSku != null -> {
                    lastProStateAt = now
                    Info(billingData = data, upgrades = data.getProSkus())
                }

                (now - lastProStateAt) < 7 * 24 * 60 * 60 * 1000L -> { // 7 days
                    log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                    Info(gracePeriod = true, billingData = null)
                }

                else -> {
                    Info(billingData = data, upgrades = data.getProSkus())
                }
            }
        }
        .onStart {
            val now = System.currentTimeMillis()
            if ((now - lastProStateAt) < 7 * 24 * 60 * 60 * 1000L) {
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null))
            }
        }
        .catch { error ->
            log(TAG, WARN) { "upgradeInfo error: ${error.asLog()}" }
            val now = System.currentTimeMillis()
            if ((now - lastProStateAt) < 7 * 24 * 60 * 60 * 1000L) {
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null, error = error))
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        val upgrades: Collection<PurchasedSku> = emptyList(),
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type
            get() = UpgradeRepo.Type.GPLAY

        override val isPro: Boolean
            get() = billingData?.getProSku() != null || gracePeriod

        val hasIap: Boolean
            get() = upgrades.any { it.sku is Sku.Iap }

        val hasSub: Boolean
            get() = upgrades.any { it.sku is Sku.Subscription }

        override val upgradedAt: Instant?
            get() = billingData
                ?.getProSku()
                ?.purchase?.purchaseTime
                ?.let { Instant.ofEpochMilli(it) }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingDataRepo.querySkus(*skus)

    suspend fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer? = null,
    ) = billingDataRepo.startBillingFlow(activity, sku, offer)

    suspend fun refresh(): BillingData = billingDataRepo.refresh()

    companion object {
        private fun BillingData.getProSku(): PurchasedSku? = purchasedSkus
            .firstOrNull { it.sku in CapodSku.PRO_SKUS }

        private fun BillingData.getProSkus(): Collection<PurchasedSku> = purchasedSkus
            .filter { it.sku in CapodSku.PRO_SKUS }

        val TAG: String = logTag("Upgrade", "Gplay", "Control")
    }
}
