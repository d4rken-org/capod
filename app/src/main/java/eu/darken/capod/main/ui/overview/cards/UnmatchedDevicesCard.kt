package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeviceUnknown
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun UnmatchedDevicesCard(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.DeviceUnknown,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = stringResource(R.string.overview_unmatched_devices_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = pluralStringResource(R.plurals.overview_unmatched_devices_description, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onToggle,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.TwoTone.VisibilityOff else Icons.TwoTone.Visibility,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = if (isExpanded) {
                        stringResource(R.string.general_hide_action)
                    } else {
                        stringResource(R.string.general_show_action)
                    }
                )
            }
        }
    }
}

@Preview2
@Composable
private fun UnmatchedDevicesCardCollapsedPreview() = PreviewWrapper {
    UnmatchedDevicesCard(count = 1, isExpanded = false, onToggle = {})
}

@Preview2
@Composable
private fun UnmatchedDevicesCardExpandedPreview() = PreviewWrapper {
    UnmatchedDevicesCard(count = 3, isExpanded = true, onToggle = {})
}
