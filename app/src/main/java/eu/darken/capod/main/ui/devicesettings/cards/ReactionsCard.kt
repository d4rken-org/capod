package eu.darken.capod.main.ui.devicesettings.cards

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Nightlight
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.dialogs.AutoConnectConditionDialog
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition

@Composable
internal fun ReactionsCard(
    device: PodDevice,
    features: PodModel.Features,
    isPro: Boolean,
    onAutoPlayChange: (Boolean) -> Unit = {},
    onAutoPauseChange: (Boolean) -> Unit = {},
    onOnePodModeChange: (Boolean) -> Unit = {},
    onConversationalAwarenessChange: (Boolean) -> Unit = {},
    onSleepDetectionChange: (Boolean) -> Unit = {},
    onAutoConnectChange: (Boolean) -> Unit = {},
    onAutoConnectConditionChange: (AutoConnectCondition) -> Unit = {},
    onShowPopUpOnCaseOpenChange: (Boolean) -> Unit = {},
    onShowPopUpOnConnectionChange: (Boolean) -> Unit = {},
    onOpenIssueTracker: () -> Unit = {},
) {
    val reactions = device.reactions
    val enabled = device.isAapReady

    var showAutoConnectConditionDialog by remember { mutableStateOf(false) }

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
            val hasAnyAapReaction =
                (features.hasConversationAwareness && convAwareness != null) ||
                    features.hasSleepDetection
            if (features.hasConversationAwareness && convAwareness != null) {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Hearing,
                    title = stringResource(R.string.conversation_awareness_label),
                    subtitle = stringResource(R.string.device_settings_conversation_awareness_description),
                    checked = convAwareness.enabled,
                    onCheckedChange = onConversationalAwarenessChange,
                    enabled = enabled,
                )
            }
            if (features.hasSleepDetection) {
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
