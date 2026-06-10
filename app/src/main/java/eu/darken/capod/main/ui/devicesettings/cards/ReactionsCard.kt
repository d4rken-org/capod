package eu.darken.capod.main.ui.devicesettings.cards

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.automirrored.twotone.VolumeDown
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Nightlight
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.RecordVoiceOver
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.dialogs.AutoConnectConditionDialog
import eu.darken.capod.main.ui.devicesettings.dialogs.ChargedSlotScopeDialog
import eu.darken.capod.main.ui.devicesettings.dialogs.ConversationActionDialog
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.ReactionConfig
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.reaction.core.charged.ChargedSlotScope
import eu.darken.capod.reaction.core.conversation.ConversationAction

@Composable
internal fun ReactionsCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    onAutoPlayChange: (Boolean) -> Unit = {},
    onAutoPauseChange: (Boolean) -> Unit = {},
    onStartMusicOnWearChange: (Boolean) -> Unit = {},
    onOnePodModeChange: (Boolean) -> Unit = {},
    onConversationalAwarenessChange: (Boolean) -> Unit = {},
    onConversationActionChange: (ConversationAction) -> Unit = {},
    onConversationVolumeReductionChange: (Int) -> Unit = {},
    onSleepDetectionChange: (Boolean) -> Unit = {},
    onAutoConnectChange: (Boolean) -> Unit = {},
    onAutoConnectConditionChange: (AutoConnectCondition) -> Unit = {},
    onShowPopUpOnCaseOpenChange: (Boolean) -> Unit = {},
    onShowPopUpOnConnectionChange: (Boolean) -> Unit = {},
    onNotifyWhenChargedChange: (Boolean) -> Unit = {},
    onChargedThresholdChange: (Int) -> Unit = {},
    onChargedSlotScopeChange: (ChargedSlotScope) -> Unit = {},
    onOpenIssueTracker: () -> Unit = {},
) {
    val reactions = device.reactions
    val enabled = device.isAapReady

    var showAutoConnectConditionDialog by remember { mutableStateOf(false) }
    var showConversationActionDialog by remember { mutableStateOf(false) }
    var showChargedScopeDialog by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.settings_reaction_label)) {
        if (features.hasEarDetection) {
            SettingsSwitchItem(
                icon = Icons.TwoTone.PlayCircle,
                title = stringResource(R.string.settings_autopplay_label),
                subtitle = stringResource(R.string.settings_autoplay_description),
                checked = reactions.autoPlay,
                onCheckedChange = onAutoPlayChange,
                requiresUpgrade = !isPro,
            )
            if (reactions.autoPlay) {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.PlayCircle,
                    title = stringResource(R.string.settings_start_music_on_wear_label),
                    subtitle = stringResource(R.string.settings_start_music_on_wear_description),
                    checked = reactions.startMusicOnWear,
                    onCheckedChange = onStartMusicOnWearChange,
                    requiresUpgrade = !isPro,
                )
            }
            SettingsSwitchItem(
                icon = Icons.TwoTone.PauseCircle,
                title = stringResource(R.string.settings_autopause_label),
                subtitle = stringResource(R.string.settings_autopause_description),
                checked = reactions.autoPause,
                onCheckedChange = onAutoPauseChange,
                requiresUpgrade = !isPro,
            )
            if (features.hasDualPods) {
                val onePodModeActive = reactions.onePodMode ||
                    reactions.autoPlay ||
                    reactions.autoPause ||
                    (reactions.autoConnect && reactions.autoConnectCondition == AutoConnectCondition.IN_EAR)
                SettingsBaseItem(
                    title = stringResource(R.string.settings_onepod_mode_label),
                    subtitle = stringResource(R.string.settings_onepod_mode_description),
                    icon = Icons.TwoTone.LooksOne,
                    onClick = { if (onePodModeActive) onOnePodModeChange(!reactions.onePodMode) },
                    enabled = onePodModeActive,
                    trailingContent = {
                        Switch(
                            checked = reactions.onePodMode,
                            onCheckedChange = onOnePodModeChange,
                            enabled = onePodModeActive,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    },
                )
            }
            val earDetectionWarningVisible =
                (
                    reactions.autoPlay ||
                        reactions.autoPause ||
                        (reactions.autoConnect && reactions.autoConnectCondition == AutoConnectCondition.IN_EAR)
                    ) && !device.isAapConnected
            if (earDetectionWarningVisible) {
                SettingsInfoBox(
                    text = stringResource(R.string.settings_eardetection_info_description),
                )
            }
            ReactionsDivider()
        }
        if (device.isAapConnected) {
            val convAwareness = device.conversationalAwareness
            val showConversationAwareness = features.hasConversationAwareness && convAwareness != null
            val hasAnyAapReaction = showConversationAwareness || features.hasSleepDetection
            if (showConversationAwareness) {
                SettingsCategoryHeader(text = stringResource(R.string.conversation_awareness_label))
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Hearing,
                    title = stringResource(R.string.conversation_awareness_label),
                    subtitle = stringResource(R.string.device_settings_conversation_awareness_description),
                    checked = convAwareness?.enabled == true,
                    onCheckedChange = onConversationalAwarenessChange,
                    enabled = enabled,
                )
                // The "when you start speaking" reaction only fires while CA is on (the pod emits no
                // speaking frames otherwise), so only surface it once CA is actually enabled.
                if (convAwareness?.enabled == true) {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.RecordVoiceOver,
                        title = stringResource(R.string.settings_conversation_action_label),
                        subtitle = stringResource(reactions.conversationAction.labelRes),
                        onClick = { showConversationActionDialog = true },
                        enabled = enabled,
                        requiresUpgrade = !isPro,
                    )
                    if (reactions.conversationAction == ConversationAction.LOWER_VOLUME) {
                        var sliderValue by remember(reactions.conversationVolumeReduction) {
                            mutableIntStateOf(reactions.conversationVolumeReduction)
                        }
                        SettingsSliderItem(
                            icon = Icons.AutoMirrored.TwoTone.VolumeDown,
                            title = stringResource(R.string.settings_conversation_volume_reduction_label),
                            value = sliderValue.toFloat(),
                            onValueChange = { sliderValue = it.toInt() },
                            onValueChangeFinished = { onConversationVolumeReductionChange(sliderValue) },
                            valueRange = ReactionConfig.MIN_CONVERSATION_VOLUME_REDUCTION.toFloat()..
                                ReactionConfig.MAX_CONVERSATION_VOLUME_REDUCTION.toFloat(),
                            enabled = enabled,
                            valueLabel = { "${it.toInt()}%" },
                        )
                    }
                }
            }
            if (features.hasSleepDetection) {
                if (showConversationAwareness) ReactionsDivider()
                val sleepDet = device.sleepDetection
                    ?: AapSetting.SleepDetection(enabled = true)
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Nightlight,
                    title = stringResource(R.string.device_settings_sleep_detection_label),
                    subtitle = stringResource(R.string.device_settings_sleep_detection_description),
                    checked = sleepDet.enabled,
                    onCheckedChange = onSleepDetectionChange,
                    enabled = enabled,
                    requiresUpgrade = !isPro,
                )
                if (sleepDet.enabled) {
                    SettingsInfoBox(
                        title = stringResource(R.string.device_settings_experimental_title),
                        text = stringResource(R.string.device_settings_experimental_description),
                        type = InfoBoxType.WARNING,
                        action = {
                            TextButton(onClick = onOpenIssueTracker) {
                                Text(stringResource(R.string.device_settings_experimental_action))
                            }
                        },
                    )
                }
            }
            if (hasAnyAapReaction) {
                ReactionsDivider()
            }
        }
        SettingsSwitchItem(
            icon = Icons.TwoTone.BluetoothConnected,
            title = stringResource(R.string.settings_autoconnect_label),
            subtitle = stringResource(R.string.settings_autoconnect_description),
            checked = reactions.autoConnect,
            onCheckedChange = onAutoConnectChange,
        )
        SettingsBaseItem(
            title = stringResource(R.string.settings_autoconnect_condition_label),
            subtitle = stringResource(reactions.autoConnectCondition.labelRes),
            icon = Icons.TwoTone.Workspaces,
            onClick = { if (reactions.autoConnect) showAutoConnectConditionDialog = true },
            enabled = reactions.autoConnect,
        )
        if (reactions.autoConnect && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsInfoBox(
                text = stringResource(R.string.settings_autoconnect_info_android12),
            )
        }
        ReactionsDivider()
        if (features.hasCase) {
            SettingsSwitchItem(
                icon = Icons.AutoMirrored.TwoTone.Message,
                title = stringResource(R.string.settings_popup_caseopen_label),
                subtitle = stringResource(R.string.settings_popup_caseopen_description),
                checked = reactions.showPopUpOnCaseOpen,
                onCheckedChange = onShowPopUpOnCaseOpenChange,
                requiresUpgrade = !isPro,
            )
        }
        SettingsSwitchItem(
            icon = Icons.AutoMirrored.TwoTone.Message,
            title = stringResource(R.string.settings_popup_connected_label),
            subtitle = stringResource(R.string.settings_popup_connected_description),
            checked = reactions.showPopUpOnConnection,
            onCheckedChange = onShowPopUpOnConnectionChange,
            requiresUpgrade = !isPro,
        )
        val anyPopupEnabled = reactions.showPopUpOnCaseOpen || reactions.showPopUpOnConnection
        if (anyPopupEnabled) {
            SettingsInfoBox(
                text = stringResource(R.string.settings_popup_info_not_in_app),
            )
        }
        ReactionsDivider()
        SettingsSwitchItem(
            icon = Icons.TwoTone.BatteryChargingFull,
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

    if (showConversationActionDialog) {
        ConversationActionDialog(
            current = reactions.conversationAction,
            onSelect = {
                onConversationActionChange(it)
                showConversationActionDialog = false
            },
            onDismiss = { showConversationActionDialog = false },
        )
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

    if (showAutoConnectConditionDialog) {
        AutoConnectConditionDialog(
            current = reactions.autoConnectCondition,
            hasEarDetection = features.hasEarDetection,
            hasCase = features.hasCase,
            onSelect = {
                onAutoConnectConditionChange(it)
                showAutoConnectConditionDialog = false
            },
            onDismiss = { showAutoConnectConditionDialog = false },
        )
    }
}

@Composable
private fun ReactionsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Preview2
@Composable
private fun ReactionsCardPreview() = PreviewWrapper {
    val state = previewFullState(isPro = true)
    val device = state.device!!
    ReactionsCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
    )
}

@Preview2
@Composable
private fun ReactionsCardNonProPreview() = PreviewWrapper {
    val state = previewFullState(isPro = false)
    val device = state.device!!
    ReactionsCard(
        device = device,
        features = device.model.features,
        isPro = state.isPro,
    )
}
