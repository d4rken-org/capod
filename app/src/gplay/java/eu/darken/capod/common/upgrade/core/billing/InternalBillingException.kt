package eu.darken.capod.common.upgrade.core.billing

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

class InternalBillingException(cause: Throwable) :
    BillingException("An internal Google Play error occurred.", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_internal_error_title),
        description = context.getString(R.string.upgrades_gplay_internal_error_description),
    )
}
