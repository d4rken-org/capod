package eu.darken.capod.main.ui.devicesettings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@Composable
fun ConnectedDevicesList(
    devices: List<AapSetting.ConnectedDevices.ConnectedDevice>,
    audioSource: AapSetting.AudioSource?,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.device_settings_connected_devices_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        for ((index, device) in devices.withIndex()) {
            val isSource = audioSource?.sourceMac == device.mac
            val statusLabel = if (isSource) {
                when (audioSource?.type) {
                    AapSetting.AudioSource.AudioSourceType.CALL -> stringResource(R.string.device_settings_connected_device_call)
                    AapSetting.AudioSource.AudioSourceType.MEDIA -> stringResource(R.string.device_settings_connected_device_media)
                    else -> ""
                }
            } else ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.DevicesOther,
                    contentDescription = null,
                    tint = if (isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.device_settings_connected_device_label, index + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (statusLabel.isNotEmpty()) statusLabel else device.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun ConnectedDevicesListPreview() = PreviewWrapper {
    ConnectedDevicesList(
        devices = listOf(
            AapSetting.ConnectedDevices.ConnectedDevice(mac = "AA:BB:CC:DD:EE:01", type = 2),
            AapSetting.ConnectedDevices.ConnectedDevice(mac = "AA:BB:CC:DD:EE:02", type = 2),
        ),
        audioSource = AapSetting.AudioSource(
            sourceMac = "AA:BB:CC:DD:EE:01",
            type = AapSetting.AudioSource.AudioSourceType.MEDIA,
        ),
    )
}
