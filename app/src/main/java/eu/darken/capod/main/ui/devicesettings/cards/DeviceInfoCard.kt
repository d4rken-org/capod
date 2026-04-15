package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Info
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.devicesettings.dialogs.RenameDialog
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo

internal fun buildModelLabel(device: PodDevice): String? {
    if (device.model == PodModel.UNKNOWN) return null
    val modelNumber = device.deviceInfo?.modelNumber?.takeIf { it.isNotBlank() }
    return if (modelNumber != null) "${device.model.label} ($modelNumber)" else device.model.label
}

@Composable
internal fun DeviceInfoCard(
    deviceInfo: AapDeviceInfo?,
    modelLabel: String?,
    systemBluetoothName: String?,
    connectionStateLabel: String?,
    lastSeen: String?,
    firstSeen: String?,
    detailItems: List<DeviceDetailItem> = emptyList(),
    canRename: Boolean = false,
    onRename: (String) -> Unit = {},
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(detailItems) {
        if (detailItems.isEmpty()) showBottomSheet = false
    }

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

    if (showBottomSheet && detailItems.isNotEmpty()) {
        DeviceInfoBottomSheet(
            items = detailItems,
            onDismiss = { showBottomSheet = false },
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (modelLabel != null || detailItems.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (modelLabel != null) {
                        InfoRow(
                            label = stringResource(R.string.device_settings_info_model_label),
                            value = modelLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (detailItems.isNotEmpty()) {
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.TwoTone.Info,
                                contentDescription = stringResource(R.string.device_settings_info_details_action),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (deviceInfo != null && deviceInfo.name.isNotBlank()) {
                val nameMismatch = systemBluetoothName != null && systemBluetoothName != deviceInfo.name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    InfoRow(
                        label = stringResource(R.string.device_settings_info_bt_name_label),
                        value = deviceInfo.name,
                        modifier = Modifier.weight(1f),
                        valueFontFamily = if (nameMismatch) FontFamily.Cursive else null,
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
            if (connectionStateLabel != null) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_status_label),
                    value = connectionStateLabel,
                )
            }
            if (lastSeen != null && firstSeen != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(
                        label = stringResource(R.string.device_settings_info_last_seen_label),
                        value = lastSeen,
                        modifier = Modifier.weight(1f),
                    )
                    InfoRow(
                        label = stringResource(R.string.device_settings_info_first_seen_label),
                        value = firstSeen,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
            } else if (lastSeen != null) {
                InfoRow(
                    label = stringResource(R.string.device_settings_info_last_seen_label),
                    value = lastSeen,
                )
            }
        }
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    valueFontFamily: FontFamily? = null,
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = textAlign,
            fontFamily = valueFontFamily,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview2
@Composable
private fun DeviceInfoCardFullPreview() = PreviewWrapper {
    DeviceInfoCard(
        deviceInfo = AapDeviceInfo(
            name = "AirPods Pro",
            modelNumber = "A2699",
            manufacturer = "Apple Inc.",
            serialNumber = "W5J7KV0N04",
            firmwareVersion = "7A305",
            leftEarbudSerial = "H3KL7HR926JY",
            rightEarbudSerial = "H3KL2AYL26K0",
            buildNumber = "8454624",
        ),
        modelLabel = "AirPods Pro 2 (A2699)",
        systemBluetoothName = "AirPods Pro",
        connectionStateLabel = "Connected",
        lastSeen = "Just now",
        firstSeen = "5 minutes ago",
        detailItems = listOf(
            DeviceDetailItem.Single("Serial Number", "W5J7KV0N04"),
            DeviceDetailItem.Paired(
                start = DeviceDetailItem.Single("Firmware", "7A305"),
                end = DeviceDetailItem.Single("Build", "8454624"),
            ),
        ),
        canRename = true,
    )
}

@Composable
@Preview2
private fun DeviceInfoCardMismatchPreview() = PreviewWrapper {
    DeviceInfoCard(
        deviceInfo = AapDeviceInfo(
            name = "My AirPods Pro",
            modelNumber = "A2699",
            manufacturer = "Apple Inc.",
            serialNumber = "W5J7KV0N04",
            firmwareVersion = "7A305",
        ),
        modelLabel = "AirPods Pro 2 (A2699)",
        systemBluetoothName = "AirPods Pro",
        connectionStateLabel = "Connected",
        lastSeen = "Just now",
        firstSeen = "5 minutes ago",
        detailItems = listOf(
            DeviceDetailItem.Single("Serial Number", "W5J7KV0N04"),
            DeviceDetailItem.Paired(
                start = DeviceDetailItem.Single("Firmware", "7A305"),
                end = DeviceDetailItem.Single("Build", "8454624"),
            ),
        ),
        canRename = true,
    )
}

@Composable
@Preview2
private fun DeviceInfoCardSparsePreview() = PreviewWrapper {
    DeviceInfoCard(
        deviceInfo = null,
        modelLabel = "AirPods Pro 2",
        systemBluetoothName = null,
        connectionStateLabel = "Disconnected",
        lastSeen = "Just now",
        firstSeen = null,
    )
}
