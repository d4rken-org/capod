package eu.darken.capod.main.ui.devicesettings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Message
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material.icons.twotone.Nightlight
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.TouchApp
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsPreferenceItem
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.devicesettings.cards.AapUnavailableCard
import eu.darken.capod.main.ui.devicesettings.cards.DeviceDetailItem
import eu.darken.capod.main.ui.devicesettings.cards.DeviceInfoCard
import eu.darken.capod.main.ui.devicesettings.cards.buildModelLabel
import eu.darken.capod.main.ui.devicesettings.cards.NotConnectedCard
import eu.darken.capod.main.ui.components.icon
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.main.ui.overview.cards.components.AncModeSelector
import eu.darken.capod.main.ui.devicesettings.dialogs.AutoConnectConditionDialog
import eu.darken.capod.main.ui.devicesettings.dialogs.SystemRenameUnavailableDialog
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.firstSeenFormatted
import eu.darken.capod.monitor.core.lastSeenFormatted
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.devices.HasStateDetection
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import java.time.Duration

@Composable
fun DeviceSettingsScreenHost(
    profileId: String,
    vm: DeviceSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(profileId) { vm.initialize(profileId) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameUnavailableDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                DeviceSettingsViewModel.Event.OpenBluetoothSettings -> {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                is DeviceSettingsViewModel.Event.SendFailed -> {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.device_settings_send_failed, event.message ?: ""),
                    )
                }
                DeviceSettingsViewModel.Event.SystemRenameUnavailable -> {
                    showRenameUnavailableDialog = true
                }
            }
        }
    }

    if (showRenameUnavailableDialog) {
        SystemRenameUnavailableDialog(
            onOpenBluetoothSettings = {
                showRenameUnavailableDialog = false
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            onDismiss = { showRenameUnavailableDialog = false },
        )
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    DeviceSettingsScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onAncModeChange = { vm.setAncMode(it) },
        onConversationalAwarenessChange = { vm.setConversationalAwareness(it) },
        onPersonalizedVolumeChange = { vm.setPersonalizedVolume(it) },
        onToneVolumeChange = { vm.setToneVolume(it) },
        onAdaptiveAudioNoiseChange = { vm.setAdaptiveAudioNoise(it) },
        onPressSpeedChange = { vm.setPressSpeed(it) },
        onPressHoldDurationChange = { vm.setPressHoldDuration(it) },
        onVolumeSwipeChange = { vm.setVolumeSwipe(it) },
        onVolumeSwipeLengthChange = { vm.setVolumeSwipeLength(it) },
        onEndCallMuteMicChange = { muteMic, endCall -> vm.setEndCallMuteMic(muteMic, endCall) },
        onMicrophoneModeChange = { vm.setMicrophoneMode(it) },
        onListeningModeCycleChange = { vm.setListeningModeCycle(it) },
        onOffVisibilityChange = { enabled, mask -> vm.setListeningModeOffVisibility(enabled, mask) },
        onSleepDetectionChange = { vm.setSleepDetection(it) },
        onDeviceNameChange = { vm.setDeviceName(it) },
        onStemActionsClick = { vm.navToStemConfig() },
        onForceConnect = { vm.forceConnect() },
        onUpgrade = { vm.launchUpgrade() },
        onOnePodModeChange = { vm.setOnePodMode(it) },
        onAutoPlayChange = { vm.setAutoPlay(it) },
        onAutoPauseChange = { vm.setAutoPause(it) },
        onAutoConnectChange = { vm.setAutoConnect(it) },
        onAutoConnectConditionChange = { vm.setAutoConnectCondition(it) },
        onShowPopUpOnCaseOpenChange = { vm.setShowPopUpOnCaseOpen(it) },
        onShowPopUpOnConnectionChange = { vm.setShowPopUpOnConnection(it) },
        onFixMonitorMode = { vm.setMonitorModeAutomatic() },
        onOpenIssueTracker = { vm.openIssueTracker() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    state: DeviceSettingsViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onAncModeChange: (AapSetting.AncMode.Value) -> Unit = {},
    onConversationalAwarenessChange: (Boolean) -> Unit = {},
    onPersonalizedVolumeChange: (Boolean) -> Unit = {},
    onToneVolumeChange: (Int) -> Unit = {},
    onAdaptiveAudioNoiseChange: (Int) -> Unit = {},
    onPressSpeedChange: (AapSetting.PressSpeed.Value) -> Unit = {},
    onPressHoldDurationChange: (AapSetting.PressHoldDuration.Value) -> Unit = {},
    onVolumeSwipeChange: (Boolean) -> Unit = {},
    onVolumeSwipeLengthChange: (AapSetting.VolumeSwipeLength.Value) -> Unit = {},
    onEndCallMuteMicChange: (AapSetting.EndCallMuteMic.MuteMicMode, AapSetting.EndCallMuteMic.EndCallMode) -> Unit = { _, _ -> },
    onMicrophoneModeChange: (AapSetting.MicrophoneMode.Mode) -> Unit = {},
    onListeningModeCycleChange: (Int) -> Unit = {},
    onOffVisibilityChange: (enabled: Boolean, currentCycleMask: Int) -> Unit = { _, _ -> },
    onSleepDetectionChange: (Boolean) -> Unit = {},
    onDeviceNameChange: (String) -> Unit = {},
    onStemActionsClick: () -> Unit = {},
    onForceConnect: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onOnePodModeChange: (Boolean) -> Unit = {},
    onAutoPlayChange: (Boolean) -> Unit = {},
    onAutoPauseChange: (Boolean) -> Unit = {},
    onAutoConnectChange: (Boolean) -> Unit = {},
    onAutoConnectConditionChange: (AutoConnectCondition) -> Unit = {},
    onShowPopUpOnCaseOpenChange: (Boolean) -> Unit = {},
    onShowPopUpOnConnectionChange: (Boolean) -> Unit = {},
    onFixMonitorMode: () -> Unit = {},
    onOpenIssueTracker: () -> Unit = {},
) {
    val context = LocalContext.current
    val device = state.device
    val features = device?.model?.features
    val enabled = device?.isAapReady == true
    val isPro = state.isPro
    val reactions = state.reactions

    var showAutoConnectConditionDialog by remember { mutableStateOf(false) }
    var showListeningModeCycleDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.device_settings_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val profileName = device?.label
                        if (profileName != null) {
                            Text(
                                text = stringResource(R.string.device_settings_subtitle_profile_prefix, profileName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
        ) {
            // Device Info
            if (device != null) {
                item("device_info") {
                    val context = LocalContext.current
                    val stateDetection = device.ble as? HasStateDetection
                    val seenFirst = device.seenFirstAt
                    val seenLast = device.seenLastAt
                    val firstSeen = if (seenFirst != null && seenLast != null && Duration.between(seenFirst, seenLast)
                            .toMinutes() >= 1
                    ) {
                        device.firstSeenFormatted(state.now)
                    } else null
                    val info = device.deviceInfo
                    val detailItems = buildList<DeviceDetailItem> {
                        if (info != null) {
                            if (info.manufacturer.isNotBlank()) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_manufacturer_label), info.manufacturer))
                            }
                            if (info.serialNumber.isNotBlank()) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_serial_label), info.serialNumber))
                            }
                            val hasFirmware = info.firmwareVersion.isNotBlank()
                            val hasBuild = !info.buildNumber.isNullOrBlank()
                            if (hasFirmware && hasBuild) {
                                add(DeviceDetailItem.Paired(
                                    start = DeviceDetailItem.Single(stringResource(R.string.device_settings_info_firmware_label), info.firmwareVersion),
                                    end = DeviceDetailItem.Single(stringResource(R.string.device_settings_info_build_label), info.buildNumber!!),
                                ))
                            } else if (hasFirmware) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_firmware_label), info.firmwareVersion))
                            } else if (hasBuild) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_build_label), info.buildNumber!!))
                            }
                            val hasLeft = !info.leftEarbudSerial.isNullOrBlank()
                            val hasRight = !info.rightEarbudSerial.isNullOrBlank()
                            if (hasLeft && hasRight) {
                                add(DeviceDetailItem.Paired(
                                    start = DeviceDetailItem.Single(stringResource(R.string.device_settings_info_left_serial_label), info.leftEarbudSerial!!),
                                    end = DeviceDetailItem.Single(stringResource(R.string.device_settings_info_right_serial_label), info.rightEarbudSerial!!),
                                ))
                            } else if (hasLeft) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_left_serial_label), info.leftEarbudSerial!!))
                            } else if (hasRight) {
                                add(DeviceDetailItem.Single(stringResource(R.string.device_settings_info_right_serial_label), info.rightEarbudSerial!!))
                            }
                        }
                    }
                    DeviceInfoCard(
                        deviceInfo = device.deviceInfo,
                        modelLabel = buildModelLabel(device),
                        systemBluetoothName = state.systemBluetoothName,
                        connectionStateLabel = stateDetection?.state?.getLabel(context),
                        lastSeen = device.lastSeenFormatted(state.now),
                        firstSeen = firstSeen,
                        detailItems = detailItems,
                        canRename = device.isAapReady,
                        onRename = onDeviceNameChange,
                    )
                }
            }

            // Not connected info — BLE live but no AAP connection
            if (device != null && device.ble != null && !device.isAapConnected && device.address != null) {
                if (state.isClassicallyConnected) {
                    // Device is connected for audio but AAP isn't available — show passive info
                    item("aap_unavailable_info") {
                        AapUnavailableCard()
                    }
                } else {
                    // Device is nearby but not connected — prompt user to connect
                    item("not_connected_info") {
                        NotConnectedCard(
                            isNudgeAvailable = state.isNudgeAvailable,
                            isForceConnecting = state.isForceConnecting,
                            onConnect = onForceConnect,
                        )
                    }
                }
            }

            // ── Reactions (per-profile, not gated on AAP) ─────────────────
            if (device != null && features != null) {
                item("reactions_section") {
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
                                val onePodModeActive = reactions.autoPlay ||
                                    reactions.autoPause ||
                                    (reactions.autoConnect && reactions.autoConnectCondition == AutoConnectCondition.IN_EAR) ||
                                    features.hasNcOneAirpod
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
                        if (anyPopupEnabled && state.monitorMode == MonitorMode.MANUAL) {
                            SettingsInfoBox(
                                text = stringResource(R.string.settings_popup_warning_manual_mode),
                                type = InfoBoxType.WARNING,
                                action = {
                                    TextButton(onClick = onFixMonitorMode) {
                                        Text(stringResource(R.string.general_fix_it_action))
                                    }
                                },
                            )
                        } else if (anyPopupEnabled) {
                            SettingsInfoBox(
                                text = stringResource(R.string.settings_popup_info_not_in_app),
                            )
                        }
                    }
                }
            }

            // Settings — only show when AAP is connected
            if (features != null && device.isAapConnected) {

                // ── Noise Control ────────────────────────────
                val ancMode = device.ancMode
                val adaptiveNoise = device.adaptiveAudioNoise
                if (features.hasAncControl && ancMode != null) {
                    val cycleMask = if (features.hasListeningModeCycle) {
                        (device.listeningModeCycle ?: AapSetting.ListeningModeCycle(modeMask = 0x0E)).modeMask
                    } else null
                    val cycleSummary = if (cycleMask != null) listeningModeCycleSummary(context, ancMode.supported, cycleMask) else null
                    val cycleSubtitle = if (cycleSummary != null) {
                        buildString {
                            append(cycleSummary)
                            append('\n')
                            append(
                                context.getString(
                                    if (state.hasCustomLongPressStemAction) {
                                        R.string.device_settings_listening_mode_cycle_summary_helper_override
                                    } else {
                                        R.string.device_settings_listening_mode_cycle_summary_helper
                                    }
                                ),
                            )
                        }
                    } else {
                        null
                    }

                    item("noise_control_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_noise_control_label)) {
                            NoiseControlCurrentModeControl(
                                currentMode = ancMode.current,
                                pendingMode = device.pendingAncMode,
                                supportedModes = ancMode.supported,
                                onModeSelected = onAncModeChange,
                                enabled = enabled,
                            )
                            val hasNoiseExtras = (features.hasAdaptiveAudioNoise && adaptiveNoise != null) ||
                                features.hasListeningModeCycle
                            if (hasNoiseExtras) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            if (features.hasListeningModeCycle && cycleMask != null && cycleSubtitle != null) {
                                SettingsPreferenceItem(
                                    icon = Icons.TwoTone.Loop,
                                    title = stringResource(R.string.device_settings_listening_mode_cycle_label),
                                    subtitle = cycleSubtitle,
                                    value = stringResource(
                                        if (isPro) {
                                            R.string.general_edit_action
                                        } else {
                                            R.string.general_upgrade_action
                                        },
                                    ),
                                    onClick = {
                                        if (isPro) {
                                            showListeningModeCycleDialog = true
                                        } else {
                                            onUpgrade()
                                        }
                                    },
                                    enabled = if (isPro) enabled else true,
                                    requiresUpgrade = !isPro,
                                )
                                if (state.hasCustomLongPressStemAction) {
                                    SettingsInfoBox(
                                        text = stringResource(R.string.stem_actions_long_press_anc_cycle_info),
                                        type = InfoBoxType.INFO,
                                        action = {
                                            TextButton(onClick = onStemActionsClick) {
                                                Text(stringResource(R.string.device_settings_noise_control_open_stem_actions_action))
                                            }
                                        },
                                    )
                                }
                            }
                            if (features.hasAdaptiveAudioNoise && adaptiveNoise != null) {
                                AdaptiveNoiseSlider(
                                    level = adaptiveNoise.level,
                                    onLevelChange = onAdaptiveAudioNoiseChange,
                                    enabled = enabled,
                                    isAdaptiveMode = ancMode.current == AapSetting.AncMode.Value.ADAPTIVE,
                                )
                            }
                        }
                    }
                }

                // ── Sound ────────────────────────────────────
                val personalizedVol = device.personalizedVolume
                val toneVol = device.toneVolume
                val showSoundSection =
                    (features.hasPersonalizedVolume && personalizedVol != null) ||
                            (features.hasToneVolume && toneVol != null) ||
                            features.hasMicrophoneMode
                if (showSoundSection) {
                    item("sound_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_category_sound_label)) {
                            if (features.hasPersonalizedVolume && personalizedVol != null) {
                                SettingsSwitchItem(
                                    icon = Icons.AutoMirrored.TwoTone.VolumeUp,
                                    title = stringResource(R.string.device_settings_personalized_volume_label),
                                    subtitle = stringResource(R.string.device_settings_personalized_volume_description),
                                    checked = personalizedVol.enabled,
                                    onCheckedChange = onPersonalizedVolumeChange,
                                    enabled = enabled,
                                )
                                if (personalizedVol.enabled) {
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
                            if (features.hasToneVolume && toneVol != null) {
                                if (isPro) {
                                    ToneVolumeSlider(
                                        level = toneVol.level,
                                        onLevelChange = onToneVolumeChange,
                                        enabled = enabled,
                                    )
                                } else {
                                    SettingsBaseItem(
                                        icon = Icons.AutoMirrored.TwoTone.VolumeUp,
                                        title = stringResource(R.string.device_settings_tone_volume_label),
                                        subtitle = stringResource(R.string.device_settings_tone_volume_description),
                                        onClick = onUpgrade,
                                        requiresUpgrade = true,
                                    )
                                }
                            }
                            if (features.hasMicrophoneMode) {
                                if (isPro) {
                                    val micMode = device.microphoneMode
                                        ?: AapSetting.MicrophoneMode(AapSetting.MicrophoneMode.Mode.AUTO)
                                    SegmentedSettingRow(
                                        icon = Icons.TwoTone.Mic,
                                        title = stringResource(R.string.device_settings_microphone_mode_label),
                                        subtitle = stringResource(R.string.device_settings_microphone_mode_description),
                                        options = listOf(
                                            stringResource(R.string.device_settings_microphone_mode_auto) to AapSetting.MicrophoneMode.Mode.AUTO,
                                            stringResource(R.string.device_settings_microphone_mode_left) to AapSetting.MicrophoneMode.Mode.ALWAYS_LEFT,
                                            stringResource(R.string.device_settings_microphone_mode_right) to AapSetting.MicrophoneMode.Mode.ALWAYS_RIGHT,
                                        ),
                                        selected = micMode.mode,
                                        onSelected = onMicrophoneModeChange,
                                        enabled = enabled,
                                    )
                                } else {
                                    SettingsBaseItem(
                                        icon = Icons.TwoTone.Mic,
                                        title = stringResource(R.string.device_settings_microphone_mode_label),
                                        subtitle = stringResource(R.string.device_settings_microphone_mode_description),
                                        onClick = onUpgrade,
                                        requiresUpgrade = true,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Controls ─────────────────────────────────
                val pressSpd = device.pressSpeed
                val pressHold = device.pressHoldDuration
                val volSwipe = device.volumeSwipe
                val volSwipeLen = device.volumeSwipeLength
                val endCallMuteMic = device.endCallMuteMic
                val showControlsSection = features.hasStemConfig ||
                        (features.hasEndCallMuteMic && endCallMuteMic != null) ||
                        (features.hasPressSpeed && pressSpd != null) ||
                        (features.hasPressHoldDuration && pressHold != null) ||
                        (features.hasVolumeSwipe && volSwipe != null) ||
                        (features.hasVolumeSwipeLength && volSwipeLen != null)
                if (showControlsSection) {
                    item("controls_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_category_controls_label)) {
                            if (features.hasStemConfig) {
                                SettingsPreferenceItem(
                                    icon = Icons.TwoTone.TouchApp,
                                    title = stringResource(R.string.stem_actions_title),
                                    subtitle = stringResource(R.string.stem_actions_nav_description),
                                    onClick = onStemActionsClick,
                                    enabled = enabled,
                                    requiresUpgrade = !isPro,
                                )
                            }
                            if (features.hasEndCallMuteMic && endCallMuteMic != null) {
                                EndCallMuteMicControl(
                                    current = endCallMuteMic,
                                    onChange = onEndCallMuteMicChange,
                                    enabled = enabled,
                                )
                            }
                            if (features.hasPressSpeed && pressSpd != null) {
                                SegmentedSettingRow(
                                    icon = Icons.TwoTone.Speed,
                                    title = stringResource(R.string.device_settings_press_speed_label),
                                    subtitle = stringResource(R.string.device_settings_press_speed_description),
                                    options = listOf(
                                        stringResource(R.string.device_settings_press_speed_default) to AapSetting.PressSpeed.Value.DEFAULT,
                                        stringResource(R.string.device_settings_press_speed_slower) to AapSetting.PressSpeed.Value.SLOWER,
                                        stringResource(R.string.device_settings_press_speed_slowest) to AapSetting.PressSpeed.Value.SLOWEST,
                                    ),
                                    selected = pressSpd.value,
                                    onSelected = onPressSpeedChange,
                                    enabled = enabled,
                                )
                            }
                            if (features.hasPressHoldDuration && pressHold != null) {
                                SegmentedSettingRow(
                                    icon = Icons.TwoTone.Timer,
                                    title = stringResource(R.string.device_settings_press_hold_label),
                                    subtitle = stringResource(R.string.device_settings_press_hold_description),
                                    options = listOf(
                                        stringResource(R.string.device_settings_press_hold_default) to AapSetting.PressHoldDuration.Value.DEFAULT,
                                        stringResource(R.string.device_settings_press_hold_shorter) to AapSetting.PressHoldDuration.Value.SHORTER,
                                        stringResource(R.string.device_settings_press_hold_shortest) to AapSetting.PressHoldDuration.Value.SHORTEST,
                                    ),
                                    selected = pressHold.value,
                                    onSelected = onPressHoldDurationChange,
                                    enabled = enabled,
                                )
                            }
                            if (features.hasVolumeSwipe && volSwipe != null) {
                                SettingsSwitchItem(
                                    icon = Icons.TwoTone.Swipe,
                                    title = stringResource(R.string.device_settings_volume_swipe_label),
                                    subtitle = stringResource(R.string.device_settings_volume_swipe_description),
                                    checked = volSwipe.enabled,
                                    onCheckedChange = onVolumeSwipeChange,
                                    enabled = enabled,
                                )
                            }
                            if (features.hasVolumeSwipeLength && volSwipeLen != null) {
                                SegmentedSettingRow(
                                    icon = Icons.TwoTone.Swipe,
                                    title = stringResource(R.string.device_settings_volume_swipe_length_label),
                                    subtitle = stringResource(R.string.device_settings_volume_swipe_length_description),
                                    options = listOf(
                                        stringResource(R.string.device_settings_volume_swipe_length_default) to AapSetting.VolumeSwipeLength.Value.DEFAULT,
                                        stringResource(R.string.device_settings_volume_swipe_length_longer) to AapSetting.VolumeSwipeLength.Value.LONGER,
                                        stringResource(R.string.device_settings_volume_swipe_length_longest) to AapSetting.VolumeSwipeLength.Value.LONGEST,
                                    ),
                                    selected = volSwipeLen.value,
                                    onSelected = onVolumeSwipeLengthChange,
                                    enabled = enabled,
                                )
                            }
                        }
                    }
                }

                // ── Connections ───────────────────────────────
                val connectedDevices = device.connectedDevices
                if (connectedDevices != null && connectedDevices.devices.isNotEmpty()) {
                    item("connections_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_category_connections_label)) {
                            ConnectedDevicesList(
                                devices = connectedDevices.devices,
                                audioSource = device.audioSource,
                            )
                        }
                    }
                }

                // EQ visualization (debug only)
                val eqBands = device.eqBands
                if (eu.darken.capod.BuildConfig.DEBUG && eqBands != null && eqBands.sets.isNotEmpty()) {
                    item("eq_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_eq_label)) {
                            EqBarsChart(
                                sets = eqBands.sets,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (
            showListeningModeCycleDialog &&
            device != null &&
            features?.hasListeningModeCycle == true &&
            device.isAapConnected
        ) {
            val ancMode = device.ancMode
            val currentCycleMask = (device.listeningModeCycle ?: AapSetting.ListeningModeCycle(modeMask = 0x0E)).modeMask
            if (ancMode != null) {
                ListeningModeCycleDialog(
                    supportedModes = ancMode.supported,
                    currentCycleMask = currentCycleMask,
                    onSave = { newMask ->
                        val supportsOff = ancMode.supported.contains(AapSetting.AncMode.Value.OFF)
                        if (supportsOff) {
                            val offBit = cycleBit(AapSetting.AncMode.Value.OFF)
                            onOffVisibilityChange((newMask and offBit) != 0, newMask and offBit.inv())
                        } else {
                            onListeningModeCycleChange(newMask)
                        }
                    },
                    onDismiss = { showListeningModeCycleDialog = false },
                )
            }
        }

        if (showAutoConnectConditionDialog && device != null) {
            AutoConnectConditionDialog(
                current = reactions.autoConnectCondition,
                hasEarDetection = features?.hasEarDetection == true,
                hasCase = features?.hasCase == true,
                onSelect = {
                    onAutoConnectConditionChange(it)
                    showAutoConnectConditionDialog = false
                },
                onDismiss = { showAutoConnectConditionDialog = false },
            )
        }
    }
}

@Composable
private fun ReactionsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun NoiseControlCurrentModeControl(
    currentMode: AapSetting.AncMode.Value,
    pendingMode: AapSetting.AncMode.Value?,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    enabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SettingsCompoundHeader(
                icon = Icons.TwoTone.Headphones,
                title = stringResource(R.string.device_settings_noise_control_current_mode_label),
                subtitle = null,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AncModeSelector(
                currentMode = currentMode,
                supportedModes = supportedModes,
                onModeSelected = onModeSelected,
                pendingMode = pendingMode,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ListeningModeCycleDialog(
    supportedModes: List<AapSetting.AncMode.Value>,
    currentCycleMask: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val supportedMask = supportedModes.fold(0) { mask, mode -> mask or cycleBit(mode) }
    var draftMask by remember(currentCycleMask, supportedMask) {
        mutableIntStateOf(currentCycleMask and supportedMask)
    }
    val selectedCount = Integer.bitCount(draftMask and supportedMask)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.device_settings_listening_mode_cycle_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.device_settings_listening_mode_cycle_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    supportedModes.forEach { mode ->
                        val bit = cycleBit(mode)
                        val isSelected = (draftMask and bit) != 0
                        val canToggle = !isSelected || selectedCount > 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    enabled = canToggle,
                                    role = Role.Checkbox,
                                    onClick = {
                                        draftMask = if (isSelected) {
                                            draftMask and bit.inv()
                                        } else {
                                            draftMask or bit
                                        }
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = canToggle,
                            )
                            Icon(
                                imageVector = mode.icon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (canToggle) 1f else 0.5f),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .align(Alignment.CenterVertically),
                            )
                            Text(
                                text = mode.listeningModeCycleDialogLabel(context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canToggle) 1f else 0.5f),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.device_settings_listening_mode_cycle_minimum),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draftMask)
                    onDismiss()
                },
                enabled = selectedCount >= 2,
            ) {
                Text(text = stringResource(R.string.general_save_action))
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
private fun ToneVolumeSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean,
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    SettingsSliderItem(
        icon = Icons.AutoMirrored.TwoTone.VolumeUp,
        title = stringResource(R.string.device_settings_tone_volume_label),
        subtitle = stringResource(R.string.device_settings_tone_volume_description),
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 15f..100f,
        steps = 84,
        enabled = enabled,
        valueLabel = { "${it.toInt()}%" },
    )
}

@Composable
private fun AdaptiveNoiseSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean,
    isAdaptiveMode: Boolean,
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    val subtitleRes = if (isAdaptiveMode) {
        R.string.device_settings_adaptive_noise_description
    } else {
        R.string.device_settings_adaptive_noise_requires_adaptive
    }
    SettingsSliderItem(
        icon = Icons.TwoTone.GraphicEq,
        title = stringResource(R.string.device_settings_adaptive_noise_label),
        subtitle = stringResource(subtitleRes),
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 0f..100f,
        steps = 99,
        enabled = enabled && isAdaptiveMode,
        valueLabel = { "${it.toInt()}%" },
    )
}

@Composable
private fun SettingsCompoundHeader(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
            modifier = Modifier.padding(end = 16.dp, top = 4.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedSettingRow(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SettingsCompoundHeader(
                icon = icon,
                title = title,
                subtitle = subtitle,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (label, value) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { if (enabled) onSelected(value) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EndCallMuteMicControl(
    current: AapSetting.EndCallMuteMic,
    onChange: (AapSetting.EndCallMuteMic.MuteMicMode, AapSetting.EndCallMuteMic.EndCallMode) -> Unit,
    enabled: Boolean,
) {
    val optionASelected = current.endCall == AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            SettingsCompoundHeader(
                icon = Icons.TwoTone.TouchApp,
                title = stringResource(R.string.device_settings_end_call_mute_mic_label),
                subtitle = stringResource(R.string.device_settings_end_call_mute_mic_description),
                enabled = enabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.selectableGroup()) {
                CallControlOption(
                    title = stringResource(R.string.device_settings_end_call_mute_mic_option_a_title),
                    subtitle = stringResource(R.string.device_settings_end_call_mute_mic_option_a_subtitle),
                    selected = optionASelected,
                    enabled = enabled,
                    onClick = {
                        onChange(
                            AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS,
                            AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS,
                        )
                    },
                )

                CallControlOption(
                    title = stringResource(R.string.device_settings_end_call_mute_mic_option_b_title),
                    subtitle = stringResource(R.string.device_settings_end_call_mute_mic_option_b_subtitle),
                    selected = !optionASelected,
                    enabled = enabled,
                    onClick = {
                        onChange(
                            AapSetting.EndCallMuteMic.MuteMicMode.SINGLE_PRESS,
                            AapSetting.EndCallMuteMic.EndCallMode.DOUBLE_PRESS,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CallControlOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { if (!selected) onClick() },
            )
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
    }
}

@Composable
private fun EqBarsChart(
    sets: List<List<Float>>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    // Use the first EQ set (main)
    val bands = sets.firstOrNull() ?: return
    if (bands.size != 8) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        val barCount = bands.size
        val spacing = 4.dp.toPx()
        val cornerRadius = 4.dp.toPx()
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount

        for (i in bands.indices) {
            val x = i * (barWidth + spacing)
            // Fixed 0-100 scale — values are typically 0-100 from the device
            val normalized = (bands[i] / 100f).coerceIn(0f, 1f)
            val barHeight = (normalized * size.height).coerceAtLeast(2.dp.toPx())

            // Background bar (rounded rect)
            drawRoundRect(
                color = outline,
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            )

            // Filled bar from bottom (rounded rect)
            drawRoundRect(
                color = primary,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            )
        }
    }
}

@Composable
private fun ConnectedDevicesList(
    devices: List<AapSetting.ConnectedDevices.ConnectedDevice>,
    audioSource: AapSetting.AudioSource?,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.device_settings_connected_devices_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        for ((index, device) in devices.withIndex()) {
            val isSource = audioSource?.sourceMac == device.mac
            val statusLabel = if (isSource) {
                when (audioSource?.type) {
                    AapSetting.AudioSource.AudioSourceType.CALL -> stringResource(R.string.device_settings_connected_device_call)
                    AapSetting.AudioSource.AudioSourceType.MEDIA -> stringResource(R.string.device_settings_connected_device_media)
                    else -> ""
                }
            } else ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.DevicesOther,
                    contentDescription = null,
                    tint = if (isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.device_settings_connected_device_label, index + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (statusLabel.isNotEmpty()) statusLabel else device.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun cycleBit(mode: AapSetting.AncMode.Value): Int = when (mode) {
    AapSetting.AncMode.Value.OFF -> 0x01
    AapSetting.AncMode.Value.ON -> 0x02
    AapSetting.AncMode.Value.TRANSPARENCY -> 0x04
    AapSetting.AncMode.Value.ADAPTIVE -> 0x08
}

private fun listeningModeCycleSummary(
    context: android.content.Context,
    supportedModes: List<AapSetting.AncMode.Value>,
    cycleMask: Int,
): String = supportedModes
    .filter { mode -> (cycleMask and cycleBit(mode)) != 0 }
    .joinToString(separator = " • ") { it.shortLabel(context) }

private fun AapSetting.AncMode.Value.listeningModeCycleDialogLabel(
    context: android.content.Context,
): String = when (this) {
    AapSetting.AncMode.Value.OFF -> context.getString(R.string.device_settings_allow_off_label)
    AapSetting.AncMode.Value.ON -> context.getString(R.string.device_settings_listening_mode_cycle_anc)
    AapSetting.AncMode.Value.TRANSPARENCY -> context.getString(R.string.device_settings_listening_mode_cycle_transparency)
    AapSetting.AncMode.Value.ADAPTIVE -> context.getString(R.string.device_settings_listening_mode_cycle_adaptive)
}

private fun previewFullState(isPro: Boolean) = DeviceSettingsViewModel.State(
    device = PodDevice(
        profileId = "preview",
        label = "My AirPods Pro",
        ble = MockPodDataProvider.airPodsProWithKeys(),
        aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            deviceInfo = AapDeviceInfo(
                name = "AirPods Pro",
                modelNumber = "A2699",
                manufacturer = "Apple Inc.",
                serialNumber = "W5J7KV0N04",
                firmwareVersion = "7A305",
            ),
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    current = AapSetting.AncMode.Value.ADAPTIVE,
                    supported = listOf(
                        AapSetting.AncMode.Value.OFF,
                        AapSetting.AncMode.Value.ON,
                        AapSetting.AncMode.Value.TRANSPARENCY,
                        AapSetting.AncMode.Value.ADAPTIVE,
                    ),
                ),
                AapSetting.ConversationalAwareness::class to AapSetting.ConversationalAwareness(enabled = true),
                AapSetting.NcWithOneAirPod::class to AapSetting.NcWithOneAirPod(enabled = true),
                AapSetting.PersonalizedVolume::class to AapSetting.PersonalizedVolume(enabled = false),
                AapSetting.ToneVolume::class to AapSetting.ToneVolume(level = 75),
                AapSetting.AdaptiveAudioNoise::class to AapSetting.AdaptiveAudioNoise(level = 50),
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(value = AapSetting.PressSpeed.Value.DEFAULT),
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(value = AapSetting.PressHoldDuration.Value.DEFAULT),
                AapSetting.VolumeSwipe::class to AapSetting.VolumeSwipe(enabled = true),
                AapSetting.VolumeSwipeLength::class to AapSetting.VolumeSwipeLength(value = AapSetting.VolumeSwipeLength.Value.DEFAULT),
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(
                    muteMic = AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS,
                    endCall = AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS,
                ),
            ),
        ),
    ),
    now = MOCK_NOW,
    isPro = isPro,
)

@Preview2
@Composable
private fun DeviceSettingsFullProPreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = previewFullState(isPro = true),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun DeviceSettingsFullNonProPreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = previewFullState(isPro = false),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun DeviceSettingsInfoOnlyPreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = DeviceSettingsViewModel.State(
            device = PodDevice(
                profileId = "preview-info",
                label = "My AirPods Pro",
                ble = MockPodDataProvider.airPodsProMixed(),
                aap = null,
            ),
            now = MOCK_NOW,
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun DeviceSettingsLowFeaturePreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = DeviceSettingsViewModel.State(
            device = PodDevice(
                profileId = "preview-low-feature",
                label = "Beats Solo 3",
                ble = MockPodDataProvider.beatsSolo3(),
                aap = null,
            ),
            now = MOCK_NOW,
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun DeviceSettingsCachedOnlyPreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = DeviceSettingsViewModel.State(
            device = MockPodDataProvider.dualPodCachedOnly(),
            now = MOCK_NOW,
        ),
        onNavigateUp = {},
    )
}
