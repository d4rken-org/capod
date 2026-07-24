package eu.darken.capod.upgrade.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler

@Composable
fun UpgradeScreenHost(
    manage: Boolean = false,
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    // Bind the route BEFORE anything else can race the auto-close collector.
    LaunchedEffect(manage) { vm.initialize(manage) }

    val context = LocalContext.current
    val activity = context as? Activity
    val state by vm.state.collectAsState()

    var showStillRenewingDialog by remember { mutableStateOf(false) }
    var showCheckFailedDialog by remember { mutableStateOf(false) }
    var showRestoreFailedDialog by remember { mutableStateOf(false) }

    val restoreSuccessMessage = stringResource(R.string.upgrade_screen_restore_success_message)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UpgradeViewModel.UpgradeEvent.RestoreFailed -> showRestoreFailedDialog = true
                UpgradeViewModel.UpgradeEvent.RestoreSucceeded -> {
                    Toast.makeText(context, restoreSuccessMessage, Toast.LENGTH_LONG).show()
                }

                UpgradeViewModel.UpgradeEvent.SubscriptionStillRenewing -> showStillRenewingDialog = true
                UpgradeViewModel.UpgradeEvent.SubscriptionCheckFailed -> showCheckFailedDialog = true
            }
        }
    }

    // Returning from Play's subscription-management page must refresh the renewal state promptly.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    UpgradeScreen(
        state = state,
        onNavigateUp = { vm.navUp() },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onIap = { activity?.let { vm.onGoIap(it) } },
        onRestore = { vm.restorePurchase() },
        onRetry = { vm.retrySkuQuery() },
        onManageSubscription = { vm.onManageSubscription() },
    )

    if (showStillRenewingDialog) {
        StillRenewingDialog(
            onManage = {
                showStillRenewingDialog = false
                vm.onManageSubscription()
            },
            onDismiss = { showStillRenewingDialog = false },
        )
    }

    if (showCheckFailedDialog) {
        CheckFailedDialog(onDismiss = { showCheckFailedDialog = false })
    }

    if (showRestoreFailedDialog) {
        RestoreFailedDialog(
            onDismiss = { showRestoreFailedDialog = false },
            onContactSupport = {
                // Dismiss before navigating so the dialog can't linger if this entry is retained.
                showRestoreFailedDialog = false
                vm.onContactSupport()
            },
        )
    }
}

@Composable
fun UpgradeScreen(
    state: UpgradeUiState,
    onNavigateUp: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
    onRetry: () -> Unit = {},
) {
    UpgradeScreenContainer(onNavigateUp = onNavigateUp) {
        UpgradeHeader(graphicSize = 80.dp)

        Text(
            text = upgradeScreenTitle(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state is UpgradeUiState.Loading -> UpgradeLoadingBlock(
                modifier = Modifier.testTag(UpgradeScreenTags.LOADING),
            )

            state is UpgradeUiState.Loaded && state.ownership.ownsAnything -> UpgradeOwnershipContent(
                state = state,
                onIap = onIap,
                onManageSubscription = onManageSubscription,
                onRestore = onRestore,
            )

            state is UpgradeUiState.Loaded && state.grace != null -> GraceContent(
                state = state,
                onSubscription = onSubscription,
                onSubscriptionTrial = onSubscriptionTrial,
                onIap = onIap,
                onRestore = onRestore,
                onRetry = onRetry,
            )

            state is UpgradeUiState.Loaded -> AcquisitionContent(
                state = state,
                onSubscription = onSubscription,
                onSubscriptionTrial = onSubscriptionTrial,
                onIap = onIap,
                onRestore = onRestore,
                onRetry = onRetry,
            )
        }
    }
}

// --- Grace view: Pro is active but Play can't confirm the purchase right now ---

@Composable
private fun GraceContent(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    val grace = state.grace ?: return

    UpgradeGraceCard(
        showDiagnostics = grace.showDiagnostics,
        onRestore = onRestore,
        restoreInProgress = state.restoreInProgress,
        verificationInProgress = state.verificationInProgress,
    )

    // Quiet phase: no offers, no pitch — a Play hiccup usually resolves itself and showing buy
    // buttons to a paying user is confusing. Diagnostics phase: the offers return so an actually
    // expired subscriber can switch to the one-time purchase without waiting out the grace window.
    if (grace.showDiagnostics) {
        UpgradeOffersBox(
            state = state,
            onSubscription = onSubscription,
            onSubscriptionTrial = onSubscriptionTrial,
            onIap = onIap,
            onRetry = onRetry,
        )
    }
}

// --- Acquisition view: the sales pitch ---

@Composable
private fun AcquisitionContent(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.upgrade_preamble),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }

    // Returning buyer: prominent, emphasized, and the ONLY restore affordance — a second one below
    // would make the screen feel uncertain about its own advice.
    if (state.showRestoreBanner) {
        UpgradeRestoreSection(
            title = stringResource(R.string.upgrade_screen_restore_banner_title),
            body = stringResource(R.string.upgrade_screen_restore_banner_body),
            onRestore = onRestore,
            modifier = Modifier.testTag(UpgradeScreenTags.RESTORE_BANNER),
            restoreInProgress = state.restoreInProgress,
            enabled = !state.restoreInProgress && !state.verificationInProgress,
            emphasized = true,
            restoreTag = UpgradeScreenTags.RESTORE_BANNER_ACTION,
        )
    }

    UpgradeBenefitsCard()

    UpgradeOffersBox(
        state = state,
        onSubscription = onSubscription,
        onSubscriptionTrial = onSubscriptionTrial,
        onIap = onIap,
        onRetry = onRetry,
    )

    // Restore is account reconciliation, not an offer — its own described section, after the
    // offers. Only for plain acquisition: returning buyers get the emphasized section up top.
    if (!state.showRestoreBanner) {
        UpgradeRestoreSection(
            title = stringResource(R.string.upgrade_screen_restore_banner_title),
            body = stringResource(R.string.upgrade_screen_restore_body),
            onRestore = onRestore,
            restoreInProgress = state.restoreInProgress,
            enabled = !state.restoreInProgress && !state.verificationInProgress,
        )
    }
}

