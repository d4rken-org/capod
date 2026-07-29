package eu.darken.capod.common.upgrade.core.billing

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

class ItemAlreadyOwnedBillingException(cause: Throwable) :
    BillingException("Item is already owned.", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_already_owned_label),
        description = context.getString(R.string.upgrades_gplay_already_owned_description),
    )
}
