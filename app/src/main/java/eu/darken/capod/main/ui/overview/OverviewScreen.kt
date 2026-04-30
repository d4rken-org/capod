package eu.darken.capod.main.ui.overview

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.SystemTimeSource
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.ui.overview.cards.BluetoothDisabledCard
import eu.darken.capod.main.ui.overview.cards.DeviceLimitUpgradeCard
import eu.darken.capod.main.ui.overview.cards.DualPodsCard
import eu.darken.capod.main.ui.overview.cards.MonitoringActiveCard
import eu.darken.capod.main.ui.overview.cards.NoProfilesCard
import eu.darken.capod.main.ui.overview.cards.PermissionCard
import eu.darken.capod.main.ui.overview.cards.ReactionsMovedHintCard
import eu.darken.capod.main.ui.overview.cards.SinglePodsCard
import eu.darken.capod.main.ui.overview.cards.UnknownPodDeviceCard
import eu.darken.capod.main.ui.overview.cards.UnmatchedDevicesCard
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import java.time.Instant

internal fun profiledDeviceKey(
    device: PodDevice,
    index: Int,
    duplicateProfileIds: Set<String>,
): String {
    val pid = requireNotNull(device.profileId)
    return if (pid !in duplicateProfileIds) {
        "profiled:$pid"
    } else {
        "profiled:$pid:${device.identifier ?: "idx:$index"}"
    }
}

internal fun unmatchedDeviceKey(device: PodDevice, index: Int): String =
    "unmatched:${device.identifier ?: "idx:$index"}"

@Composable
fun OverviewScreenHost(vm: OverviewViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val offRejectedMessage = stringResource(R.string.device_settings_anc_off_rejected_message)

    // Collect workerAutolaunch passively to keep it active
    LaunchedEffect(Unit) {
        vm.workerAutolaunch.collect {}
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                OverviewViewModel.Event.OffModeRejectedByDevice -> {
                    snackbarHostState.showSnackbar(offRejectedMessage)
                }
            }
        }
    }

    // Permission handling
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        vm.onPermissionResult()
    }

    // When returning from settings-based permissions
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission) {
            awaitingPermission = false
            vm.onSettingsPermissionResult()
        }
    }

    // Handle permission request events
    LaunchedEffect(Unit) {
        vm.requestPermissionEvent.collect { permission ->
            when (permission) {
                Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                    awaitingPermission = true
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }

                Permission.SYSTEM_ALERT_WINDOW -> {
                    awaitingPermission = true
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }

                else -> {
                    permissionLauncher.launch(permission.permissionId)
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    OverviewScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onRequestPermission = { vm.requestPermission(it) },
        onBluetoothSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: SecurityException) {
                // Some devices require BT permission to open BT settings
            }
        },
        onManageDevices = { vm.goToDeviceManager() },
        onSettings = { vm.goToSettings() },
        onUpgrade = { vm.onUpgrade() },
        onToggleUnmatched = { vm.toggleUnmatchedDevices() },
        onAncModeChange = { device, mode -> vm.setAncMode(device, mode) },
        onDeviceSettings = { device ->
            if (currentState.showReactionsHint) vm.dismissReactionsHint()
            vm.goToDeviceSettings(device)
        },
        onEditProfile = { device -> vm.goToEditProfile(device) },
        onToggleDeviceExpansion = { device ->
            device.profileId?.let { vm.toggleDeviceExpansion(it) }
        },
    )
}

