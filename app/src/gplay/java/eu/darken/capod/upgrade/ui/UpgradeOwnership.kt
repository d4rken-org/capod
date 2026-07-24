package eu.darken.capod.upgrade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.capod.R

// Ownership presentation for users who already own Pro. Subscribers without the one-time purchase
// see the switch offer — LOCKED while the subscription still renews, so buying it can't stack with
// an upcoming renewal.
@Composable
internal fun UpgradeOwnershipContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestore: () -> Unit,
) {
    val ownership = state.ownership
    val subscription = ownership.subscription
    val restoreEnabled = !state.restoreInProgress && !state.verificationInProgress

    UpgradeOwnedHero(ownership = ownership)

    if (ownership.hasIap) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_owned_iap_title),
            icon = Icons.TwoTone.Verified,
            modifier = Modifier.testTag(UpgradeScreenTags.OWNER_IAP_CARD),
        ) {
            UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_owned_iap_body))
        }
    }

    if (subscription != null) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_owned_sub_title),
            icon = Icons.TwoTone.Autorenew,
            modifier = Modifier.testTag(UpgradeScreenTags.OWNER_SUB_CARD),
        ) {
            UpgradeSectionBody(
                text = stringResource(
                    // No dates: the client can't know expiry/renewal, only intent.
                    if (subscription.isAutoRenewing) R.string.upgrade_screen_owned_sub_renewing_body
                    else R.string.upgrade_screen_owned_sub_not_renewing_body
                ),
            )
            if (subscription.isAutoRenewing && ownership.hasIap) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_both_renewing_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(UpgradeScreenTags.OWNER_WARNING),
                )
            }
            OutlinedButton(
                onClick = onManageSubscription,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.MANAGE_SUB_BUTTON),
            ) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        }
    }

    if (subscription != null && !ownership.hasIap) {
        // The switch path as a visible artifact, not just prose: while the subscription still
        // renews the offer is shown LOCKED with the unlock condition. `iapEnabled` centralizes
        // settled/restore/verification/ownership gating; the renewal state adds the lock.
        val switchUnlocked = !subscription.isAutoRenewing
        UpgradeActionCard(modifier = Modifier.testTag(UpgradeScreenTags.SWITCH_CARD)) {
            UpgradeOfferRow(
                title = stringResource(R.string.upgrade_screen_iap_offer_title),
                price = state.iapPrice,
                hint = stringResource(
                    if (switchUnlocked) R.string.upgrade_screen_owned_iap_purchase_note
                    else R.string.upgrade_screen_owned_iap_locked_note
                ),
            ) {
                Button(
                    onClick = onIap,
                    enabled = switchUnlocked && state.iapEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.SWITCH_BUTTON),
                ) {
                    BusyButtonLabel(
                        busy = state.verificationInProgress,
                        text = stringResource(R.string.upgrade_screen_iap_action),
                    )
                }
            }
        }
    }

    // Framed as a status re-check; support is offered only by the failed-restore dialog.
    UpgradeRestoreSection(
        title = stringResource(R.string.upgrade_screen_restore_status_title),
        body = stringResource(R.string.upgrade_screen_restore_status_body),
        onRestore = onRestore,
        restoreInProgress = state.restoreInProgress,
        enabled = restoreEnabled,
    )
}

// The "you have it" moment: header graphic + congrats in one hero card, with the variant
// (subscription vs one-time) spelled out. The per-purchase cards below carry details and actions.
@Composable
private fun UpgradeOwnedHero(
    ownership: Ownership,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.OWNER_HERO),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.splash_graphic2),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    // The permanent purchase is the meaningful one when both are owned.
                    text = stringResource(
                        if (ownership.hasIap) R.string.upgrade_screen_owned_hero_iap_body
                        else R.string.upgrade_screen_owned_hero_sub_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// Shown on the acquisition view while Pro is active purely via the local grace window. Calm
// reassurance, not a warning. Stage 1 confirms Pro is intact (spinner header); stage 2 (after the
// episode aged past the threshold) explains and offers restore (static icon + button).
@Composable
internal fun UpgradeGraceCard(
    showDiagnostics: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    restoreInProgress: Boolean = false,
    verificationInProgress: Boolean = false,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_screen_grace_title),
        icon = Icons.TwoTone.Verified,
        modifier = modifier.testTag(UpgradeScreenTags.GRACE_CARD),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        leading = if (showDiagnostics) null else {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        },
    ) {
        Text(
            text = stringResource(
                if (showDiagnostics) R.string.upgrade_screen_grace_body
                else R.string.upgrade_screen_grace_body_short
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (showDiagnostics) {
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress && !verificationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON),
            ) {
                BusyButtonLabel(
                    busy = restoreInProgress,
                    text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                )
            }
        }
    }
}
