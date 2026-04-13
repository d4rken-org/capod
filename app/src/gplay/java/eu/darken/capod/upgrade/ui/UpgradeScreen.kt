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
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Widgets
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler


@Composable
fun UpgradeScreenHost(vm: UpgradeViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? Activity
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UpgradeViewModel.UpgradeEvent.RestoreFailed -> {
                    Toast.makeText(
                        context,
                        R.string.upgrade_screen_restore_purchase_message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.billingEvents.collect { event ->
            activity ?: return@collect
            when (event) {
                UpgradeViewModel.BillingEvent.LaunchIap -> vm.launchBillingIap(activity)
                UpgradeViewModel.BillingEvent.LaunchSubscription -> vm.launchBillingSubscription(activity)
                UpgradeViewModel.BillingEvent.LaunchSubscriptionTrial -> vm.launchBillingSubscriptionTrial(activity)
            }
        }
    }

    UpgradeScreen(
        state = state,
        onNavigateUp = { vm.navUp() },
        onSubscription = { vm.onGoSubscription() },
        onSubscriptionTrial = { vm.onGoSubscriptionTrial() },
        onIap = { vm.onGoIap() },
        onRestore = { vm.restorePurchase() },
    )
}

private data class Benefit(val icon: ImageVector, val textRes: Int)

@Composable
fun UpgradeScreen(
    state: UpgradeViewModel.Pricing?,
    onNavigateUp: () -> Unit,
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
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

                if (state == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else {
                    PricingContent(
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

@Composable
private fun PricingContent(
    state: UpgradeViewModel.Pricing,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onIap: () -> Unit,
    onRestore: () -> Unit,
) {
    // Subscription button (primary)
    if (state.subAvailable) {
        val subscriptionAction = if (state.hasTrialOffer) onSubscriptionTrial else onSubscription

        val subscriptionLabel = if (state.hasTrialOffer) {
            stringResource(R.string.upgrade_screen_subscription_trial_action)
        } else {
            stringResource(R.string.upgrade_screen_subscription_action)
        }

        Button(
            onClick = subscriptionAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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

        if (state.subPrice != null) {
            Text(
                text = stringResource(R.string.upgrade_screen_subscription_action_hint, state.subPrice),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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
        text = stringResource(R.string.upgrade_screen_options_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = onRestore,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text = stringResource(R.string.upgrade_screen_restore_purchase_action))
    }
}

@Preview2
@Composable
private fun UpgradeScreenPreview() = PreviewWrapper {
    UpgradeScreen(
        state = UpgradeViewModel.Pricing(
            subPrice = "€3.49",
            iapPrice = "€6.49",
            hasTrialOffer = true,
        ),
        onNavigateUp = {},
        onSubscription = {},
        onSubscriptionTrial = {},
        onIap = {},
        onRestore = {},
    )
}
