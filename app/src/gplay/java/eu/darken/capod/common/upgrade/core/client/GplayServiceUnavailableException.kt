package eu.darken.capod.common.upgrade.core.client

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

class GplayServiceUnavailableException(cause: Throwable) : Exception("Google Play services are unavailable.", cause),
    HasLocalizedError {
    override fun getLocalizedError(context: Context): LocalizedError {
        return LocalizedError(
            throwable = this,
            label = "Google Play Services Unavailable",
            description = context.getString(R.string.upgrades_iap_gplay_unavailable_error)
        )
    }


}