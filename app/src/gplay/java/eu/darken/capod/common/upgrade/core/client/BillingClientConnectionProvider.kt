package eu.darken.capod.common.upgrade.core.client

import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.newBuilder
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.*
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingClientConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val connectionProvider: Flow<BillingClientConnection> = callbackFlow {
        val purchasePublisher = MutableStateFlow<Collection<Purchase>>(emptySet())
        // Events, not state: fresh observations feed the grace stamping (every successful query or
        // push payload counts, even if equal to the previous one — Purchase.equals would dedupe a
        // StateFlow), and failures must not be conflated away (Play reuses BillingResult instances,
        // so a repeated ITEM_ALREADY_OWNED could be a same-instance emission).
        // replay=1 on observations: the connect-time query can complete before the grace recorder
        // subscribes (construction order race) — the latest fresh observation must not be lost.
        // Failures stay replay=0: they can only originate from a purchase flow, which requires the
        // consumer to already exist, and a consumed event must not be re-delivered.
        val freshPurchaseObservations = MutableSharedFlow<Collection<Purchase>>(
            replay = 1,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val purchaseFailureEvents = MutableSharedFlow<BillingResult>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        val client = newBuilder(context).apply {
            enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            setListener { result, purchases ->
                if (result.isSuccess) {
                    log(TAG) {
                        "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    purchasePublisher.value = purchases.orEmpty()
                    freshPurchaseObservations.tryEmit(
                        purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    )
                } else {
                    log(TAG, WARN) {
                        "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    // Failures are published too: async ITEM_ALREADY_OWNED (Play telling us mid-flow
                    // that the user already owns it) drives the auto-restore in UpgradeRepoGplay.
                    purchaseFailureEvents.tryEmit(result)
                }
            }
        }.build()


        log(TAG, VERBOSE) { "startConnection(...)" }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                log(TAG, VERBOSE) {
                    "onBillingSetupFinished(code=${result.responseCode}, message=${result.debugMessage})"
                }

                when (result.responseCode) {
                    BillingResponseCode.OK -> {
                        val connection = BillingClientConnection(
                            client = client,
                            purchasesGlobal = purchasePublisher,
                            freshObservations = freshPurchaseObservations,
                            purchaseFailuresGlobal = purchaseFailureEvents,
                        )

                        trySendBlocking(connection)

                        launch {
                            try {
                                // Bounded: a hung Play callback would otherwise hold the refresh
                                // lock indefinitely and starve every later refresh on this
                                // connection (foreground, manual restore, already-owned recovery).
                                val initial = withTimeoutOrNull(INITIAL_QUERY_TIMEOUT_MS) {
                                    connection.refreshPurchases()
                                }
                                if (initial != null) log(TAG) { "Initial purchase query successful." }
                                else log(TAG, WARN) { "Initial purchase query timed out." }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Initial purchase query failed:\n${e.asLog()}" }
                            }
                        }
                    }
                    else -> {
                        close(BillingResultException(result))
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                log(TAG, VERBOSE) { "onBillingServiceDisconnected() " }
                close(BillingException("Billing service disconnected"))
            }
        })

        log(TAG) { "Awaiting close." }
        awaitClose {
            log(TAG) { "Stopping billing client connection" }
            client.endConnection()
        }
    }

    val connection: Flow<BillingClientConnection> = connectionProvider
        .setupCommonEventHandlers(TAG) { "connection" }
        .retryWhen { cause, attempt ->
            log(TAG) { "Billing client connection error: ${cause.asLog()}" }

            if (cause is CancellationException) {
                log(TAG) { "BillingClient connection cancelled." }
                return@retryWhen false
            }

            if (cause !is BillingException) {
                log(TAG, WARN) { "Unknown exception type: $cause" }
                return@retryWhen false
            }

            if (cause is BillingResultException && cause.result.isGplayUnavailablePermanent) {
                log(TAG) { "Got BILLING_UNAVAILABLE while trying to connect client." }
                return@retryWhen false
            }

            if (attempt > 5) {
                log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                return@retryWhen false
            }

            log(TAG) { "Will retry BillingClient connection... *sigh*" }
            delay(3000 * attempt)
            true
        }

    companion object {
        private const val INITIAL_QUERY_TIMEOUT_MS = 30_000L
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Client", "ConnectionProvider")
    }
}