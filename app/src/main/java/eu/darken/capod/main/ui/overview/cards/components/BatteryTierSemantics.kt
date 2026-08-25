package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import eu.darken.capod.R
import eu.darken.capod.monitor.core.battery.BatteryTier

/**
 * Announces a low or critical level on a battery slot — crossing either threshold is otherwise
 * only visible as a colour change. A no-op at the other tiers, which make no claim to announce.
 */
@Composable
fun Modifier.batteryTierState(tier: BatteryTier): Modifier {
    val description = when (tier) {
        BatteryTier.CRITICAL -> stringResource(R.string.battery_state_critical_cd)
        BatteryTier.WARN -> stringResource(R.string.battery_state_low_cd)
        BatteryTier.UNKNOWN, BatteryTier.GOOD -> return this
    }
    return semantics(mergeDescendants = true) { stateDescription = description }
}
