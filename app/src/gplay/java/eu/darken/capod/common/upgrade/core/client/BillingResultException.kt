package eu.darken.capod.common.upgrade.core.client

import com.android.billingclient.api.BillingResult

class BillingResultException(val result: BillingResult) : BillingException(result.debugMessage) {

    override fun toString(): String =
        "BillingResultException(code=${result.responseCode}, message=${result.debugMessage})"
}