package eu.darken.capod.main.ui.overview.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper

/**
 * Asks the user to leave a review. [onReview] is null when no hosting Activity is available, Play's
 * in-app review flow can't be launched without one, so the action is shown disabled instead.
 */
@Composable
fun ReviewCard(
    onReview: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.review_app_review_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.review_app_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.review_app_dismiss_action))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onReview?.invoke() },
                    enabled = onReview != null,
                ) {
                    Text(text = stringResource(R.string.review_app_review_action))
                }
            }
        }
    }
}

@Preview2
@Composable
private fun ReviewCardPreview() = PreviewWrapper {
    ReviewCard(
        onReview = {},
        onDismiss = {},
    )
}
