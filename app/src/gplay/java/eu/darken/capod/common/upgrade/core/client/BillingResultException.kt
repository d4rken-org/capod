package eu.darken.capod.common.upgrade.core.client

import android.content.Context
import com.android.billingclient.api.BillingResult
import eu.darken.capod.R
import eu.darken.capod.common.error.LocalizedError

class BillingResultException(val result: BillingResult) : BillingException(result.debugMessage) {

    override fun toString(): String =
        "BillingResultException(code=${result.responseCode}, message=${result.debugMessage})"

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_billing_result_error_label),
        description = context.getString(R.string.upgrades_gplay_billing_result_error_description, result)
    )
}