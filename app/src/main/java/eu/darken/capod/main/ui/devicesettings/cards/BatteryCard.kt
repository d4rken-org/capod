package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.dialogs.ChargedSlotScopeDialog
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.charged.ChargedSlotScope

/**
 * The device's "Battery" settings, grouping everything charge/battery-related. Time remaining &
 * battery health lead, then a divider, then the charging-side settings:
 *  - The time-remaining/health estimate: a per-device toggle (disabling pauses/hides without
 *    discarding learned data) and a reset that wipes the learned drain, charge and health data so
 *    it starts over from the model's rated battery life.
 *  - Apple's "Optimized Charge Limit" (AAP setting 0x3B) — only for models that advertise
 *    [PodModel.Features.hasDynamicEndOfCharge] over an active AAP session.
 *  - "Notify when charged" (a per-device reaction) — fires purely off observed charging state, so it
 *    works for any live device (BLE or AAP), not just when the phone is the audio source.
 *
 * Each row is gated independently; the whole card hides when nothing applies.
 */
@Composable
internal fun BatteryCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    chargeCapControlEnabled: Boolean,
    estimateEnabled: Boolean,
    onDynamicEndOfChargeChange: (Boolean) -> Unit = {},
    onNotifyWhenChargedChange: (Boolean) -> Unit = {},
    onChargedThresholdChange: (Int) -> Unit = {},
    onChargedSlotScopeChange: (ChargedSlotScope) -> Unit = {},
    onEstimateEnabledChange: (Boolean) -> Unit = {},
    onResetEstimate: () -> Unit = {},
) {
    val chargeCap = device.dynamicEndOfCharge?.takeIf { features.hasDynamicEndOfCharge }
    // Per-profile controls (charge notification + estimate) apply to any live, profile-matched device.
    val showLiveControls = device.isLive && device.profileId != null
    if (chargeCap == null && !showLiveControls) return

    val reactions = device.reactions
    var showResetConfirm by remember { mutableStateOf(false) }
    var showChargedScopeDialog by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.device_settings_category_battery_label)) {
        // Time remaining & battery health lead — the headline feature. Charging-side settings
        // (Apple's charge limit, the charged notification) follow below the divider.
        if (showLiveControls) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.Schedule,
                title = stringResource(R.string.device_battery_estimate_toggle_label),
                subtitle = stringResource(R.string.device_battery_estimate_card_desc),
                checked = estimateEnabled,
                onCheckedChange = onEstimateEnabledChange,
            )
            SettingsBaseItem(
                icon = Icons.TwoTone.RestartAlt,
                title = stringResource(R.string.device_battery_estimate_reset_action),
                subtitle = stringResource(R.string.device_battery_estimate_reset_desc),
                onClick = { showResetConfirm = true },
            )
            // Same divider style the sibling device-settings cards use (see ReactionsCard).
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (chargeCap != null) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.BatteryChargingFull,
                title = stringResource(R.string.device_settings_charge_cap_label),
                subtitle = stringResource(R.string.device_settings_charge_cap_description),
                checked = chargeCap.enabled,
                onCheckedChange = onDynamicEndOfChargeChange,
                enabled = chargeCapControlEnabled,
            )
        }
        if (showLiveControls) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.NotificationsActive,
                title = stringResource(R.string.settings_charged_notification_label),
                subtitle = stringResource(R.string.settings_charged_notification_description),
                checked = reactions.notifyWhenCharged,
                onCheckedChange = onNotifyWhenChargedChange,
                requiresUpgrade = !isPro,
            )
            if (reactions.notifyWhenCharged) {
                var thresholdValue by remember(reactions.chargedThreshold) {
                    mutableIntStateOf(reactions.chargedThreshold)
                }
                SettingsSliderItem(
                    icon = Icons.TwoTone.BatteryChargingFull,
                    title = stringResource(R.string.settings_charged_threshold_label),
                    value = thresholdValue.toFloat(),
                    onValueChange = { thresholdValue = it.toInt() },
                    onValueChangeFinished = { onChargedThresholdChange(thresholdValue) },
                    valueRange = ReactionConfig.MIN_CHARGED_THRESHOLD.toFloat()..
                        ReactionConfig.MAX_CHARGED_THRESHOLD.toFloat(),
                    steps = (ReactionConfig.MAX_CHARGED_THRESHOLD - ReactionConfig.MIN_CHARGED_THRESHOLD) /
                        ReactionConfig.CHARGED_THRESHOLD_STEP - 1,
                    valueLabel = { "${it.toInt()}%" },
                )
                if (features.hasCase) {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.Workspaces,
                        title = stringResource(R.string.settings_charged_scope_label),
                        subtitle = stringResource(reactions.chargedSlotScope.labelRes),
                        onClick = { showChargedScopeDialog = true },
                    )
                }
            }
        }
    }

    if (showChargedScopeDialog) {
        ChargedSlotScopeDialog(
            current = reactions.chargedSlotScope,
            onSelect = {
                onChargedSlotScopeChange(it)
                showChargedScopeDialog = false
            },
            onDismiss = { showChargedScopeDialog = false },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(text = stringResource(R.string.device_battery_estimate_reset_confirm_title)) },
            text = { Text(text = stringResource(R.string.device_battery_estimate_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetEstimate()
                    },
                ) {
                    Text(text = stringResource(R.string.device_battery_estimate_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(text = stringResource(R.string.general_cancel_action))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun BatteryCardEnabledPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    BatteryCard(
        device = device,
        features = PodModel.Features(hasDynamicEndOfCharge = true, hasCase = true),
        isPro = true,
        chargeCapControlEnabled = true,
        estimateEnabled = true,
    )
}

@Preview2
@Composable
private fun BatteryCardDisabledPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    BatteryCard(
        device = device,
        features = PodModel.Features(hasDynamicEndOfCharge = true, hasCase = true),
        isPro = false,
        chargeCapControlEnabled = false,
        estimateEnabled = false,
    )
}
