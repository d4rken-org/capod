package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import eu.darken.capod.common.permissions.Permission

@Composable
fun PermissionCard(
    permission: Permission,
    onRequest: (Permission) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(permission.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )

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