@Composable
fun OverviewScreen(
    state: OverviewViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRequestPermission: (Permission) -> Unit,
    onBluetoothSettings: () -> Unit,
    onManageDevices: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onToggleUnmatched: () -> Unit,
    onAncModeChange: (PodDevice, AapSetting.AncMode.Value) -> Unit = { _, _ -> },
    onDeviceSettings: (PodDevice) -> Unit = {},
    onEditProfile: (PodDevice) -> Unit = {},
    onToggleDeviceExpansion: (PodDevice) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ToolbarTitle(upgradeInfo = state.upgradeInfo)
                },
                actions = {
                    // Bluetooth status icon
                    val btState = state.bluetoothIconState
                    if (btState != OverviewViewModel.BluetoothIconState.HIDDEN) {
                        IconButton(onClick = onBluetoothSettings) {
                            Icon(
                                imageVector = when (btState) {
                                    OverviewViewModel.BluetoothIconState.CONNECTED -> Icons.TwoTone.BluetoothConnected
                                    else -> Icons.TwoTone.Bluetooth
                                },
                                contentDescription = stringResource(
                                    when (btState) {
                                        OverviewViewModel.BluetoothIconState.DISABLED -> R.string.overview_bluetooth_disabled_icon_cd
                                        OverviewViewModel.BluetoothIconState.CONNECTED -> R.string.overview_bluetooth_connected_icon_cd
                                        else -> R.string.overview_bluetooth_nearby_icon_cd
                                    }
                                ),
                                tint = when (btState) {
                                    OverviewViewModel.BluetoothIconState.DISABLED -> MaterialTheme.colorScheme.error
                                    OverviewViewModel.BluetoothIconState.CONNECTED -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }

                    // Upgrade/donate button based on type and pro status
                    val info = state.upgradeInfo
                    when {
                        info.type == UpgradeRepo.Type.GPLAY && !info.isPro -> {
                            IconButton(onClick = onUpgrade) {
                                Icon(
                                    imageVector = Icons.TwoTone.Stars,
                                    contentDescription = stringResource(R.string.general_upgrade_action),
                                )
                            }
                        }

                        info.type == UpgradeRepo.Type.FOSS && !info.isPro -> {
                            IconButton(onClick = onUpgrade) {
                                Icon(
                                    imageVector = Icons.TwoTone.Stars,
                                    contentDescription = stringResource(R.string.general_donate_action),
                                )
                            }
                        }
                    }

                    IconButton(onClick = onManageDevices) {
                        Icon(
                            imageVector = Icons.TwoTone.DevicesOther,
                            contentDescription = stringResource(R.string.settings_devices_label),
                        )
                    }

                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = stringResource(R.string.settings_general_label),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // 1. Permission cards
            items(
                items = state.permissions.sortedByDescending { it.isScanBlocking },
                key = { it.permissionId },
            ) { permission ->
                PermissionCard(
                    permission = permission,
                    onRequest = onRequestPermission,
                )
            }

            // 2. Bluetooth disabled card
            if (!state.isBluetoothEnabled && !state.isScanBlocked) {
                item(key = "bluetooth_disabled") {
                    BluetoothDisabledCard()
                }
            }

            // 3. No profiles card
            if (state.profiles.isEmpty() && !state.isScanBlocked && state.isBluetoothEnabled) {
                item(key = "no_profiles") {
                    NoProfilesCard(onManageDevices = onManageDevices)
                }
            }

            // 4. Profiled device cards (limited to 1 for free users)
            if (!state.isScanBlocked && state.isBluetoothEnabled) {
                if (state.showReactionsHint && state.visibleProfiledDevices.isNotEmpty()) {
                    item(key = "reactions_hint") {
                        ReactionsMovedHintCard()
                    }
                }

                val duplicateProfileIds = state.visibleProfiledDevices
                    .mapNotNull { it.profileId }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                itemsIndexed(
                    items = state.visibleProfiledDevices,
                    key = { index, device -> profiledDeviceKey(device, index, duplicateProfileIds) },
                ) { index, device ->
                    val isCollapsed = !state.isExpanded(device, index)
                    val isToggleable = state.isToggleable(device, index)
                    PodDeviceCard(
                        device = device,
                        isPro = state.upgradeInfo.isPro,
                        showDebug = state.isDebugMode,
                        now = state.now,
                        isCollapsed = isCollapsed,
                        onToggleCollapse = if (isToggleable) {
                            { onToggleDeviceExpansion(device) }
                        } else null,
                        onAncModeChange = { mode -> onAncModeChange(device, mode) },
                        onUpgrade = onUpgrade,
                        onDeviceSettings = { onDeviceSettings(device) },
                        onEditProfile = { onEditProfile(device) },
                    )
                }

                // 4b. Upgrade card when additional devices are hidden
                if (state.hiddenProfiledDeviceCount > 0) {
                    item(key = "device_limit_upgrade") {
                        DeviceLimitUpgradeCard(
                            hiddenCount = state.hiddenProfiledDeviceCount,
                            upgradeType = state.upgradeInfo.type,
                            onUpgrade = onUpgrade,
                        )
                    }
                }

                // 5. Monitoring active card
                if (state.profiles.isNotEmpty() && state.devices.isEmpty()) {
                    item(key = "monitoring_active") {
                        MonitoringActiveCard()
                    }
                }

                // 6. Unmatched devices section
                if (state.unmatchedDevices.isNotEmpty()) {
                    item(key = "unmatched_header") {
                        UnmatchedDevicesCard(
                            count = state.unmatchedDevices.size,
                            isExpanded = state.showUnmatchedDevices,
                            onToggle = onToggleUnmatched,
                        )
                    }

                    if (state.showUnmatchedDevices) {
                        itemsIndexed(
                            items = state.unmatchedDevices,
                            key = { index, device -> unmatchedDeviceKey(device, index) },
                        ) { _, device ->
                            PodDeviceCard(
                                device = device,
                                isPro = state.upgradeInfo.isPro,
                                showDebug = state.isDebugMode,
                                now = state.now,
                                onAncModeChange = { mode -> onAncModeChange(device, mode) },
                                onUpgrade = onUpgrade,
                                onDeviceSettings = { onDeviceSettings(device) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodDeviceCard(
    device: PodDevice,
    isPro: Boolean,
    showDebug: Boolean,
    now: Instant,
    isCollapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    onAncModeChange: (AapSetting.AncMode.Value) -> Unit,
    onUpgrade: () -> Unit,
    onDeviceSettings: (() -> Unit)? = null,
    onEditProfile: (() -> Unit)? = null,
) {
    when {
        device.hasDualPods -> DualPodsCard(
            device = device, isPro = isPro, showDebug = showDebug, now = now,
            isCollapsed = isCollapsed,
            onToggleCollapse = onToggleCollapse,
            onAncModeChange = onAncModeChange,
            onUpgrade = onUpgrade,
            onDeviceSettings = onDeviceSettings,
            onEditProfile = onEditProfile,
        )
        device.model != PodModel.UNKNOWN -> SinglePodsCard(
            device = device, isPro = isPro, showDebug = showDebug, now = now,
            isCollapsed = isCollapsed,
            onToggleCollapse = onToggleCollapse,
            onAncModeChange = onAncModeChange,
            onUpgrade = onUpgrade,
            onDeviceSettings = onDeviceSettings,
            onEditProfile = onEditProfile,
        )
        else -> UnknownPodDeviceCard(device = device, showDebug = showDebug)
    }
}

@Preview2
@Composable
private fun OverviewScreenWithDevicesPreview() = PreviewWrapper {
    OverviewScreen(
        state = OverviewViewModel.State(
            now = SystemTimeSource.now(),
            permissions = emptySet(),
            devices = listOf(
                MockPodDataProvider.dualPodMonitoredMixed(),
                MockPodDataProvider.singlePodMonitored(),
                MockPodDataProvider.unknownMonitored(),
            ),
            isDebugMode = false,
            isBluetoothEnabled = true,
            profiles = listOf(
                MockPodDataProvider.profile("My AirPods Pro", PodModel.AIRPODS_PRO2),
                MockPodDataProvider.profile("AirPods Max", PodModel.AIRPODS_MAX),
            ),
            upgradeInfo = MockPodDataProvider.fossInfo(),
            showUnmatchedDevices = false,
            showReactionsHint = true,
        ),
        onRequestPermission = {},
        onBluetoothSettings = {},
        onManageDevices = {},
        onSettings = {},
        onUpgrade = {},
        onToggleUnmatched = {},
    )
}

@Preview2
@Composable
private fun OverviewScreenEmptyPreview() = PreviewWrapper {
    OverviewScreen(
        state = OverviewViewModel.State(
            now = SystemTimeSource.now(),
            permissions = emptySet(),
            devices = emptyList(),
            isDebugMode = false,
            isBluetoothEnabled = true,
            profiles = listOf(MockPodDataProvider.profile("My AirPods", PodModel.AIRPODS_PRO2)),
            upgradeInfo = MockPodDataProvider.fossInfo(),
            showUnmatchedDevices = false,
        ),
        onRequestPermission = {},
        onBluetoothSettings = {},
        onManageDevices = {},
        onSettings = {},
        onUpgrade = {},
        onToggleUnmatched = {},
    )
}

@Preview2
@Composable
private fun OverviewScreenNoProfilesPreview() = PreviewWrapper {
    OverviewScreen(
        state = OverviewViewModel.State(
            now = SystemTimeSource.now(),
            permissions = emptySet(),
            devices = emptyList(),
            isDebugMode = false,
            isBluetoothEnabled = true,
            profiles = emptyList(),
            upgradeInfo = MockPodDataProvider.gplayInfo(),
            showUnmatchedDevices = false,
        ),
        onRequestPermission = {},
        onBluetoothSettings = {},
        onManageDevices = {},
        onSettings = {},
        onUpgrade = {},
        onToggleUnmatched = {},
    )
}

@Preview2
@Composable
private fun OverviewScreenBluetoothOffPreview() = PreviewWrapper {
    OverviewScreen(
        state = OverviewViewModel.State(
            now = SystemTimeSource.now(),
            permissions = emptySet(),
            devices = emptyList(),
            isDebugMode = false,
            isBluetoothEnabled = false,
            profiles = listOf(MockPodDataProvider.profile("My AirPods", PodModel.AIRPODS_PRO2)),
            upgradeInfo = MockPodDataProvider.fossInfo(isPro = true),
            showUnmatchedDevices = false,
        ),
        onRequestPermission = {},
        onBluetoothSettings = {},
        onManageDevices = {},
        onSettings = {},
        onUpgrade = {},
        onToggleUnmatched = {},
    )
}

@Composable
private fun ToolbarTitle(upgradeInfo: UpgradeRepo.Info) {
    val appName = stringResource(R.string.app_name)
    val proName = stringResource(R.string.app_name_pro)
    val fossName = stringResource(R.string.app_name_foss)

    val titleParts = when (upgradeInfo.type) {
        UpgradeRepo.Type.GPLAY -> {
            if (upgradeInfo.isPro) proName else appName
        }

        UpgradeRepo.Type.FOSS -> {
            if (upgradeInfo.isPro) fossName else appName
        }
    }.split(" ").filter { it.isNotEmpty() }

    if (titleParts.size == 2) {
        val suffixColor = when (upgradeInfo.type) {
            UpgradeRepo.Type.FOSS -> colorResource(R.color.brand_secondary)
            else -> colorResource(R.color.brand_tertiary)
        }

        Text(
            text = buildAnnotatedString {
                append("${titleParts[0]} ")
                withStyle(SpanStyle(color = suffixColor)) {
                    append(titleParts[1])
                }
            },
        )
    } else {
        Text(text = appName)
    }
}
