package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DeviceInfoBottomSheet(
    items: List<DeviceDetailItem>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DeviceInfoDetailsContent(items)
    }
}

@Composable
private fun DeviceInfoDetailsContent(items: List<DeviceDetailItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.device_settings_info_details_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        items.forEach { item ->
            when (item) {
                is DeviceDetailItem.Single -> InfoRow(label = item.label, value = item.value)
                is DeviceDetailItem.Paired -> Row(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(
                        label = item.start.label,
                        value = item.start.value,
                        modifier = Modifier.weight(1f),
                    )
                    InfoRow(
                        label = item.end.label,
                        value = item.end.value,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private val previewDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneOffset.UTC)

@Preview2
@Composable
private fun DeviceInfoBottomSheetPreviewSameDayPairing() = PreviewWrapper {
    val pairedAt = Instant.parse("2023-10-16T12:00:00Z")
    val info = AapDeviceInfo(
        name = "AirPods Pro",
        modelNumber = "A2699",
        manufacturer = "Apple Inc.",
        serialNumber = "W5J7KV0N04",
        firmwareVersion = "81.2675000075000000.6814",
        hardwareVersion = "1.0.0",
        leftEarbudSerial = "H3KL7HR926JY",
        rightEarbudSerial = "H3KL2AYL26K0",
        marketingVersion = "8454768",
        leftEarbudFirstPaired = pairedAt,
        rightEarbudFirstPaired = pairedAt,
    )
    DeviceInfoDetailsContent(
        buildDeviceInfoDetailItems(info, rememberDeviceInfoDetailLabels()) {
            previewDateFormatter.format(it)
        },
    )
}

@Preview2
@Composable
private fun DeviceInfoBottomSheetPreviewMismatchedPairing() = PreviewWrapper {
    val info = AapDeviceInfo(
        name = "AirPods Pro",
        modelNumber = "A2699",
        manufacturer = "Apple Inc.",
        serialNumber = "W5J7KV0N04",
        firmwareVersion = "81.2675000075000000.6814",
        firmwareVersionPending = "82.1000000075000000.7000",
        hardwareVersion = "1.0.0",
        leftEarbudSerial = "H3KL7HR926JY",
        rightEarbudSerial = "H3KL2AYL26K0",
        marketingVersion = "8454768",
        leftEarbudFirstPaired = Instant.parse("2024-02-14T12:00:00Z"),
        rightEarbudFirstPaired = Instant.parse("2023-10-16T12:00:00Z"),
    )
    DeviceInfoDetailsContent(
        buildDeviceInfoDetailItems(info, rememberDeviceInfoDetailLabels()) {
            previewDateFormatter.format(it)
        },
    )
}

@Preview2
@Composable
private fun DeviceInfoBottomSheetPreviewOneSidedPairing() = PreviewWrapper {
    val info = AapDeviceInfo(
        name = "AirPods Pro",
        modelNumber = "A2699",
        manufacturer = "Apple Inc.",
        serialNumber = "W5J7KV0N04",
        firmwareVersion = "7A305",
        leftEarbudSerial = "H3KL7HR926JY",
        leftEarbudFirstPaired = Instant.parse("2023-10-16T12:00:00Z"),
    )
    DeviceInfoDetailsContent(
        buildDeviceInfoDetailItems(info, rememberDeviceInfoDetailLabels()) {
            previewDateFormatter.format(it)
        },
    )
}
