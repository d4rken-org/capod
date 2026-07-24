package eu.darken.capod.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.capod.R

// The acquisition offers card: header, offer rows, an "or" divider and footnote — but only when
// BOTH pricing models actually loaded. capod's subscriptionEnabled/iapEnabled do not encode offer
// availability (both can be true with a null price), so availability drives conditional rendering
// here while the enabled flags drive the busy/settled gating.
@Composable
internal fun LoadedOffers(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
) {
    UpgradeActionCard(modifier = Modifier.testTag(UpgradeScreenTags.OFFERS)) {
        UpgradeSectionHeader(
            title = stringResource(R.string.upgrade_screen_offers_title),
            icon = Icons.TwoTone.Stars,
        )

        val showBoth = state.subAvailable && state.iapAvailable

        if (state.subAvailable) {
            val isTrial = state.subscriptionAction == SubscriptionAction.TRIAL
            UpgradeOfferRow(
                title = stringResource(R.string.upgrade_screen_subscription_offer_title),
                price = state.subscriptionPrice,
                hint = stringResource(
                    if (isTrial) R.string.upgrade_screen_subscription_offer_body
                    else R.string.upgrade_screen_subscription_offer_body_no_trial
                ),
            ) {
                Button(
                    onClick = if (isTrial) onSubscriptionTrial else onSubscription,
                    enabled = state.subscriptionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.SUB_BUTTON),
                ) {
                    Text(
                        text = stringResource(
                            if (isTrial) R.string.upgrade_screen_subscription_trial_action
                            else R.string.upgrade_screen_subscription_action
                        ),
                    )
                }
            }
        }

        if (showBoth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.upgrade_screen_offers_or),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
        }

        if (state.iapAvailable) {
            UpgradeOfferRow(
                // Acquisition uses the neutral "One-time purchase" title; the switch-flavored
                // iap_offer_title copy stays owner-only.
                title = stringResource(R.string.upgrade_screen_owned_iap_title),
                price = state.iapPrice,
                hint = stringResource(R.string.upgrade_screen_iap_offer_body),
            ) {
                OutlinedButton(
                    onClick = onIap,
                    enabled = state.iapEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.IAP_BUTTON),
                ) {
                    BusyButtonLabel(
                        busy = state.verificationInProgress,
                        text = stringResource(R.string.upgrade_screen_iap_action),
                    )
                }
            }
        }

        if (showBoth) {
            UpgradeHintText(text = stringResource(R.string.upgrade_screen_offers_body))
        }
    }
}

// Title and price share one line ("·"-joined: direction-neutral punctuation, not translatable
// copy), terms follow as body text, then the action.
@Composable
internal fun UpgradeOfferRow(
    title: String,
    price: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = listOfNotNull(title, price).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        hint?.let { UpgradeSectionBody(text = it) }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

private data class Benefit(val icon: ImageVector, val textRes: Int)

private val BENEFITS = listOf(
    Benefit(Icons.TwoTone.Palette, R.string.upgrade_benefit_themes),
    Benefit(Icons.TwoTone.PlayCircle, R.string.upgrade_benefit_autoplay),
    Benefit(Icons.AutoMirrored.TwoTone.Message, R.string.upgrade_benefit_popups),
    Benefit(Icons.TwoTone.Widgets, R.string.upgrade_benefit_widgets),
    Benefit(Icons.TwoTone.Tune, R.string.upgrade_benefit_device_settings),
    Benefit(Icons.TwoTone.Headphones, R.string.upgrade_benefit_device_controls),
    Benefit(Icons.TwoTone.Favorite, R.string.upgrade_benefit_support),
)

// capod-specific icon benefit list (kept over SD Maid's text bullets), wrapped in a section card
// so it joins the offercard visual pattern.
@Composable
internal fun UpgradeBenefitsCard(
    modifier: Modifier = Modifier,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_screen_benefits_title),
        icon = Icons.TwoTone.AutoAwesome,
        modifier = modifier.testTag(UpgradeScreenTags.BENEFITS),
    ) {
        BENEFITS.forEach { benefit ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = benefit.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(benefit.textRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        UpgradeHintText(
            text = stringResource(R.string.upgrade_benefit_disclaimer),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
