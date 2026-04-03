package eu.darken.capod.main.ui.devicesettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Swipe
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.TouchApp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsCategoryHeader
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.common.settings.SettingsSwitchItem
import eu.darken.capod.main.ui.overview.cards.AncModeSelector
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun DeviceSettingsScreenHost(
    address: String,
    vm: DeviceSettingsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(address) { vm.initialize(address) }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return

    DeviceSettingsScreen(
        state = currentState,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    state: DeviceSettingsViewModel.State,
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
) {
    val device = state.device
    val features = device?.model?.features
    val enabled = device?.isAapReady == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.device_settings_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
        ) {
            // Device Info
            val deviceInfo = device?.deviceInfo
            if (deviceInfo != null) {
                item("device_info") {
                    DeviceInfoCard(deviceInfo = deviceInfo)
                }
            }

            // Sound section — only show when AAP is connected (settings come from AAP)
            if (features != null && device.isAapConnected) {
                item("sound_header") {
                    SettingsCategoryHeader(text = stringResource(R.string.device_settings_category_sound_label))
                }

                val ancMode = device.ancMode
                if (features.hasAncControl && ancMode != null) {
                    item("anc_mode") {
                        AncModeSelector(
                            currentMode = ancMode.current,
                            supportedModes = ancMode.supported,
                            onModeSelected = onAncModeChange,
                            pendingMode = device.pendingAncMode,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            enabled = enabled,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

                val personalizedVol = device.personalizedVolume
                if (features.hasPersonalizedVolume && personalizedVol != null) {
                    item("personalized_volume") {
                        SettingsSwitchItem(
                            icon = Icons.AutoMirrored.TwoTone.VolumeUp,
                            title = stringResource(R.string.device_settings_personalized_volume_label),
                            subtitle = stringResource(R.string.device_settings_personalized_volume_description),
                            checked = personalizedVol.enabled,
                            onCheckedChange = onPersonalizedVolumeChange,
                            enabled = enabled,
                        )
                    }
                }

                val toneVol = device.toneVolume
                if (features.hasToneVolume && toneVol != null) {
                    item("tone_volume") {
                        ToneVolumeSlider(
                            level = toneVol.level,
                            onLevelChange = onToneVolumeChange,
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

                // Controls section
                item("controls_header") {
                    SettingsCategoryHeader(text = stringResource(R.string.device_settings_category_controls_label))
                }

                val pressSpd = device.pressSpeed
                if (features.hasPressSpeed && pressSpd != null) {
                    item("press_speed") {
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
                }

                val pressHold = device.pressHoldDuration
                if (features.hasPressHoldDuration && pressHold != null) {
                    item("press_hold") {
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
                }

                val volSwipe = device.volumeSwipe
                if (features.hasVolumeSwipe && volSwipe != null) {
                    item("volume_swipe") {
                        SettingsSwitchItem(
                            icon = Icons.TwoTone.Swipe,
                            title = stringResource(R.string.device_settings_volume_swipe_label),
                            subtitle = stringResource(R.string.device_settings_volume_swipe_description),
                            checked = volSwipe.enabled,
                            onCheckedChange = onVolumeSwipeChange,
                            enabled = enabled,
                        )
                    }
                }

                val volSwipeLen = device.volumeSwipeLength
                if (features.hasVolumeSwipeLength && volSwipeLen != null) {
                    item("volume_swipe_length") {
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

                val endCallMuteMic = device.endCallMuteMic
                if (features.hasEndCallMuteMic && endCallMuteMic != null) {
                    item("end_call_mute_mic") {
                        EndCallMuteMicControl(
                            current = endCallMuteMic,
                            onChange = onEndCallMuteMicChange,
                            enabled = enabled,
                        )
                    }
                }
            }

            item("bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(deviceInfo: AapDeviceInfo) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (deviceInfo.name.isNotBlank()) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_name_label),
                    value = deviceInfo.name,
                )
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
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
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
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun EndCallMuteMicControl(
    current: AapSetting.EndCallMuteMic,
    onChange: (AapSetting.EndCallMuteMic.MuteMicMode, AapSetting.EndCallMuteMic.EndCallMode) -> Unit,
    enabled: Boolean,
) {
    val optionASelected = current.endCall == AapSetting.EndCallMuteMic.EndCallMode.SINGLE_PRESS

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.TwoTone.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
                modifier = Modifier.padding(end = 16.dp, top = 4.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.device_settings_end_call_mute_mic_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                )
                Text(
                    text = stringResource(R.string.device_settings_end_call_mute_mic_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
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
