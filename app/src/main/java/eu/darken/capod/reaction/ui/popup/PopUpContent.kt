package eu.darken.capod.reaction.ui.popup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SignalCellularAlt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.pods.core.getSignalQuality

@Composable
fun PopUpContent(
    device: PodDevice,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: label + signal quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = device.getLabel(context),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                val signalText = device.getSignalQuality(context)
                if (signalText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.TwoTone.SignalCellularAlt,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = signalText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device-specific content
            when (device) {
                is DualPodDevice -> DualPodContent(device)
                is SinglePodDevice -> SinglePodContent(device)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Close button
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(text = stringResource(R.string.general_close_action))
            }
        }
    }
}

@Composable
private fun DualPodContent(device: DualPodDevice) {
    val hasCase = device as? HasCase

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Left pod
        BatteryColumn(
            iconRes = device.leftPodIcon,
            batteryPercent = device.batteryLeftPodPercent,
            modifier = Modifier.weight(1f),
        )

        // Case (only if device has one)
        if (hasCase != null) {
            BatteryColumn(
                iconRes = hasCase.caseIcon,
                batteryPercent = hasCase.batteryCasePercent,
                modifier = Modifier.weight(1f),
            )
        }

        // Right pod
        BatteryColumn(
            iconRes = device.rightPodIcon,
            batteryPercent = device.batteryRightPodPercent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SinglePodContent(device: SinglePodDevice) {
    BatteryColumn(
        iconRes = device.iconRes,
        batteryPercent = device.batteryHeadsetPercent,
    )
}

@Composable
private fun BatteryColumn(
    iconRes: Int,
    batteryPercent: Float?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(getBatteryDrawable(batteryPercent)),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = formatBatteryPercent(context, batteryPercent),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
