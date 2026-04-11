package eu.darken.capod.common.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun UpgradeBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.TwoTone.Stars,
        contentDescription = stringResource(R.string.common_upgrade_required_label),
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(16.dp),
    )
}

@Preview2
@Composable
private fun UpgradeBadgePreview() = PreviewWrapper {
    UpgradeBadge()
}
