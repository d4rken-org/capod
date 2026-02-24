package eu.darken.capod.main.ui.overview

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.darken.capod.R
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.ui.overview.cards.BluetoothDisabledCard
import eu.darken.capod.main.ui.overview.cards.DualPodsCard
import eu.darken.capod.main.ui.overview.cards.MonitoringActiveCard
import eu.darken.capod.main.ui.overview.cards.NoProfilesCard
import eu.darken.capod.main.ui.overview.cards.PermissionCard
import eu.darken.capod.main.ui.overview.cards.SinglePodsCard
import eu.darken.capod.main.ui.overview.cards.UnknownPodDeviceCard
import eu.darken.capod.main.ui.overview.cards.UnmatchedDevicesCard
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import java.time.Instant

@Composable
fun OverviewScreenHost(vm: OverviewViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? Activity

    // Collect workerAutolaunch passively to keep it active
    LaunchedEffect(Unit) {
        vm.workerAutolaunch.collect {}
    }

    // Permission handling
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.onPermissionResult(granted)
    }

    // When returning from settings-based permissions
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission) {
            awaitingPermission = false
            vm.onPermissionResult(true)
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

    // Handle upgrade flow events
    LaunchedEffect(Unit) {
        vm.launchUpgradeFlow.collect { action ->
            activity?.let { action(it) }
        }
    }

    val stateHolder = waitForState(vm.state)
    val state = stateHolder.value ?: return

    OverviewScreen(
        state = state,
        onRequestPermission = { vm.requestPermission(it) },
        onManageDevices = { vm.goToDeviceManager() },
        onSettings = { vm.goToSettings() },
        onUpgrade = { vm.onUpgrade() },
        onToggleUnmatched = { vm.toggleUnmatchedDevices() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewViewModel.State,
    onRequestPermission: (Permission) -> Unit,
    onManageDevices: () -> Unit,
    onSettings: () -> Unit,
    onUpgrade: () -> Unit,
    onToggleUnmatched: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ToolbarTitle(upgradeInfo = state.upgradeInfo)
                },
                actions = {
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
                                    imageVector = Icons.TwoTone.Favorite,
                                    contentDescription = stringResource(R.string.general_donate_action),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // 1. Permission cards
            items(
                items = state.permissions.toList(),
                key = { it.permissionId },
            ) { permission ->
                PermissionCard(
                    permission = permission,
                    onRequest = onRequestPermission,
                )
            }

            // 2. Bluetooth disabled card
            if (!state.isBluetoothEnabled && state.permissions.isEmpty()) {
                item(key = "bluetooth_disabled") {
                    BluetoothDisabledCard()
                }
            }

            // 3. No profiles card
            if (state.profiles.isEmpty() && state.permissions.isEmpty() && state.isBluetoothEnabled) {
                item(key = "no_profiles") {
                    NoProfilesCard(onManageDevices = onManageDevices)
                }
            }

            // 4. Profiled device cards
            if (state.permissions.isEmpty() && state.isBluetoothEnabled) {
                items(
                    items = state.profiledDevices,
                    key = { it.identifier.hashCode() },
                ) { device ->
                    PodDeviceCard(device = device, showDebug = state.isDebugMode, now = state.now)
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
                        items(
                            items = state.unmatchedDevices,
                            key = { "unmatched_${it.identifier.hashCode()}" },
                        ) { device ->
                            PodDeviceCard(device = device, showDebug = state.isDebugMode, now = state.now)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodDeviceCard(device: PodDevice, showDebug: Boolean, now: Instant) {
    when (device) {
        is DualPodDevice -> DualPodsCard(device = device, showDebug = showDebug, now = now)
        is SinglePodDevice -> SinglePodsCard(device = device, showDebug = showDebug, now = now)
        else -> UnknownPodDeviceCard(device = device, showDebug = showDebug, now = now)
    }
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
