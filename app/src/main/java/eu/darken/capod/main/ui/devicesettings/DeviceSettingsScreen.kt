package eu.darken.capod.main.ui.devicesettings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.LooksOne
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.Mic
import androidx.compose.material.icons.twotone.Nightlight
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.TouchApp
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsPreferenceItem
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
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
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.device_settings_rename_system_unavailable),
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    DeviceSettingsScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onAncModeChange = { vm.setAncMode(it) },
        onConversationalAwarenessChange = { vm.setConversationalAwareness(it) },
        onNcWithOneAirPodChange = { vm.setNcWithOneAirPod(it) },
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
        onAllowOffOptionChange = { vm.setAllowOffOption(it) },
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
    onNcWithOneAirPodChange: (Boolean) -> Unit = {},
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
    onAllowOffOptionChange: (Boolean) -> Unit = {},
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
) {
    val device = state.device
    val features = device?.model?.features
    val enabled = device?.isAapReady == true
    val isPro = state.isPro
    val reactions = state.reactions

    var showAutoConnectConditionDialog by remember { mutableStateOf(false) }

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
                                text = profileName,
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
                    DeviceInfoCard(
                        deviceInfo = device.deviceInfo,
                        connectionStateLabel = stateDetection?.state?.getLabel(context),
                        lastSeen = device.lastSeenFormatted(state.now),
                        firstSeen = firstSeen,
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
                    }
                }
            }

            // Settings — only show when AAP is connected
            if (features != null && device.isAapConnected) {

                // ── Noise Control ────────────────────────────
                val ancMode = device.ancMode
                if (features.hasAncControl && ancMode != null) {
                    item("noise_control_header") {
                        SettingsCategoryHeader(text = stringResource(R.string.device_settings_noise_control_label))
                    }

                    val cycleMask = if (features.hasListeningModeCycle) {
                        (device.listeningModeCycle ?: AapSetting.ListeningModeCycle(modeMask = 0x0E)).modeMask
                    } else null

                    item("noise_control") {
                        NoiseControlCombined(
                            currentMode = ancMode.current,
                            pendingMode = device.pendingAncMode,
                            supportedModes = ancMode.supported,
                            onModeSelected = onAncModeChange,
                            cycleMask = if (isPro) cycleMask else null,
                            onCycleMaskChange = onListeningModeCycleChange,
                            onAllowOffChange = onAllowOffOptionChange,
                            enabled = enabled,
                        )
                    }

                    if (!isPro && features.hasListeningModeCycle) {
                        item("noise_control_cycle_pro") {
                            SettingsBaseItem(
                                icon = Icons.TwoTone.Loop,
                                title = stringResource(R.string.device_settings_listening_mode_cycle_label),
                                subtitle = stringResource(R.string.device_settings_listening_mode_cycle_description),
                                onClick = onUpgrade,
                                requiresUpgrade = true,
                            )
                        }
                    }

                    val convAwareness = device.conversationalAwareness
                    if (features.hasConversationAwareness && convAwareness != null) {
                        item("conversation_awareness") {
                            SettingsSwitchItem(
                                icon = Icons.TwoTone.Hearing,
                                title = stringResource(R.string.conversation_awareness_label),
                                subtitle = stringResource(R.string.device_settings_conversation_awareness_description),
                                checked = convAwareness.enabled,
                                onCheckedChange = onConversationalAwarenessChange,
                                enabled = enabled,
                            )
                        }
                    }

                    val ncOneAirpod = device.ncWithOneAirPod
                    if (features.hasNcOneAirpod && ncOneAirpod != null) {
                        item("nc_one_airpod") {
                            SettingsSwitchItem(
                                icon = Icons.TwoTone.Headphones,
                                title = stringResource(R.string.device_settings_nc_one_airpod_label),
                                subtitle = stringResource(R.string.device_settings_nc_one_airpod_description),
                                checked = ncOneAirpod.enabled,
                                onCheckedChange = onNcWithOneAirPodChange,
                                enabled = enabled,
                            )
                        }
                    }

                    val adaptiveNoise = device.adaptiveAudioNoise
                    if (features.hasAdaptiveAudioNoise && adaptiveNoise != null) {
                        item("adaptive_noise") {
                            AdaptiveNoiseSlider(
                                level = adaptiveNoise.level,
                                onLevelChange = onAdaptiveAudioNoiseChange,
                                enabled = enabled,
                            )
                        }
                    }
                }

                // ── Sound ────────────────────────────────────
                val personalizedVol = device.personalizedVolume
                val toneVol = device.toneVolume
                val showSoundSection =
                    (features.hasPersonalizedVolume && personalizedVol != null) || (features.hasToneVolume && toneVol != null)
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
                            }
                            if (features.hasToneVolume && toneVol != null) {
                                ToneVolumeSlider(
                                    level = toneVol.level,
                                    onLevelChange = onToneVolumeChange,
                                    enabled = enabled,
                                )
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

                // ── Other ───────────────────────────────────
                val showOtherSection = features.hasMicrophoneMode || features.hasSleepDetection
                if (showOtherSection) {
                    item("other_section") {
                        SettingsSection(title = stringResource(R.string.settings_category_other_label)) {
                            if (features.hasMicrophoneMode) {
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
private fun AutoConnectConditionDialog(
    current: AutoConnectCondition,
    hasEarDetection: Boolean,
    hasCase: Boolean,
    onSelect: (AutoConnectCondition) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = AutoConnectCondition.entries.filter { condition ->
        when (condition) {
            AutoConnectCondition.IN_EAR -> hasEarDetection
            AutoConnectCondition.CASE_OPEN -> hasCase
            AutoConnectCondition.WHEN_SEEN -> true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_autoconnect_condition_label)) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { condition ->
                    val isSelected = condition == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(condition) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = stringResource(condition.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeviceInfoCard(
    deviceInfo: AapDeviceInfo?,
    connectionStateLabel: String?,
    lastSeen: String?,
    firstSeen: String?,
    canRename: Boolean = false,
    onRename: (String) -> Unit = {},
) {
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog && deviceInfo != null) {
        RenameDialog(
            currentName = deviceInfo.name,
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (deviceInfo != null) {
                if (deviceInfo.name.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        InfoRow(
                            label = stringResource(R.string.device_settings_info_name_label),
                            value = deviceInfo.name,
                            modifier = Modifier.weight(1f),
                        )
                        if (canRename) {
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(
                                    imageVector = Icons.TwoTone.Edit,
                                    contentDescription = stringResource(R.string.device_settings_rename_label),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (deviceInfo.serialNumber.isNotBlank()) {
                    InfoRow(
                        label = stringResource(R.string.device_settings_info_serial_label),
                        value = deviceInfo.serialNumber,
                    )
                }
                if (deviceInfo.firmwareVersion.isNotBlank()) {
                    InfoRow(
                        label = stringResource(R.string.device_settings_info_firmware_label),
                        value = deviceInfo.firmwareVersion,
                    )
                }
            }
            if (connectionStateLabel != null) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_status_label),
                    value = connectionStateLabel,
                )
            }
            if (lastSeen != null) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_last_seen_label),
                    value = lastSeen,
                )
            }
            if (firstSeen != null) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_first_seen_label),
                    value = firstSeen,
                )
            }
        }
    }
}

@Composable
private fun NotConnectedCard(
    isNudgeAvailable: Boolean,
    isForceConnecting: Boolean,
    onConnect: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.device_settings_not_connected_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.device_settings_not_connected_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConnect,
                enabled = !isForceConnecting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(
                        if (isNudgeAvailable) R.string.device_settings_not_connected_connect_action
                        else R.string.device_settings_not_connected_open_settings_action
                    ),
                )
            }
        }
    }
}

