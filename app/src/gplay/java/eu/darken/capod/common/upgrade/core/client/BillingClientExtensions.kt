package eu.darken.capod.common.upgrade.core.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult

internal val BillingResult.isSuccess: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK

internal val BillingResult.isGplayUnavailableTemporary: Boolean
    get() = setOf(
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT
    ).contains(responseCode)

internal val BillingResult.isGplayUnavailablePermanent: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE