package eu.darken.capod.upgrade.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material.icons.twotone.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler

object UpgradeScreenTags {
    const val SUB_BUTTON = "upgrade.sub.button"
    const val IAP_BUTTON = "upgrade.iap.button"
    const val RESTORE_BUTTON = "upgrade.restore.button"
    const val RESTORE_BANNER = "upgrade.restore.banner"
    const val OWNER_HERO = "upgrade.owner.hero"
    const val OWNER_SUB_CARD = "upgrade.owner.subCard"
    const val OWNER_IAP_CARD = "upgrade.owner.iapCard"
    const val OWNER_WARNING = "upgrade.owner.bothOwnedWarning"
    const val MANAGE_SUB_BUTTON = "upgrade.manageSub.button"
    const val SWITCH_CARD = "upgrade.switch.card"
    const val SWITCH_BUTTON = "upgrade.switch.button"
    const val GRACE_CARD = "upgrade.grace.card"
    const val GRACE_RESTORE_BUTTON = "upgrade.grace.restore"
    const val DIALOG_STILL_RENEWING = "upgrade.dialog.stillRenewing"
    const val DIALOG_CHECK_FAILED = "upgrade.dialog.checkFailed"
    const val DIALOG_RESTORE_FAILED = "upgrade.dialog.restoreFailed"
    const val BENEFITS = "upgrade.benefits"
}

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
        RestoreFailedDialog(onDismiss = { showRestoreFailedDialog = false })
    }
}

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

@Composable
internal fun RestoreFailedDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(UpgradeScreenTags.DIALOG_RESTORE_FAILED),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action)) },
        text = {
            Text(
                text = listOf(
                    stringResource(R.string.upgrade_screen_restore_purchase_message),
                    stringResource(R.string.upgrade_screen_restore_troubleshooting_msg),
                    stringResource(R.string.upgrade_screen_restore_multiaccount_hint),
                    stringResource(R.string.upgrade_screen_restore_webinstall_hint),
                    stringResource(R.string.upgrade_screen_restore_sync_patience_hint),
                ).joinToString("\n\n")
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.general_done_action))
            }
        },
    )
}

private data class Benefit(val icon: ImageVector, val textRes: Int)

@Composable
fun UpgradeScreen(
    state: UpgradeUiState,
    onNavigateUp: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {}
                    Image(
                        painter = painterResource(R.drawable.splash_graphic2),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = buildAnnotatedString {
                        append("CAPod ")
                        withStyle(SpanStyle(color = colorResource(R.color.brand_tertiary), fontWeight = FontWeight.Bold)) {
                            append("Pro")
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                when {
                    state is UpgradeUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }

                    state is UpgradeUiState.Loaded && state.ownership.ownsAnything -> OwnerContent(
                        state = state,
                        onIap = onIap,
                        onRestore = onRestore,
                        onManageSubscription = onManageSubscription,
                    )

                    state is UpgradeUiState.Loaded && state.grace != null -> GraceContent(
                        state = state,
                        onSubscription = onSubscription,
                        onSubscriptionTrial = onSubscriptionTrial,
                        onIap = onIap,
                        onRestore = onRestore,
                    )

                    state is UpgradeUiState.Loaded -> AcquisitionContent(
                        state = state,
                        onSubscription = onSubscription,
                        onSubscriptionTrial = onSubscriptionTrial,
                        onIap = onIap,
                        onRestore = onRestore,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                    contentDescription = null,
                )
            }
        }
    }
}

// --- Owner view: status instead of sales pitch ---

@Composable
private fun OwnerContent(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val ownership = state.ownership
    val subscription = ownership.subscription

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.OWNER_HERO),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                // The permanent purchase wins the headline when both are owned.
                text = stringResource(
                    if (ownership.hasIap) R.string.upgrade_screen_owned_hero_iap_body
                    else R.string.upgrade_screen_owned_hero_sub_body
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (subscription != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UpgradeScreenTags.OWNER_SUB_CARD),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_sub_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // No dates: the client can't know the expiry/renewal date, only the intent.
                    text = stringResource(
                        if (subscription.isAutoRenewing) R.string.upgrade_screen_owned_sub_renewing_body
                        else R.string.upgrade_screen_owned_sub_not_renewing_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
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
    }

    if (ownership.hasIap) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UpgradeScreenTags.OWNER_IAP_CARD),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_iap_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_iap_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (ownership.hasIap && subscription?.isAutoRenewing == true) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UpgradeScreenTags.OWNER_WARNING),
        ) {
            Text(
                text = stringResource(R.string.upgrade_screen_owned_both_renewing_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (subscription != null && !ownership.hasIap) {
        Spacer(modifier = Modifier.height(12.dp))
        SwitchOfferCard(state = state, onIap = onIap)
    }

    Spacer(modifier = Modifier.height(24.dp))
    RestoreSection(
        restoreInProgress = state.restoreInProgress,
        verificationInProgress = state.verificationInProgress,
        onRestore = onRestore,
    )
}

// Spinner-prefixed button label shared by all busy-capable buttons on this screen.
@Composable
private fun BusyButtonLabel(busy: Boolean, text: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text)
}

@Composable
private fun SwitchOfferCard(
    state: UpgradeUiState.Loaded,
    onIap: () -> Unit,
) {
    val subscription = state.ownership.subscription ?: return
    val switchUnlocked = !subscription.isAutoRenewing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.SWITCH_CARD),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_iap_offer_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.iapPrice != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_iap_action_hint, state.iapPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (switchUnlocked) R.string.upgrade_screen_owned_iap_purchase_note
                    else R.string.upgrade_screen_owned_iap_locked_note
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onIap,
                // Deliberately NOT gated on price availability: the pricing query may have failed
                // while the purchase would work (the billing flow re-queries details on launch).
                // The fail-closed SUBS verification happens on tap, in the ViewModel.
                enabled = switchUnlocked && state.settled && !state.verificationInProgress && !state.restoreInProgress,
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

// --- Grace view: Pro is active but Play can't confirm the purchase right now ---

@Composable
private fun GraceContent(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
) {
    val grace = state.grace ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.GRACE_CARD),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (grace.showDiagnostics) {
                    Icon(
                        imageVector = Icons.TwoTone.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.upgrade_screen_grace_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (grace.showDiagnostics) R.string.upgrade_screen_grace_body
                    else R.string.upgrade_screen_grace_body_short
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (grace.showDiagnostics) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRestore,
                    enabled = !state.restoreInProgress && !state.verificationInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.GRACE_RESTORE_BUTTON),
                ) {
                    BusyButtonLabel(
                        busy = state.restoreInProgress,
                        text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                    )
                }
            }
        }
    }

    // Quiet phase: no offers, no pitch — a Play hiccup usually resolves itself and showing buy
    // buttons to a paying user is confusing. Diagnostics phase: the offers return, so an actually
    // expired subscriber can switch to the one-time purchase without waiting out the grace window.
    if (grace.showDiagnostics) {
        Spacer(modifier = Modifier.height(24.dp))
        PricingContent(
            state = state,
            onSubscription = onSubscription,
            onSubscriptionTrial = onSubscriptionTrial,
            onIap = onIap,
            onRestore = onRestore,
            showRestore = false,
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
) {
    val benefits = listOf(
        Benefit(Icons.TwoTone.Palette, R.string.upgrade_benefit_themes),
        Benefit(Icons.TwoTone.PlayCircle, R.string.upgrade_benefit_autoplay),

        Benefit(Icons.AutoMirrored.TwoTone.Message, R.string.upgrade_benefit_popups),
        Benefit(Icons.TwoTone.Widgets, R.string.upgrade_benefit_widgets),
        Benefit(Icons.TwoTone.Tune, R.string.upgrade_benefit_device_settings),
        Benefit(Icons.TwoTone.Headphones, R.string.upgrade_benefit_device_controls),
        Benefit(Icons.TwoTone.Favorite, R.string.upgrade_benefit_support),
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.upgrade_preamble),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (state.showRestoreBanner) {
        RestoreBanner(
            onRestore = onRestore,
            restoreInProgress = state.restoreInProgress,
            verificationInProgress = state.verificationInProgress,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.BENEFITS),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            benefits.forEach { benefit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
        }
    }

    Text(
        text = stringResource(R.string.upgrade_benefit_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )

    Spacer(modifier = Modifier.height(24.dp))

    PricingContent(
        state = state,
        onSubscription = onSubscription,
        onSubscriptionTrial = onSubscriptionTrial,
        onIap = onIap,
        onRestore = onRestore,
    )
}

@Composable
private fun RestoreBanner(
    onRestore: () -> Unit,
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.RESTORE_BANNER),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrade_screen_restore_banner_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress && !verificationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BusyButtonLabel(
                    busy = restoreInProgress,
                    text = stringResource(R.string.upgrade_screen_restore_purchase_action),
                )
            }
        }
    }
}

