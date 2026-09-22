package eu.darken.capod.main.ui.equalizer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Tune
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import eu.darken.capod.common.settings.InfoBoxType
import eu.darken.capod.common.settings.SettingsInfoBox
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.main.ui.devicesettings.cards.CUSTOM_EQ_NEUTRAL
import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun EqualizerScreenHost(
    profileId: String,
    vm: EqualizerViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)

    LaunchedEffect(profileId) { vm.initialize(profileId) }

    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailedTemplate = stringResource(R.string.device_settings_send_failed, "%1\$s")

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EqualizerViewModel.Event.SendFailed -> {
                    snackbarHostState.showSnackbar(
                        sendFailedTemplate.format(event.message ?: ""),
                    )
                }
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    val isAapConnected = currentState.device?.isAapConnected == true
    var hasSeenAapConnected by rememberSaveable(profileId) { mutableStateOf(false) }
    var didAutoNavigate by rememberSaveable(profileId) { mutableStateOf(false) }
    LaunchedEffect(profileId, isAapConnected) {
        if (isAapConnected) {
            hasSeenAapConnected = true
        } else if (hasSeenAapConnected && !didAutoNavigate) {
            didAutoNavigate = true
            vm.navUp()
        }
    }

    EqualizerScreen(
        state = currentState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { vm.navUp() },
        onSetUp = { vm.setUpNeutral() },
        onEnabledChange = { vm.setEnabled(it) },
        onLowChange = { vm.setLow(it) },
        onMidChange = { vm.setMid(it) },
        onHighChange = { vm.setHigh(it) },
        onReset = { vm.resetToNeutral() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    state: EqualizerViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onSetUp: () -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    onLowChange: (Int) -> Unit = {},
    onMidChange: (Int) -> Unit = {},
    onHighChange: (Int) -> Unit = {},
    onReset: () -> Unit = {},
) {
    var showResetDialog by remember { mutableStateOf(false) }

    val draft = state.draft
    val bandsEnabled = state.isAapReady && draft?.enabled == true

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
            text = { Text(stringResource(R.string.device_settings_equalizer_reset_confirm_message)) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_settings_equalizer_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (draft != null) {
                        IconButton(
                            onClick = { showResetDialog = true },
                            enabled = state.isAapReady,
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.RestartAlt,
                                contentDescription = stringResource(R.string.device_settings_equalizer_reset_label),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            if (draft == null) {
                item("unknown") {
                    SettingsInfoBox(
                        text = stringResource(R.string.device_settings_equalizer_unknown_description),
                        action = {
                            TextButton(onClick = onSetUp, enabled = state.isAapReady) {
                                Text(stringResource(R.string.device_settings_equalizer_setup_action))
                            }
                        },
                    )
                }
            } else {
                item("description") {
                    Text(
                        text = stringResource(R.string.device_settings_equalizer_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item("state") {
                    SegmentedSettingRow(
                        icon = Icons.TwoTone.Tune,
                        title = stringResource(R.string.device_settings_equalizer_label),
                        options = listOf(
                            stringResource(R.string.device_settings_equalizer_state_off) to false,
                            stringResource(R.string.device_settings_equalizer_state_custom) to true,
                        ),
                        selected = draft.enabled,
                        onSelected = onEnabledChange,
                        enabled = state.isAapReady,
                    )
                }
                item("band_low") {
                    BandSlider(
                        title = stringResource(R.string.device_settings_equalizer_band_low),
                        level = draft.low,
                        onLevelChange = onLowChange,
                        enabled = bandsEnabled,
                    )
                }
                item("band_mid") {
                    BandSlider(
                        title = stringResource(R.string.device_settings_equalizer_band_mid),
                        level = draft.mid,
                        onLevelChange = onMidChange,
                        enabled = bandsEnabled,
                    )
                }
                item("band_high") {
                    BandSlider(
                        title = stringResource(R.string.device_settings_equalizer_band_high),
                        level = draft.high,
                        onLevelChange = onHighChange,
                        enabled = bandsEnabled,
                    )
                }
            }

            if (state.hasPendingWrite) {
                item("pending") {
                    SettingsInfoBox(
                        text = stringResource(R.string.device_settings_equalizer_pending_note),
                        type = InfoBoxType.WARNING,
                    )
                }
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BandSlider(
    title: String,
    level: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean,
) {
    var sliderValue by remember(level) { mutableIntStateOf(level) }
    SettingsSliderItem(
        icon = Icons.TwoTone.GraphicEq,
        title = title,
        value = sliderValue.toFloat(),
        onValueChange = { sliderValue = it.toInt() },
        onValueChangeFinished = { onLevelChange(sliderValue) },
        valueRange = 0f..100f,
        steps = 99,
        enabled = enabled,
        valueLabel = {
            val offset = it.toInt() - CUSTOM_EQ_NEUTRAL
            if (offset > 0) "+$offset" else "$offset"
        },
    )
}

internal fun previewEqualizerState(
    customEq: AapSetting.CustomEq?,
    isPro: Boolean = true,
    hasPendingWrite: Boolean = false,
) = EqualizerViewModel.State(
    device = PodDevice(
        profileId = "preview",
        label = "My AirPods Pro",
        ble = MockPodDataProvider.airPodsProWithKeys(),
        aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = buildMap {
                if (customEq != null) put(AapSetting.CustomEq::class, customEq)
            },
        ),
    ),
    isPro = isPro,
    isAapReady = true,
    hasPendingWrite = hasPendingWrite,
    deviceState = customEq,
    draft = customEq,
)

@Preview2
@Composable
private fun EqualizerScreenUnknownPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(customEq = null),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun EqualizerScreenCustomPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(
            customEq = AapSetting.CustomEq(enabled = true, low = 62, mid = 50, high = 42),
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun EqualizerScreenOffPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(
            customEq = AapSetting.CustomEq(enabled = false, low = 62, mid = 50, high = 42),
        ),
        onNavigateUp = {},
    )
}

@Preview2
@Composable
private fun EqualizerScreenPendingPreview() = PreviewWrapper {
    EqualizerScreen(
        state = previewEqualizerState(
            customEq = AapSetting.CustomEq(enabled = true, low = 70, mid = 50, high = 30),
            hasPendingWrite = true,
        ),
        onNavigateUp = {},
    )
}
