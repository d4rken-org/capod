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

/**
 * Asks the user to leave a review. [onReview] is null when no hosting Activity is available, Play's
 * in-app review flow can't be launched without one, so the action is shown disabled instead.
 */
@Composable
fun ReviewCard(
    onReview: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // The card only disappears with the next state emission, so the tap targets need a latch. It is
    // asymmetric on purpose: the harmful orderings are a dismiss after a review (which overwrites
    // the review bookkeeping with a snooze) and a review after a dismiss. A repeated review tap is
    // harmless, the tool's single-flight lock absorbs it, and blocking it here would leave a dead
    // card whenever a Play request fails and nothing gets persisted.
    //
    // Plain remember, not rememberSaveable: the card is a keyed item in a lazy list, which hands
    // saveable state back when the item returns, so a card that was removed by a higher priority
    // card would come back still latched. Disposal is the intended reset, the latch only has to
    // survive the sub-second window until the next state emission takes the card away.
    var dismissLocked by remember { mutableStateOf(false) }
    var fullyLatched by remember { mutableStateOf(false) }
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
                TextButton(
                    onClick = {
                        if (!fullyLatched && !dismissLocked) {
                            dismissLocked = true
                            fullyLatched = true
                            onDismiss()
                        }
                    },
                    enabled = !fullyLatched && !dismissLocked,
                ) {
                    Text(text = stringResource(R.string.review_app_dismiss_action))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (!fullyLatched && onReview != null) {
                            dismissLocked = true
                            onReview.invoke()
                        }
                    },
                    enabled = onReview != null && !fullyLatched,
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
