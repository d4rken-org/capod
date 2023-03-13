package eu.darken.capod.common.upgrade.core.data

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.core.client.BillingClientConnectionProvider
import eu.darken.capod.common.upgrade.core.client.BillingClientException
import eu.darken.capod.common.upgrade.core.client.BillingException
import eu.darken.capod.common.upgrade.core.client.GplayServiceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingDataRepo @Inject constructor(
    billingClientConnectionProvider: BillingClientConnectionProvider,
    @AppScope private val scope: CoroutineScope,
) {

    private val connectionProvider = billingClientConnectionProvider.connection
        .catch { log(TAG, ERROR) { "Unable to provide client connection:\n${it.asLog()}" } }
        .replayingShare(scope)

    val billingData: Flow<BillingData> = connectionProvider
        .flatMapLatest { it.purchases }
        .map { BillingData(purchases = it) }
        .setupCommonEventHandlers(TAG) { "iapData" }
        .replayingShare(scope)

    init {
        connectionProvider
            .flatMapLatest { client ->
                client.purchases.map { client to it }
            }
            .onEach { (client, purchases) ->
                purchases
                    .filter {
                        val needsAck = !it.isAcknowledged

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "Already ACK'ed: $it" }

                        needsAck
                    }
                    .forEach {
                        log(TAG, INFO) { "Acknowledging purchase: $it" }

                        val ackResult = client.acknowledgePurchase(it)
                        if (ackResult.responseCode != BillingResponseCode.OK) {
                            throw BillingClientException(ackResult)
                        }
                    }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                log(TAG, ERROR) { "Failed to ancknowledge purchase: ${cause.asLog()}" }

                if (cause is CancellationException) {
                    log(TAG) { "Ack was cancelled (appScope?) cancelled." }
                    return@retryWhen false
                }

                if (attempt > 5) {
                    log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }
                if (cause !is BillingException) {
                    log(TAG, WARN) { "Unknown exception type: $cause" }
                    return@retryWhen false
                }

                if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                    log(TAG) { "Got BILLING_UNAVAILABLE while trying to ACK purchase." }
                    return@retryWhen false
                }

                log(TAG) { "Will retry ACK" }
                delay(3000 * attempt)
                true
            }
            .launchIn(scope)
    }

    suspend fun getIapData(): BillingData = try {
        val clientConnection = connectionProvider.first()
        val iaps = clientConnection.queryPurchases()

        BillingData(
            purchases = iaps
        )
    } catch (e: Exception) {
        throw e.tryMapUserFriendly()
    }

    suspend fun startIapFlow(activity: Activity, sku: Sku) {
        try {
            val clientConnection = connectionProvider.first()
            clientConnection.launchBillingFlow(activity, sku)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to start IAP flow:\n${e.asLog()}" }
            val ignoredCodes = listOf(3, 6)
            if (e !is BillingClientException || !e.result.responseCode.let { ignoredCodes.contains(it) }) {
                Bugs.report(TAG, "IAP flow failed for $sku", e)
            }

            throw e.tryMapUserFriendly()
        }
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "DataRepo")

        internal fun Throwable.tryMapUserFriendly(): Throwable {
            if (this !is BillingClientException) return this

            return when (result.responseCode) {
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_TIMEOUT -> GplayServiceUnavailableException(this)
                else -> this
            }
        }
    }
}
