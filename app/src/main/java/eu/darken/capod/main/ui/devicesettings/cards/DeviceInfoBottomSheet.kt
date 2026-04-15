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

@Composable
internal fun DeviceInfoBottomSheet(
    items: List<DeviceDetailItem>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
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
}

@Preview2
@Composable
private fun DeviceInfoBottomSheetPreview() = PreviewWrapper {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Device Details",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        InfoRow(label = "Manufacturer", value = "Apple Inc.")
        InfoRow(label = "Serial Number", value = "W5J7KV0N04")
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoRow(label = "Firmware", value = "7A305", modifier = Modifier.weight(1f))
            InfoRow(label = "Build", value = "8454624", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoRow(label = "Left Pod Serial", value = "H3KL7HR926JY", modifier = Modifier.weight(1f))
            InfoRow(label = "Right Pod Serial", value = "H3KL2AYL26K0", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}
