package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.BluetoothSearching
import androidx.compose.material.icons.twotone.BatterySaver
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.BluetoothConnected
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.ShareLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.permissions.Permission

@Composable
fun PermissionCard(
    permission: Permission,
    onRequest: (Permission) -> Unit,
) {
    val colors = if (permission.isScanBlocking) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = permission.iconVector(),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = stringResource(permission.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(permission.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onRequest(permission) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.general_grant_permission_action))
            }
        }
    }
}

@Preview2
@Composable
private fun PermissionCardPreview() = PreviewWrapper {
    PermissionCard(permission = Permission.BLUETOOTH_SCAN, onRequest = {})
}

@Preview2
@Composable
private fun PermissionCardNormalPreview() = PreviewWrapper {
    PermissionCard(permission = Permission.IGNORE_BATTERY_OPTIMIZATION, onRequest = {})
}

private fun Permission.iconVector(): ImageVector = when (this) {
    Permission.BLUETOOTH -> Icons.TwoTone.Bluetooth
    Permission.BLUETOOTH_CONNECT -> Icons.TwoTone.BluetoothConnected
    Permission.BLUETOOTH_SCAN -> Icons.AutoMirrored.TwoTone.BluetoothSearching
    Permission.ACCESS_FINE_LOCATION -> Icons.TwoTone.MyLocation
    Permission.ACCESS_BACKGROUND_LOCATION -> Icons.TwoTone.ShareLocation
    Permission.IGNORE_BATTERY_OPTIMIZATION -> Icons.TwoTone.BatterySaver
    Permission.SYSTEM_ALERT_WINDOW -> Icons.TwoTone.Layers
    Permission.POST_NOTIFICATIONS -> Icons.TwoTone.Notifications
}
