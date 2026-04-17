package eu.darken.capod.main.ui.presscontrols

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.reaction.core.stem.StemAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig

@Composable
fun PressControlsScreenHost(
    profileId: String,
    vm: PressControlsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)

    LaunchedEffect(profileId) { vm.initialize(profileId) }

    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedTemplate = stringResource(R.string.device_settings_send_failed, "%1\$s")

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PressControlsViewModel.Event.SendFailed -> {
                    snackbarHostState.showSnackbar(
                        sendFailedTemplate.format(event.message ?: ""),
                    )
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    PressControlsScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onReset = { vm.resetAll() },
        onLeftSingle = { vm.setLeftSingle(it) },
        onLeftDouble = { vm.setLeftDouble(it) },
        onLeftTriple = { vm.setLeftTriple(it) },
        onLeftLong = { vm.setLeftLong(it) },
        onRightSingle = { vm.setRightSingle(it) },
        onRightDouble = { vm.setRightDouble(it) },
        onRightTriple = { vm.setRightTriple(it) },
        onRightLong = { vm.setRightLong(it) },
        onPressSpeedChange = { vm.setPressSpeed(it) },
        onPressHoldDurationChange = { vm.setPressHoldDuration(it) },
        onEndCallMuteMicChange = { muteMic, endCall -> vm.setEndCallMuteMic(muteMic, endCall) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PressControlsScreen(
    state: PressControlsViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onReset: () -> Unit = {},
    onLeftSingle: (StemAction) -> Unit = {},
    onLeftDouble: (StemAction) -> Unit = {},
    onLeftTriple: (StemAction) -> Unit = {},
    onLeftLong: (StemAction) -> Unit = {},
    onRightSingle: (StemAction) -> Unit = {},
    onRightDouble: (StemAction) -> Unit = {},
    onRightTriple: (StemAction) -> Unit = {},
    onRightLong: (StemAction) -> Unit = {},
    onPressSpeedChange: (AapSetting.PressSpeed.Value) -> Unit = {},
    onPressHoldDurationChange: (AapSetting.PressHoldDuration.Value) -> Unit = {},
    onEndCallMuteMicChange: (
        AapSetting.EndCallMuteMic.MuteMicMode,
        AapSetting.EndCallMuteMic.EndCallMode,
    ) -> Unit = { _, _ -> },
) {
    var showResetDialog by remember { mutableStateOf(false) }

    val device = state.device
    val features: PodModel.Features? = device?.model?.features
    val hasStemConfig = features?.hasStemConfig == true
    val hasPressSpeed = features?.hasPressSpeed == true && device.pressSpeed != null
    val hasPressHoldDuration = features?.hasPressHoldDuration == true && device.pressHoldDuration != null
    val hasEndCallMuteMic = features?.hasEndCallMuteMic == true && device.endCallMuteMic != null

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = { Text(stringResource(R.string.press_controls_reset_confirm_message)) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.press_controls_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (hasStemConfig) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                imageVector = Icons.TwoTone.RestartAlt,
                                contentDescription = stringResource(R.string.press_controls_reset_label),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item("description") {
                Text(
                    text = stringResource(R.string.press_controls_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ── Press timing (device-scoped AAP settings) ────────────────────
            if (hasPressSpeed) {
                item("press_speed") {
                    PressSpeedSetting(
                        selected = device.pressSpeed!!.value,
                        onSelected = onPressSpeedChange,
                        enabled = state.isAapReady,
                    )
                }
            }
            if (hasPressHoldDuration) {
                item("press_hold") {
                    PressHoldDurationSetting(
                        selected = device.pressHoldDuration!!.value,
                        onSelected = onPressHoldDurationChange,
                        enabled = state.isAapReady,
                    )
                }
            }
            if (hasEndCallMuteMic) {
                item("call_controls") {
                    CallControlSettings(
                        current = device.endCallMuteMic!!,
                        onChange = onEndCallMuteMicChange,
                        enabled = state.isAapReady,
                    )
                }
            }

            // ── Press mappings (profile-scoped, Pro-gated) ────────────────────
            if (hasStemConfig) {
                item("mappings_card") {
                    PressMappingsCard(
                        stemActions = state.stemActions,
                        isPro = state.isPro,
                        onLeftSingle = onLeftSingle,
                        onLeftDouble = onLeftDouble,
                        onLeftTriple = onLeftTriple,
                        onLeftLong = onLeftLong,
                        onRightSingle = onRightSingle,
                        onRightDouble = onRightDouble,
                        onRightTriple = onRightTriple,
                        onRightLong = onRightLong,
                    )
                }
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

internal fun previewPressControlsState(
    isPro: Boolean,
    hasStemConfig: Boolean = true,
    stemActions: StemActionsConfig = StemActionsConfig(),
): PressControlsViewModel.State {
    val model = if (hasStemConfig) PodModel.AIRPODS_PRO2 else PodModel.AIRPODS_GEN2
    val device = PodDevice(
        profileId = "preview",
        label = "My AirPods",
        ble = MockPodDataProvider.airPodsProWithKeys(),
        aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            deviceInfo = AapDeviceInfo(
                name = if (hasStemConfig) "AirPods Pro" else "AirPods",
                modelNumber = "A2699",
                manufacturer = "Apple Inc.",
                serialNumber = "W5J7KV0N04",
                firmwareVersion = "7A305",
            ),
            settings = mapOf(
                AapSetting.PressSpeed::class to AapSetting.PressSpeed(value = AapSetting.PressSpeed.Value.DEFAULT),
                AapSetting.PressHoldDuration::class to AapSetting.PressHoldDuration(value = AapSetting.PressHoldDuration.Value.DEFAULT),
                AapSetting.EndCallMuteMic::class to AapSetting.EndCallMuteMic(
                    muteMic = AapSetting.EndCallMuteMic.MuteMicMode.DOUBLE_PRESS,
                    endCall = AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS,
                ),
            ),
        ),
    )
    return PressControlsViewModel.State(
        device = device,
        profile = AppleDeviceProfile(label = "My AirPods", model = model, address = "AA:BB:CC:DD:EE:FF", stemActions = stemActions),
        stemActions = stemActions,
        isPro = isPro,
        isAapReady = true,
    )
}

@Preview2
@Composable
private fun PressControlsScreenProPreview() = PreviewWrapper {
    PressControlsScreen(
        state = previewPressControlsState(
            isPro = true,
            stemActions = StemActionsConfig(
                leftSingle = StemAction.PLAY_PAUSE,
                rightSingle = StemAction.NO_ACTION,
                leftLong = StemAction.NEXT_TRACK,
            ),
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun PressControlsScreenNonProPreview() = PreviewWrapper {
    PressControlsScreen(
        state = previewPressControlsState(isPro = false),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun PressControlsScreenPressOnlyPreview() = PreviewWrapper {
    // Device with timing/call settings but no stem mapping support.
    PressControlsScreen(
        state = previewPressControlsState(isPro = true, hasStemConfig = false),
        onNavigateUp = {},
    )
}
