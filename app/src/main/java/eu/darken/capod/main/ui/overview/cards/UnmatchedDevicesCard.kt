package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.overview_unmatched_devices_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onToggle) {
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