@Composable
private fun RestoreSection(
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
    onRestore: () -> Unit,
) {
    Text(
        text = stringResource(R.string.upgrade_screen_restore_status_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.upgrade_screen_restore_status_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRestore,
        enabled = !restoreInProgress && !verificationInProgress,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.RESTORE_BUTTON),
    ) {
        BusyButtonLabel(
            busy = restoreInProgress,
            text = stringResource(R.string.upgrade_screen_restore_purchase_action),
        )
    }
}

@Composable
private fun PricingContent(
    state: UpgradeUiState.Loaded,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
    showRestore: Boolean = true,
) {
    // Subscription button (primary)
    if (state.subAvailable) {
        val subscriptionAction =
            if (state.subscriptionAction == SubscriptionAction.TRIAL) onSubscriptionTrial else onSubscription

        val subscriptionLabel = if (state.subscriptionAction == SubscriptionAction.TRIAL) {
            stringResource(R.string.upgrade_screen_subscription_trial_action)
        } else {
            stringResource(R.string.upgrade_screen_subscription_action)
        }

        Button(
            onClick = subscriptionAction,
            enabled = state.subscriptionEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UpgradeScreenTags.SUB_BUTTON),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = subscriptionLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (state.subscriptionPrice != null) {
            Text(
                text = stringResource(R.string.upgrade_screen_subscription_action_hint, state.subscriptionPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // IAP button (secondary)
    if (state.iapAvailable) {
        FilledTonalButton(
            onClick = onIap,
            enabled = state.iapEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UpgradeScreenTags.IAP_BUTTON),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.upgrade_screen_iap_action),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (state.iapPrice != null) {
            Text(
                text = stringResource(R.string.upgrade_screen_iap_action_hint, state.iapPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    // If no details loaded at all, show a simple fallback upgrade button
    if (!state.subAvailable && !state.iapAvailable) {
        Button(
            onClick = onIap,
            enabled = state.iapEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UpgradeScreenTags.IAP_BUTTON),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.general_upgrade_action),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        // Don't promise a free trial when Play didn't return the trial offer (e.g. returning
        // subscribers) — the subscribe button already falls back accordingly.
        text = stringResource(
            if (state.subscriptionAction == SubscriptionAction.TRIAL) R.string.upgrade_screen_options_description
            else R.string.upgrade_screen_options_description_no_trial
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    if (showRestore) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRestore,
            enabled = !state.restoreInProgress && !state.verificationInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(UpgradeScreenTags.RESTORE_BUTTON),
            shape = RoundedCornerShape(12.dp),
        ) {
            BusyButtonLabel(
                busy = state.restoreInProgress,
                text = stringResource(R.string.upgrade_screen_restore_purchase_action),
            )
        }
    }
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
private fun UpgradeScreenOwnerSubNotRenewingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(
            ownership = Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
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
private fun UpgradeScreenOwnerBothRenewingPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(
            ownership = Ownership(
                hasIap = true,
                subscription = SubscriptionOwnership(isAutoRenewing = true),
            ),
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
private fun UpgradeScreenGraceQuietPreview() = PreviewWrapper {
    UpgradeScreen(
        state = previewLoaded(grace = GraceHint(showDiagnostics = false)),
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