@Composable
private fun AapUnavailableCard() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.device_settings_aap_unavailable_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.device_settings_aap_unavailable_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    SettingsSliderItem(
        icon = Icons.TwoTone.GraphicEq,
        title = stringResource(R.string.device_settings_adaptive_noise_label),
        subtitle = stringResource(R.string.device_settings_adaptive_noise_description),
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 0f..100f,
        steps = 99,
        enabled = enabled,
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

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember { mutableStateOf(currentName) }

    // The decoder in DefaultAapDeviceProfile only round-trips printable ASCII (0x20..0x7E),
    // so even if the device accepts a UTF-8 name we can't display it back correctly. Accept any
    // input up to the 32-byte UX cap, but flag non-ASCII with an inline error so the user
    // understands why Rename is disabled.
    val hasInvalidAscii = textValue.any { it.code !in 0x20..0x7E }
    val isValid = textValue.isNotBlank() && !hasInvalidAscii

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_settings_rename_label)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    // US_ASCII encoding maps non-ASCII chars to '?' (1 byte each), giving a
                    // stable upper bound equal to the UTF-16 char count. Keeps the cap the user
                    // sees consistent regardless of character content.
                    if (newValue.toByteArray(Charsets.US_ASCII).size <= 32) textValue = newValue
                },
                singleLine = true,
                label = { Text(stringResource(R.string.device_settings_rename_hint)) },
                isError = hasInvalidAscii,
                supportingText = if (hasInvalidAscii) {
                    { Text(stringResource(R.string.device_settings_rename_invalid_ascii)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (isValid) onConfirm(textValue) },
                enabled = isValid && textValue != currentName,
            ) {
                Text(stringResource(R.string.device_settings_rename_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun NoiseControlCombined(
    currentMode: AapSetting.AncMode.Value,
    pendingMode: AapSetting.AncMode.Value?,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    cycleMask: Int?,
    onCycleMaskChange: (Int) -> Unit,
    onAllowOffChange: (Boolean) -> Unit = {},
    enabled: Boolean,
) {
    val displayMode = pendingMode ?: currentMode
    val cycleBits = mapOf(
        AapSetting.AncMode.Value.OFF to 0x01,
        AapSetting.AncMode.Value.ON to 0x02,
        AapSetting.AncMode.Value.TRANSPARENCY to 0x04,
        AapSetting.AncMode.Value.ADAPTIVE to 0x08,
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            for (mode in supportedModes) {
                val isSelected = mode == displayMode
                val bit = cycleBits[mode] ?: continue
                val inCycle = cycleMask?.let { (it and bit) != 0 }
                val cycleCount = cycleMask?.let { Integer.bitCount(it and 0x0F) } ?: 0
                val canRemoveFromCycle = inCycle != true || cycleCount > 2

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Visibility toggle — independent click target
                    if (cycleMask != null) {
                        IconButton(
                            onClick = {
                                val isOff = mode == AapSetting.AncMode.Value.OFF
                                if (inCycle == true && canRemoveFromCycle) {
                                    onCycleMaskChange(cycleMask xor bit)
                                    if (isOff) onAllowOffChange(false)
                                } else if (inCycle != true) {
                                    onCycleMaskChange((cycleMask ?: 0) or bit)
                                    if (isOff) onAllowOffChange(true)
                                }
                            },
                            enabled = enabled && (inCycle != true || canRemoveFromCycle),
                        ) {
                            Icon(
                                imageVector = if (inCycle == true) Icons.TwoTone.Visibility else Icons.TwoTone.VisibilityOff,
                                contentDescription = null,
                                tint = if (inCycle == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                            )
                        }
                    }

                    // Mode selection — label + radio as one click target
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = enabled) { onModeSelected(mode) }
                            .padding(start = if (cycleMask == null) 16.dp else 0.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
                            },
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            enabled = enabled,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AapSetting.AncMode.Value.label(): String = when (this) {
    AapSetting.AncMode.Value.OFF -> stringResource(R.string.device_settings_listening_mode_cycle_off)
    AapSetting.AncMode.Value.ON -> stringResource(R.string.device_settings_listening_mode_cycle_anc)
    AapSetting.AncMode.Value.TRANSPARENCY -> stringResource(R.string.device_settings_listening_mode_cycle_transparency)
    AapSetting.AncMode.Value.ADAPTIVE -> stringResource(R.string.device_settings_listening_mode_cycle_adaptive)
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

@Preview2
@Composable
private fun NotConnectedCardNudgeAvailablePreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = true,
        isForceConnecting = false,
        onConnect = {},
    )
}

@Preview2
@Composable
private fun NotConnectedCardNudgeUnavailablePreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = false,
        isForceConnecting = false,
        onConnect = {},
    )
}

@Preview2
@Composable
private fun NotConnectedCardForceConnectingPreview() = PreviewWrapper {
    NotConnectedCard(
        isNudgeAvailable = true,
        isForceConnecting = true,
        onConnect = {},
    )
}
