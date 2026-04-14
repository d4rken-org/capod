package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Edit
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
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.devicesettings.dialogs.RenameDialog
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo

@Composable
internal fun DeviceInfoCard(
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
        ),
        connectionStateLabel = "Connected",
        lastSeen = "Just now",
        firstSeen = "5 minutes ago",
        canRename = true,
    )
}

@Composable
@Preview2
private fun DeviceInfoCardWithoutRenamePreview() = PreviewWrapper {
    DeviceInfoCard(
        deviceInfo = AapDeviceInfo(
            name = "AirPods Pro",
            modelNumber = "A2699",
            manufacturer = "Apple Inc.",
            serialNumber = "W5J7KV0N04",
            firmwareVersion = "7A305",
        ),
        connectionStateLabel = "Disconnected",
        lastSeen = "2 hours ago",
        firstSeen = null,
        canRename = false,
    )
}

@Composable
@Preview2
private fun DeviceInfoCardSparsePreview() = PreviewWrapper {
    DeviceInfoCard(
        deviceInfo = null,
        connectionStateLabel = "Disconnected",
        lastSeen = "Just now",
        firstSeen = null,
    )
}
