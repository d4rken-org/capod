package eu.darken.capod.common.upgrade.core.client

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

open class BillingException(override val message: String) : Exception(), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_billing_error_label),
        description = context.getString(R.string.upgrades_gplay_billing_error_description, message)
    )
}