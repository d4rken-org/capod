package eu.darken.capod.profiles.ui.creation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.toHex
import eu.darken.capod.pods.core.PodDevice

@Composable
fun DeviceProfileCreationScreenHost(
    profileId: String? = null,
    vm: DeviceProfileCreationViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.initialize(profileId) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)

    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.showUnsavedChangesEvent) {
        vm.showUnsavedChangesEvent.collect { showUnsavedChangesDialog = true }
    }
    LaunchedEffect(vm.showDeleteConfirmationEvent) {
        vm.showDeleteConfirmationEvent.collect { showDeleteDialog = true }
    }

    BackHandler {
        vm.onBackPressed()
    }

    state?.let {
        DeviceProfileCreationScreen(
            state = it,
            onBack = { vm.onBackPressed() },
            onSave = { vm.saveProfile() },
            onDelete = { vm.requestDeleteProfile() },
            onNameChange = { name -> vm.updateName(name) },
            onModelChange = { model -> vm.updateModel(model) },
            onDeviceChange = { device -> vm.updateSelectedDevice(device) },
            onIdentityKeyChange = { key -> vm.updateIdentityKey(key) },
            onEncryptionKeyChange = { key -> vm.updateEncryptionKey(key) },
            onSignalQualityChange = { quality -> vm.updateMinimumSignalQuality(quality) },
            onKeyGuide = { vm.openKeyGuide() },
        )
    }

    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onSave = {
                showUnsavedChangesDialog = false
                vm.saveProfile()
            },
            onDiscard = {
                showUnsavedChangesDialog = false
                vm.discardChanges()
            },
            onKeepEditing = {
                showUnsavedChangesDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onDelete = {
                showDeleteDialog = false
                vm.deleteProfile()
            },
            onCancel = {
                showDeleteDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceProfileCreationScreen(
    state: DeviceProfileCreationViewModel.State,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onModelChange: (PodDevice.Model) -> Unit,
    onDeviceChange: (BluetoothDevice2?) -> Unit,
    onIdentityKeyChange: (ByteArray?) -> Unit,
    onEncryptionKeyChange: (ByteArray?) -> Unit,
    onSignalQualityChange: (Float) -> Unit,
    onKeyGuide: () -> Unit,
) {
    val title = if (state.isEditMode) {
        stringResource(R.string.settings_devices_label)
    } else {
        stringResource(R.string.profiles_create_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSave,
                        enabled = state.canSave,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Save,
                            contentDescription = stringResource(R.string.profiles_save_action),
                            tint = if (state.canSave) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                    if (state.isEditMode) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = stringResource(R.string.profiles_delete_action),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DeviceInfoCard(
                name = state.name,
                nameError = state.nameError,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                selectedDevice = state.selectedDevice,
                bondedDevices = state.bondedDevices,
                onNameChange = onNameChange,
                onModelChange = onModelChange,
                onDeviceChange = onDeviceChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            KeyCard(
                title = stringResource(R.string.profiles_identitykey_label),
                description = stringResource(R.string.profiles_maindevice_identitykey_explanation),
                keyValue = state.identityKey,
                onKeyChange = onIdentityKeyChange,
                onKeyGuide = onKeyGuide,
            )

            Spacer(modifier = Modifier.height(16.dp))

            KeyCard(
                title = stringResource(R.string.profiles_maindevice_encryptionkey_label),
                description = stringResource(R.string.profiles_maindevice_encryptionkey_explanation),
                keyValue = state.encryptionKey,
                onKeyChange = onEncryptionKeyChange,
                onKeyGuide = onKeyGuide,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SignalQualityCard(
                minimumSignalQuality = state.minimumSignalQuality,
                onSignalQualityChange = onSignalQualityChange,
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ProfileSectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    headerEnd: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (headerEnd != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    headerEnd()
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoCard(
    name: String,
    nameError: String?,
    selectedModel: PodDevice.Model?,
    availableModels: List<PodDevice.Model>,
    selectedDevice: BluetoothDevice2?,
    bondedDevices: List<BluetoothDevice2>,
    onNameChange: (String) -> Unit,
    onModelChange: (PodDevice.Model) -> Unit,
    onDeviceChange: (BluetoothDevice2?) -> Unit,
) {
    ProfileSectionCard(
        icon = Icons.TwoTone.DevicesOther,
        title = stringResource(R.string.profiles_basic_info_title),
        description = stringResource(R.string.profiles_basic_info_description),
    ) {
        // Name input
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.profiles_name_label)) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(text = it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Model dropdown
        var modelExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedModel?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(R.string.profiles_model_label)) },
                leadingIcon = selectedModel?.let {
                    {
                        Icon(
                            painter = painterResource(it.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false },
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(model.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = model.label)
                            }
                        },
                        onClick = {
                            onModelChange(model)
                            modelExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Paired device dropdown
        var deviceExpanded by remember { mutableStateOf(false) }
        val noneLabel = stringResource(R.string.profiles_paired_device_none)
        val unknownLabel = stringResource(R.string.pods_unknown_label)
        val deviceDisplayText = if (selectedDevice != null) {
            "${selectedDevice.name ?: unknownLabel} (${selectedDevice.address})"
        } else {
            noneLabel
        }

        ExposedDropdownMenuBox(
            expanded = deviceExpanded,
            onExpandedChange = { deviceExpanded = it },
        ) {
            OutlinedTextField(
                value = deviceDisplayText,
                onValueChange = {},
                readOnly = true,
                label = { Text(text = stringResource(R.string.profiles_paired_device_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = deviceExpanded,
                onDismissRequest = { deviceExpanded = false },
            ) {
                // "None" option
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(text = noneLabel)
                            Text(
                                text = stringResource(R.string.profiles_paired_device_none_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onDeviceChange(null)
                        deviceExpanded = false
                    },
                )
                bondedDevices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text = device.name ?: unknownLabel)
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onDeviceChange(device)
                            deviceExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCard(
    title: String,
    description: String,
    keyValue: ByteArray?,
    onKeyChange: (ByteArray?) -> Unit,
    onKeyGuide: () -> Unit,
) {
    val keyRegex = remember { Regex("^([0-9A-Fa-f]{2}[- ]){15}[0-9A-Fa-f]{2}$") }
    val exampleKey = "FE-D0-1C-54-11-81-BC-BC-87-D2-C4-3F-31-64-5F-EE"

    var textFieldValue by rememberSaveable { mutableStateOf(keyValue?.toHex() ?: "") }
    var keyError by rememberSaveable { mutableStateOf<String?>(null) }

    // Sync from external state changes (e.g. profile load)
    LaunchedEffect(keyValue) {
        val externalHex = keyValue?.toHex() ?: ""
        if (textFieldValue.replace(" ", "").replace("-", "") !=
            externalHex.replace(" ", "").replace("-", "")
        ) {
            textFieldValue = externalHex
            keyError = null
        }
    }

    val invalidFormatLabel = stringResource(R.string.profiles_key_invalid_format)
    val expectedFormatLabel = stringResource(R.string.profiles_key_expected_format, exampleKey)

    // Derive chip state from local validation, not from external keyValue
    val isConfigured = textFieldValue.isNotEmpty() && keyError == null

    ProfileSectionCard(
        icon = Icons.TwoTone.Key,
        title = title,
        description = description,
        headerEnd = {
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = stringResource(
                            if (isConfigured) R.string.profiles_key_status_configured
                            else R.string.profiles_key_status_not_set
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                icon = if (isConfigured) {
                    {
                        Icon(
                            imageVector = Icons.TwoTone.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else null,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    disabledContainerColor = if (isConfigured) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    disabledLabelColor = if (isConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledIconContentColor = if (isConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
        },
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { text ->
                textFieldValue = text
                when {
                    text.isEmpty() -> {
                        keyError = null
                        onKeyChange(null)
                    }

                    text.matches(keyRegex) -> {
                        keyError = null
                        try {
                            val normalized = text
                                .replace(" ", "")
                                .replace("-", "")
                                .chunked(2)
                                .map { it.toInt(16).toByte() }
                                .toByteArray()
                            onKeyChange(normalized)
                        } catch (_: Exception) {
                            keyError = invalidFormatLabel
                        }
                    }

                    else -> {
                        keyError = expectedFormatLabel
                        onKeyChange(null)
                    }
                }
            },
            label = {
                Text(
                    text = stringResource(R.string.general_example_label, exampleKey),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            isError = keyError != null,
            supportingText = keyError?.let { { Text(text = it) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onKeyGuide,
            ) {
                Text(
                    text = stringResource(R.string.general_guide_action),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SignalQualityCard(
    minimumSignalQuality: Float,
    onSignalQualityChange: (Float) -> Unit,
) {
    val clamped = minimumSignalQuality.coerceIn(0f, 1f)
    val percentage = (clamped * 100).toInt()

    ProfileSectionCard(
        icon = Icons.TwoTone.SettingsInputAntenna,
        title = stringResource(R.string.profiles_signal_quality_title),
        description = stringResource(R.string.profiles_signal_quality_description),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Slider(
                value = clamped * 100f,
                onValueChange = { onSignalQualityChange(it / 100f) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.profiles_signal_quality_min_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.profiles_signal_quality_max_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(text = stringResource(R.string.general_unsaved_changes_title)) },
        text = { Text(text = stringResource(R.string.general_unsaved_changes_message)) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(text = stringResource(R.string.general_save_and_exit_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(text = stringResource(R.string.general_discard_action))
            }
        },
    )
}

@Preview2
@Composable
private fun DeviceProfileCreationScreenNewPreview() = PreviewWrapper {
    DeviceProfileCreationScreen(
        state = DeviceProfileCreationViewModel.State(
            isEditMode = false,
            name = "",
            nameError = null,
            selectedModel = null,
            availableModels = PodDevice.Model.entries.filter { it != PodDevice.Model.UNKNOWN },
            identityKey = null,
            encryptionKey = null,
            selectedDevice = null,
            bondedDevices = emptyList(),
            minimumSignalQuality = 0.15f,
            canSave = false,
        ),
        onBack = {},
        onSave = {},
        onDelete = {},
        onNameChange = {},
        onModelChange = {},
        onDeviceChange = {},
        onIdentityKeyChange = {},
        onEncryptionKeyChange = {},
        onSignalQualityChange = {},
        onKeyGuide = {},
    )
}

@Preview2
@Composable
private fun DeviceProfileCreationScreenEditPreview() = PreviewWrapper {
    DeviceProfileCreationScreen(
        state = DeviceProfileCreationViewModel.State(
            isEditMode = true,
            name = "My AirPods Pro",
            nameError = null,
            selectedModel = PodDevice.Model.AIRPODS_PRO2,
            availableModels = PodDevice.Model.entries.filter { it != PodDevice.Model.UNKNOWN },
            identityKey = null,
            encryptionKey = null,
            selectedDevice = null,
            bondedDevices = emptyList(),
            minimumSignalQuality = 0.25f,
            canSave = true,
        ),
        onBack = {},
        onSave = {},
        onDelete = {},
        onNameChange = {},
        onModelChange = {},
        onDeviceChange = {},
        onIdentityKeyChange = {},
        onEncryptionKeyChange = {},
        onSignalQualityChange = {},
        onKeyGuide = {},
    )
}

@Preview2
@Composable
private fun DeviceProfileCreationScreenWithKeysPreview() = PreviewWrapper {
    DeviceProfileCreationScreen(
        state = DeviceProfileCreationViewModel.State(
            isEditMode = true,
            name = "My AirPods Pro",
            nameError = null,
            selectedModel = PodDevice.Model.AIRPODS_PRO2,
            availableModels = PodDevice.Model.entries.filter { it != PodDevice.Model.UNKNOWN },
            identityKey = byteArrayOf(
                0xFE.toByte(), 0xD0.toByte(), 0x1C.toByte(), 0x54.toByte(),
                0x11.toByte(), 0x81.toByte(), 0xBC.toByte(), 0xBC.toByte(),
                0x87.toByte(), 0xD2.toByte(), 0xC4.toByte(), 0x3F.toByte(),
                0x31.toByte(), 0x64.toByte(), 0x5F.toByte(), 0xEE.toByte(),
            ),
            encryptionKey = byteArrayOf(
                0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01.toByte(),
                0x23.toByte(), 0x45.toByte(), 0x67.toByte(), 0x89.toByte(),
                0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01.toByte(),
                0x23.toByte(), 0x45.toByte(), 0x67.toByte(), 0x89.toByte(),
            ),
            selectedDevice = null,
            bondedDevices = emptyList(),
            minimumSignalQuality = 0.35f,
            canSave = true,
        ),
        onBack = {},
        onSave = {},
        onDelete = {},
        onNameChange = {},
        onModelChange = {},
        onDeviceChange = {},
        onIdentityKeyChange = {},
        onEncryptionKeyChange = {},
        onSignalQualityChange = {},
        onKeyGuide = {},
    )
}

@Composable
private fun DeleteConfirmationDialog(
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.profiles_delete_title)) },
        text = { Text(text = stringResource(R.string.profiles_delete_message)) },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(text = stringResource(R.string.profiles_delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.general_cancel_action))
            }
        },
    )
}
