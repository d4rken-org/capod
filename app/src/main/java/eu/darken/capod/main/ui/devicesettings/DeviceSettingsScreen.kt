package eu.darken.capod.main.ui.devicesettings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.main.ui.devicesettings.cards.AapUnavailableCard
import eu.darken.capod.main.ui.devicesettings.cards.ControlsCard
import eu.darken.capod.main.ui.devicesettings.cards.DeviceDetailItem
import eu.darken.capod.main.ui.devicesettings.cards.DeviceInfoCard
import eu.darken.capod.main.ui.devicesettings.cards.NoiseControlCard
import eu.darken.capod.main.ui.devicesettings.cards.NotConnectedCard
import eu.darken.capod.main.ui.devicesettings.cards.ReactionsCard
import eu.darken.capod.main.ui.devicesettings.cards.SoundCard
import eu.darken.capod.main.ui.devicesettings.cards.buildModelLabel
import eu.darken.capod.main.ui.devicesettings.components.ConnectedDevicesList
import eu.darken.capod.main.ui.devicesettings.components.EqBarsChart
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
    var showListeningModeCycleDialog by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val offRejectedMessage = stringResource(R.string.device_settings_anc_off_rejected_message)

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

                DeviceSettingsViewModel.Event.OffModeRejectedByDevice -> {
                    snackbarHostState.showSnackbar(offRejectedMessage)
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

    val currentState = state ?: return

    DeviceSettingsScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        showListeningModeCycleDialog = showListeningModeCycleDialog,
        onShowListeningModeCycleDialogChange = { showListeningModeCycleDialog = it },
        onNavigateUp = { vm.navUp() },
        onAncModeChange = { vm.setAncMode(it) },
        onConversationalAwarenessChange = { vm.setConversationalAwareness(it) },
        onNcWithOneAirPodChange = { vm.setNcWithOneAirPod(it) },
        onPersonalizedVolumeChange = { vm.setPersonalizedVolume(it) },
        onToneVolumeChange = { vm.setToneVolume(it) },
        onAdaptiveAudioNoiseChange = { vm.setAdaptiveAudioNoise(it) },
        onVolumeSwipeChange = { vm.setVolumeSwipe(it) },
        onVolumeSwipeLengthChange = { vm.setVolumeSwipeLength(it) },
        onMicrophoneModeChange = { vm.setMicrophoneMode(it) },
        onListeningModeCycleChange = { vm.setListeningModeCycle(it) },
        onAllowOffOptionChange = { vm.setAllowOffOption(it) },
        onSleepDetectionChange = { vm.setSleepDetection(it) },
        onDeviceNameChange = { vm.setDeviceName(it) },
        onPressControlsClick = { vm.navToPressControls() },
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

@Composable
fun DeviceSettingsScreen(
    state: DeviceSettingsViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showListeningModeCycleDialog: Boolean = false,
    onShowListeningModeCycleDialogChange: (Boolean) -> Unit = {},
    onNavigateUp: () -> Unit,
    onAncModeChange: (AapSetting.AncMode.Value) -> Unit = {},
    onConversationalAwarenessChange: (Boolean) -> Unit = {},
    onNcWithOneAirPodChange: (Boolean) -> Unit = {},
    onPersonalizedVolumeChange: (Boolean) -> Unit = {},
    onToneVolumeChange: (Int) -> Unit = {},
    onAdaptiveAudioNoiseChange: (Int) -> Unit = {},
    onVolumeSwipeChange: (Boolean) -> Unit = {},
    onVolumeSwipeLengthChange: (AapSetting.VolumeSwipeLength.Value) -> Unit = {},
    onMicrophoneModeChange: (AapSetting.MicrophoneMode.Mode) -> Unit = {},
    onListeningModeCycleChange: (Int) -> Unit = {},
    onAllowOffOptionChange: (Boolean) -> Unit = {},
    onSleepDetectionChange: (Boolean) -> Unit = {},
    onDeviceNameChange: (String) -> Unit = {},
    onPressControlsClick: () -> Unit = {},
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
    val device = state.device
    val features = device?.model?.features
    val enabled = device?.isAapReady == true
    val isPro = state.isPro

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
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_manufacturer_label),
                                        info.manufacturer
                                    )
                                )
                            }
                            if (info.serialNumber.isNotBlank()) {
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_serial_label),
                                        info.serialNumber
                                    )
                                )
                            }
                            val hasFirmware = info.firmwareVersion.isNotBlank()
                            val hasBuild = !info.buildNumber.isNullOrBlank()
                            if (hasFirmware && hasBuild) {
                                add(
                                    DeviceDetailItem.Paired(
                                        start = DeviceDetailItem.Single(
                                            stringResource(R.string.device_settings_info_firmware_label),
                                            info.firmwareVersion
                                        ),
                                        end = DeviceDetailItem.Single(
                                            stringResource(R.string.device_settings_info_build_label),
                                            info.buildNumber!!
                                        ),
                                    )
                                )
                            } else if (hasFirmware) {
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_firmware_label),
                                        info.firmwareVersion
                                    )
                                )
                            } else if (hasBuild) {
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_build_label),
                                        info.buildNumber!!
                                    )
                                )
                            }
                            val hasLeft = !info.leftEarbudSerial.isNullOrBlank()
                            val hasRight = !info.rightEarbudSerial.isNullOrBlank()
                            if (hasLeft && hasRight) {
                                add(
                                    DeviceDetailItem.Paired(
                                        start = DeviceDetailItem.Single(
                                            stringResource(R.string.device_settings_info_left_serial_label),
                                            info.leftEarbudSerial!!
                                        ),
                                        end = DeviceDetailItem.Single(
                                            stringResource(R.string.device_settings_info_right_serial_label),
                                            info.rightEarbudSerial!!
                                        ),
                                    )
                                )
                            } else if (hasLeft) {
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_left_serial_label),
                                        info.leftEarbudSerial!!
                                    )
                                )
                            } else if (hasRight) {
                                add(
                                    DeviceDetailItem.Single(
                                        stringResource(R.string.device_settings_info_right_serial_label),
                                        info.rightEarbudSerial!!
                                    )
                                )
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
                    ReactionsCard(
                        device = device,
                        features = features,
                        isPro = isPro,
                        monitorMode = state.monitorMode,
                        onAutoPlayChange = onAutoPlayChange,
                        onAutoPauseChange = onAutoPauseChange,
                        onOnePodModeChange = onOnePodModeChange,
                        onConversationalAwarenessChange = onConversationalAwarenessChange,
                        onSleepDetectionChange = onSleepDetectionChange,
                        onAutoConnectChange = onAutoConnectChange,
                        onAutoConnectConditionChange = onAutoConnectConditionChange,
                        onShowPopUpOnCaseOpenChange = onShowPopUpOnCaseOpenChange,
                        onShowPopUpOnConnectionChange = onShowPopUpOnConnectionChange,
                        onFixMonitorMode = onFixMonitorMode,
                        onOpenIssueTracker = onOpenIssueTracker,
                    )
                }
            }

            // Settings — only show when AAP is connected
            if (features != null && device.isAapConnected) {

                if (device.hasPendingSettings == true) {
                    item("pending_info") {
                        SettingsInfoBox(
                            text = stringResource(R.string.device_settings_pending_info),
                        )
                    }
                }

                // ── Noise Control ────────────────────────────
                if (features.hasAncControl && device.ancMode != null) {
                    item("noise_control_section") {
                        NoiseControlCard(
                            device = device,
                            features = features,
                            isPro = isPro,
                            enabled = enabled,
                            hasCustomLongPressStemAction = state.hasCustomLongPressStemAction,
                            showListeningModeCycleDialog = showListeningModeCycleDialog,
                            onShowListeningModeCycleDialogChange = onShowListeningModeCycleDialogChange,
                            onAncModeChange = onAncModeChange,
                            onNcWithOneAirPodChange = onNcWithOneAirPodChange,
                            onAdaptiveAudioNoiseChange = onAdaptiveAudioNoiseChange,
                            onAllowOffOptionChange = onAllowOffOptionChange,
                            onListeningModeCycleChange = onListeningModeCycleChange,
                            onPressControlsClick = onPressControlsClick,
                            onUpgrade = onUpgrade,
                        )
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
                        SoundCard(
                            device = device,
                            features = features,
                            isPro = isPro,
                            enabled = enabled,
                            onPersonalizedVolumeChange = onPersonalizedVolumeChange,
                            onToneVolumeChange = onToneVolumeChange,
                            onMicrophoneModeChange = onMicrophoneModeChange,
                            onUpgrade = onUpgrade,
                            onOpenIssueTracker = onOpenIssueTracker,
                        )
                    }
                }

                // ── Controls ─────────────────────────────────
                val showControlsSection = features.hasStemConfig ||
                        (features.hasEndCallMuteMic && device.endCallMuteMic != null) ||
                        (features.hasPressSpeed && device.pressSpeed != null) ||
                        (features.hasPressHoldDuration && device.pressHoldDuration != null) ||
                        (features.hasVolumeSwipe && device.volumeSwipe != null) ||
                        (features.hasVolumeSwipeLength && device.volumeSwipeLength != null)
                if (showControlsSection) {
                    item("controls_section") {
                        ControlsCard(
                            device = device,
                            features = features,
                            enabled = enabled,
                            onPressControlsClick = onPressControlsClick,
                            onVolumeSwipeChange = onVolumeSwipeChange,
                            onVolumeSwipeLengthChange = onVolumeSwipeLengthChange,
                        )
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
    }
}

internal fun previewFullState(isPro: Boolean) = DeviceSettingsViewModel.State(
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
