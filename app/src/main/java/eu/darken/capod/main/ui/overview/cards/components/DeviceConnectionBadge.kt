package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.monitor.core.BleKeyState
import eu.darken.capod.monitor.core.ConnectionState

@Composable
fun DeviceConnectionBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    if (!state.hasBleData && state.bleKeyState == BleKeyState.NONE && !state.isAapConnected) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (state.hasBleData) {
                Icon(
                    imageVector = Icons.TwoTone.SettingsInputAntenna,
                    contentDescription = stringResource(R.string.signal_badge_ble_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.bleKeyState != BleKeyState.NONE) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = stringResource(R.string.signal_badge_key_irk_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.bleKeyState == BleKeyState.IRK_AND_ENCRYPTED) {
                Icon(
                    imageVector = Icons.TwoTone.Lock,
                    contentDescription = stringResource(R.string.signal_badge_key_encrypted_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isAapConnected) {
                Icon(
                    imageVector = Icons.TwoTone.Bluetooth,
                    contentDescription = stringResource(R.string.signal_badge_aap_cd),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun DeviceConnectionBadgeAllIconsPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.IRK_AND_ENCRYPTED,
            isAapConnected = true,
            rssiQuality = 1f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeBleOnlyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = false,
            rssiQuality = 0.7f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeBleIrkPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = true,
            bleKeyState = BleKeyState.IRK_ONLY,
            isAapConnected = false,
            rssiQuality = 0.5f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeAapOnlyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = false,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = true,
            rssiQuality = 0f,
        ),
    )
}

@Preview2
@Composable
private fun DeviceConnectionBadgeEmptyPreview() = PreviewWrapper {
    DeviceConnectionBadge(
        state = ConnectionState(
            hasBleData = false,
            bleKeyState = BleKeyState.NONE,
            isAapConnected = false,
            rssiQuality = 0f,
        ),
    )
}
