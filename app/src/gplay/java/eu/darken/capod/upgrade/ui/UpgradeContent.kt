package eu.darken.capod.upgrade.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.capod.R

// Test tags for the upgrade screen. Existing values are kept verbatim so the behavioral Compose
// tests keep pointing at the same nodes across the offercard restructure; new surfaces get new tags.
object UpgradeScreenTags {
    const val SUB_BUTTON = "upgrade.sub.button"
    const val IAP_BUTTON = "upgrade.iap.button"
    const val RESTORE_BUTTON = "upgrade.restore.button"
    const val RETRY_BUTTON = "upgrade.retry.button"
    const val RESTORE_BANNER = "upgrade.restore.banner"
    const val RESTORE_BANNER_ACTION = "upgrade.restore.banner.action"
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
    const val CONTACT_SUPPORT_BUTTON = "upgrade.dialog.contactSupport"
    const val BENEFITS = "upgrade.benefits"
    const val OFFERS = "upgrade.offers"
    const val OFFERS_UNAVAILABLE = "upgrade.offers.unavailable"
    const val OFFERS_SETTLING = "upgrade.offers.settling"
    const val LOADING = "upgrade.loading"
}

// "CAPod Pro" with the postfix highlighted in the upgraded brand color — the same treatment the
// dashboard title uses. Split from the composed resource so translations can reorder the words.
@Composable
internal fun upgradeScreenTitle(): AnnotatedString {
    val parts = stringResource(R.string.app_name_pro).split(" ").filter { it.isNotEmpty() }
    val highlight = colorResource(R.color.brand_tertiary)
    return buildAnnotatedString {
        if (parts.size == 2) {
            append("${parts[0]} ")
            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) { append(parts[1]) }
        } else {
            append(stringResource(R.string.app_name_pro))
        }
    }
}

// The screen shell: a plain surface with the floating back arrow (capod convention — the upgrade
// TopAppBar was removed in 7f2b6976 to avoid clipping the header graphic) over a centered,
// width-capped scrolling column. Sections are spaced uniformly; the caller supplies the header.
@Composable
internal fun UpgradeScreenContainer(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        // widthIn BEFORE fillMaxWidth: reversed, fillMaxWidth would pin the min to
                        // the full screen and the 560dp cap would never take effect on wide screens.
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 48.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content,
                )
            }
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                // Matches capod's app-wide back-button convention (no navigate-up string exists).
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                    contentDescription = null,
                )
            }
        }
    }
}

// The header graphic in a tinted circle. capod has no mascot; splash_graphic2 stands in for it in
// both the acquisition header and the owned hero.
@Composable
internal fun UpgradeHeader(
    graphicSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(graphicSize + 40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {}
            Image(
                painter = painterResource(R.drawable.splash_graphic2),
                contentDescription = null,
                modifier = Modifier.size(graphicSize),
            )
        }
    }
}

@Composable
internal fun UpgradeSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    colors: CardColors? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpgradeSectionHeader(title = title, icon = icon, iconTint = iconTint, leading = leading)
            content()
        }
    }
}

@Composable
internal fun UpgradeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) {
            leading()
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.primary else iconTint,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun UpgradeSectionBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun UpgradeHintText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

// Container for the offers block: a raised card that resizes smoothly as offer rows appear/vanish.
@Composable
internal fun UpgradeActionCard(
    modifier: Modifier = Modifier,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun UpgradeLoadingBlock(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

// Error-container styled card, used for the "prices couldn't load" fallback.
@Composable
internal fun UpgradeInlineStateCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    UpgradeSectionCard(
        title = title,
        icon = icon,
        modifier = modifier,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        content()
    }
}

// Spinner-prefixed button label shared by all busy-capable buttons on this screen.
@Composable
internal fun BusyButtonLabel(busy: Boolean, text: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text)
}