// The offers card, cross-faded ONLY on the offers phase so ordinary Loaded→Loaded updates
// (settled/restore/verification) recompose in place instead of animating a duplicate-tagged,
// briefly-interactive copy of the whole box.
private enum class OffersPhase { LOADED, SETTLING, NO_OFFERS }

private fun UpgradeUiState.Loaded.offersPhase(): OffersPhase = when {
    subAvailable || iapAvailable -> OffersPhase.LOADED
    // Before the first billing reconciliation (or while a query is still running) missing offers are
    // warm-up, not an outage: on entry upgradeInfo looks like a non-owner until Play answers, so an
    // owner would otherwise flash the red "unavailable" card for the split second before their
    // status resolves. Show a neutral spinner until we're actually sure Play returned nothing.
    !settled || skuQueryInProgress -> OffersPhase.SETTLING
    else -> OffersPhase.NO_OFFERS
}

@Composable
private fun UpgradeOffersBox(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRetry: () -> Unit,
) {
    // Key on the phase but carry the whole state: each content instance must render ITS OWN state
    // snapshot, or a crossfade would recompose the outgoing card with the incoming state (empty
    // offers fading out, etc.). Same-phase Loaded→Loaded updates share a key and recompose in place.
    AnimatedContent(
        targetState = state,
        contentKey = { it.offersPhase() },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "upgrade-offers",
        modifier = Modifier.fillMaxWidth(),
    ) { animatedState ->
        when (animatedState.offersPhase()) {
            OffersPhase.LOADED -> LoadedOffers(
                state = animatedState,
                onSubscription = onSubscription,
                onSubscriptionTrial = onSubscriptionTrial,
                onIap = onIap,
            )

            OffersPhase.SETTLING -> UpgradeActionCard {
                UpgradeLoadingBlock(modifier = Modifier.testTag(UpgradeScreenTags.OFFERS_SETTLING))
            }

            OffersPhase.NO_OFFERS -> NoOffersCard(
                state = animatedState,
                onIap = onIap,
                onRetry = onRetry,
            )
        }
    }
}

// Cold/slow Play returned no product details. Keep both a fallback purchase action (the billing
// flow re-queries details on launch, so it can still work) AND a Retry that reloads the offers.
@Composable
private fun NoOffersCard(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onRetry: () -> Unit,
) {
    UpgradeInlineStateCard(
        title = stringResource(R.string.upgrade_screen_offers_unavailable_title),
        body = stringResource(R.string.upgrade_screen_offers_unavailable_message),
        icon = Icons.TwoTone.WarningAmber,
        modifier = Modifier.testTag(UpgradeScreenTags.OFFERS_UNAVAILABLE),
    ) {
        Button(
            onClick = onIap,
            enabled = state.iapEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UpgradeScreenTags.IAP_BUTTON),
        ) {
            Text(text = stringResource(R.string.general_upgrade_action))
        }
        OutlinedButton(
            onClick = onRetry,
            // Disabled while a query runs so repeated taps can't thrash the query flow.
            enabled = !state.skuQueryInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UpgradeScreenTags.RETRY_BUTTON),
        ) {
            Text(text = stringResource(R.string.general_retry_action))
        }
    }
}

// --- Dialogs kept on the screen (restore-failed lives in UpgradeRestore.kt) ---

@Composable
internal fun StillRenewingDialog(
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(UpgradeScreenTags.DIALOG_STILL_RENEWING),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_sub_still_renewing_title)) },
        text = { Text(text = stringResource(R.string.upgrade_screen_sub_still_renewing_message)) },
        confirmButton = {
            TextButton(
                onClick = onManage,
                modifier = Modifier.testTag(UpgradeScreenTags.MANAGE_SUB_BUTTON),
            ) {
                Text(text = stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_cancel_action))
            }
        },
    )
}

@Composable
internal fun CheckFailedDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(UpgradeScreenTags.DIALOG_CHECK_FAILED),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_sub_check_failed_title)) },
        text = { Text(text = stringResource(R.string.upgrade_screen_sub_check_failed_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_done_action))
            }
        },
    )
}

// --- Previews ---

private fun previewLoaded(
    ownership: Ownership = Ownership(),
    grace: GraceHint? = null,
    showRestoreBanner: Boolean = false,
) = UpgradeUiState.Loaded(
    subscriptionAction = SubscriptionAction.TRIAL,
    subscriptionEnabled = true,
    subscriptionPrice = "€3.49",
    iapEnabled = true,
    iapPrice = "€6.49",
    ownership = ownership,
    grace = grace,
    showRestoreBanner = showRestoreBanner,
)

@Preview2
@Composable
private fun UpgradeScreenPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
        onManageSubscription = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenReturningBuyerPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(showRestoreBanner = true),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
        onManageSubscription = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenOwnerSubRenewingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(
            ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true)),
        ),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
        onManageSubscription = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenOwnerIapPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(ownership = Ownership(hasIap = true)),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
        onManageSubscription = {},
    )
}

@Preview2
@Composable
private fun UpgradeScreenGraceDiagnosticsPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(grace = GraceHint(showDiagnostics = true)),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
        onManageSubscription = {},
    )
}
