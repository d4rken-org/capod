package eu.darken.capod.common.upgrade.core.billing

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

class NetworkBillingException(cause: Throwable) :
    BillingException("Unable to connect to Google Play.", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_network_error_title),
        description = context.getString(R.string.upgrades_gplay_network_error_description),
    )
}
