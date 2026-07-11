package eu.darken.capod.common.upgrade.core.client

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

// Google Play reports the product as already owned when trying to launch the purchase flow.
// UpgradeRepoGplay auto-handles this by restoring; this error only surfaces if that fails.
class ItemAlreadyOwnedBillingException(cause: Throwable) :
    Exception("Already owned according to Google Play.", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_already_owned_label),
        description = context.getString(R.string.upgrades_gplay_already_owned_description)
    )
}
