package eu.darken.capod.common.upgrade.core.billing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

class GplayServiceUnavailableException(cause: Throwable) :
    BillingException("Google Play services are unavailable.", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_unavailable_error),
        description = context.getString(R.string.upgrades_gplay_unavailable_error_description),
        // Deliberately untranslated brand name.
        fixActionLabel = "Google Play",
        // BillingManager also maps transient timeout/network failures onto this exception, so the
        // action is a GENERIC troubleshooting affordance (open Play's app info), not a diagnosis of
        // the cause. Harmless for a transient blip, and it matches the fleet's dialog.
        fixAction = { activity ->
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", GPLAY_PKG, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                log(ERROR) { "Can't launch settings intent for Google Play: $e" }
                Toast.makeText(activity, "Google Play is not installed", Toast.LENGTH_SHORT).show()
            }
        },
    )

    companion object {
        private const val GPLAY_PKG = "com.android.vending"
    }
}
