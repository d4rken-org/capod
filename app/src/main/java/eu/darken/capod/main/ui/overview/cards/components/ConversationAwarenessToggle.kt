package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

@Composable
fun ConversationAwarenessToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.conversation_awareness_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Preview2
@Composable
private fun ConversationAwarenessToggleEnabledPreview() = PreviewWrapper {
    ConversationAwarenessToggle(enabled = true, onToggle = {})
}

@Preview2
@Composable
private fun ConversationAwarenessToggleDisabledPreview() = PreviewWrapper {
    ConversationAwarenessToggle(enabled = false, onToggle = {})
}
