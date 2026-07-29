package eu.darken.capod.common.upgrade.core.billing

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.error.HasLocalizedError
import eu.darken.capod.common.error.LocalizedError

/**
 * Play can't sell us this product/offer right now: it was omitted from the product-details response,
 * came back ambiguous (duplicate rows), or is reported as unavailable.
 *
 * This is a merchandising state (region, account eligibility, a withheld or revoked offer), NOT a
 * defect on our side — so it must stay off the bug-report path and surface as user-facing copy
 * instead of the raw NoSuchElementException/NPE that the strict `single`/`!!` lookups produced.
 */
class OfferUnavailableBillingException(
    val sku: Sku,
    val offer: Sku.Subscription.Offer?,
) : BillingException(
    "Google Play has no usable offer for ${sku.print()} (offer=${offer?.let { "${it.basePlanId}/${it.offerId}" }})",
), HasLocalizedError {

    override fun getLocalizedError(context: Context): LocalizedError = LocalizedError(
        throwable = this,
        label = context.getString(R.string.upgrades_gplay_offer_unavailable_title),
        description = context.getString(R.string.upgrades_gplay_offer_unavailable_description),
    )
}
