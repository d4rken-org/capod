package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.material.icons.twotone.KeyboardVoice
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.AapPodState

@Composable
fun StatusChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChipRow(
    chargingState: AapPodState.ChargingState?,
    isInEar: Boolean,
    showEarDetection: Boolean,
    isMicrophone: Boolean,
    showMicrophone: Boolean,
    chargingLabel: String,
    chargingOptimizedLabel: String,
    inEarLabel: String,
    microphoneLabel: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (chargingState) {
            AapPodState.ChargingState.CHARGING_OPTIMIZED -> StatusChip(
                icon = Icons.TwoTone.BatteryChargingFull,
                label = chargingOptimizedLabel,
            )
            AapPodState.ChargingState.CHARGING -> StatusChip(
                icon = Icons.TwoTone.BatteryChargingFull,
                label = chargingLabel,
            )
            else -> Unit
        }
        if (showMicrophone && isMicrophone) {
            StatusChip(
                icon = Icons.TwoTone.KeyboardVoice,
                label = microphoneLabel,
            )
        }
        if (showEarDetection && isInEar) {
            StatusChip(
                icon = Icons.TwoTone.Hearing,
                label = inEarLabel,
            )
        }
    }
}

@Preview2
@Composable
private fun StatusChipChargingPreview() = PreviewWrapper {
    StatusChip(icon = Icons.TwoTone.BatteryChargingFull, label = "Charging")
}

@Preview2
@Composable
private fun StatusChipRowAllPreview() = PreviewWrapper {
    StatusChipRow(
        chargingState = AapPodState.ChargingState.CHARGING,
        isInEar = true,
        showEarDetection = true,
        isMicrophone = true,
        showMicrophone = true,
        chargingLabel = "Charging",
        chargingOptimizedLabel = "Optimized",
        inEarLabel = "In Ear",
        microphoneLabel = "Mic",
    )
}

@Preview2
@Composable
private fun StatusChipRowOptimizedPreview() = PreviewWrapper {
    StatusChipRow(
        chargingState = AapPodState.ChargingState.CHARGING_OPTIMIZED,
        isInEar = false,
        showEarDetection = true,
        isMicrophone = true,
        showMicrophone = true,
        chargingLabel = "Charging",
        chargingOptimizedLabel = "Optimized",
        inEarLabel = "In Ear",
        microphoneLabel = "Mic",
    )
}
