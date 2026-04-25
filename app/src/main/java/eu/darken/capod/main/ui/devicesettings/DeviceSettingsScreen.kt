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
import androidx.compose.ui.platform.LocalConfiguration
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
import eu.darken.capod.main.ui.devicesettings.cards.BatteryCard
import eu.darken.capod.main.ui.devicesettings.cards.ControlsCard
import eu.darken.capod.main.ui.devicesettings.cards.DeviceInfoCard
import eu.darken.capod.main.ui.devicesettings.cards.NoiseControlCard
import eu.darken.capod.main.ui.devicesettings.cards.NotConnectedCard
import eu.darken.capod.main.ui.devicesettings.cards.ReactionsCard
import eu.darken.capod.main.ui.overview.cards.components.MissingPairedDeviceBanner
import eu.darken.capod.main.ui.devicesettings.cards.SoundCard
import eu.darken.capod.main.ui.devicesettings.cards.buildDeviceInfoDetailItems
import eu.darken.capod.main.ui.devicesettings.cards.buildModelLabel
import eu.darken.capod.main.ui.devicesettings.cards.rememberDeviceInfoDetailLabels
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    val chargeCapRejectedMessage = stringResource(R.string.device_settings_charge_cap_rejected_message)
    val pendingInfoMessage = stringResource(R.string.device_settings_pending_info)

    val hasPendingSettings = state?.device?.hasPendingSettings
    var lastPendingState by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasPendingSettings) {
        val current = hasPendingSettings ?: return@LaunchedEffect
        val previous = lastPendingState
        lastPendingState = current
        if (previous == false && current) {
            snackbarHostState.showSnackbar(pendingInfoMessage)
        }
    }

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

                DeviceSettingsViewModel.Event.DynamicEndOfChargeRejectedByDevice -> {
                    snackbarHostState.showSnackbar(chargeCapRejectedMessage)
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
        onDynamicEndOfChargeChange = { vm.setDynamicEndOfCharge(it) },
        onDeviceNameChange = { vm.setDeviceName(it) },
        onPressControlsClick = { vm.navToPressControls() },
        onEditProfile = { vm.navToEditProfile() },
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
        onOpenAapTracker = { vm.openAapCompatibilityTracker() },
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
    onDynamicEndOfChargeChange: (Boolean) -> Unit = {},
    onDeviceNameChange: (String) -> Unit = {},
    onPressControlsClick: () -> Unit = {},
    onEditProfile: () -> Unit = {},
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
    onOpenAapTracker: () -> Unit = {},
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
                    val locale = LocalConfiguration.current.locales[0]
                    val zoneId = ZoneId.systemDefault()
                    val dateFormatter = remember(locale, zoneId) {
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(locale)
                            .withZone(zoneId)
                    }
                    val detailItems = buildDeviceInfoDetailItems(
                        info = info,
                        labels = rememberDeviceInfoDetailLabels(),
                        formatDate = { instant -> dateFormatter.format(instant) },
                    )
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

            // Profile has no paired Bluetooth device — supersedes all other state cards
            if (device != null && !device.hasSelectedPairedDevice) {
                item("missing_paired_device") {
                    MissingPairedDeviceBanner(
                        onClick = onEditProfile,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Not nearby — no live BLE; settings require the device to be present
            if (device != null && device.hasSelectedPairedDevice &&
                device.ble == null && !state.isClassicallyConnected
            ) {
                item("not_nearby_info") {
                    SettingsInfoBox(
                        title = stringResource(R.string.device_settings_not_nearby_label),
                        text = stringResource(R.string.device_settings_not_nearby_description),
                    )
                }
            }

            // Not connected info — device is nearby but not connected; prompt user to connect
            if (device != null && device.hasSelectedPairedDevice &&
                device.ble != null && !device.isAapConnected && device.address != null &&
                !state.isClassicallyConnected
            ) {
                item("not_connected_info") {
                    NotConnectedCard(
                        isNudgeAvailable = state.isNudgeAvailable,
                        isForceConnecting = state.isForceConnecting,
                        onConnect = onForceConnect,
                    )
                }
            }

            // ── Reactions (gated on classic connection — needs phone to be the audio target) ──
            if (device != null && device.hasSelectedPairedDevice &&
                features != null && state.isClassicallyConnected
            ) {
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
            if (features != null && device.isAapConnected && device.hasSelectedPairedDevice) {

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

                // ── Battery ──────────────────────────────────
                if (features.hasDynamicEndOfCharge && device.dynamicEndOfCharge != null) {
                    item("battery_section") {
                        BatteryCard(
                            device = device,
                            features = features,
                            enabled = enabled,
                            onDynamicEndOfChargeChange = onDynamicEndOfChargeChange,
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

                // PME Config visualization (debug only, opcode 0x53).
                // PME = Personal Medical Equipment (hearing-aid band gains for iOS
                // 18.1+ AirPods Pro 2 hearing-aid mode). The bar chart reuses the
                // EQ visualization because the data shape (per-ear × per-profile ×
                // band gains) renders identically; hidden on all-zero payloads, which
                // just mean the user hasn't configured a hearing-aid profile yet.
                val pmeConfig = device.pmeConfig
                if (eu.darken.capod.BuildConfig.DEBUG &&
                    pmeConfig != null &&
                    pmeConfig.sets.isNotEmpty() &&
                    !pmeConfig.isAllZero
                ) {
                    item("eq_section") {
                        SettingsSection(title = stringResource(R.string.device_settings_eq_label)) {
                            EqBarsChart(
                                sets = pmeConfig.sets,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Advanced settings unavailable — phone's Bluetooth lacks AAP support; passive info, shown last
            if (device != null && device.hasSelectedPairedDevice &&
                device.ble != null && !device.isAapConnected && device.address != null &&
                state.isClassicallyConnected
            ) {
                item("aap_unavailable_info") {
                    AapUnavailableCard(onOpenTracker = onOpenAapTracker)
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
                hardwareVersion = "1.0.0",
                leftEarbudSerial = "H3KL7HR926JY",
                rightEarbudSerial = "H3KL2AYL26K0",
                marketingVersion = "8454624",
                leftEarbudFirstPaired = Instant.ofEpochSecond(1697480211L),
                rightEarbudFirstPaired = Instant.ofEpochSecond(1697480211L),
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
                AapSetting.DynamicEndOfCharge::class to AapSetting.DynamicEndOfCharge(enabled = true),
            ),
        ),
    ),
    now = MOCK_NOW,
    isPro = isPro,
    isClassicallyConnected = true,
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
private fun DeviceSettingsMissingPairedDevicePreview() = PreviewWrapper {
    DeviceSettingsScreen(
        state = DeviceSettingsViewModel.State(
            device = MockPodDataProvider.dualPodMissingPairedDevice(),
            now = MOCK_NOW,
        ),
        onNavigateUp = {},
    )
}
