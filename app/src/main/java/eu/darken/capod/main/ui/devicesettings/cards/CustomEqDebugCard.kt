package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.settings.SettingsSection
import eu.darken.capod.common.settings.SettingsSliderItem
import eu.darken.capod.main.ui.devicesettings.components.SegmentedSettingRow
import eu.darken.capod.main.ui.devicesettings.previewFullState
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

/**
 * Debug-only evaluation control for Custom EQ (opcode 0x63), whose wire format has never been
 * confirmed on hardware. It is a measurement instrument, not a shipped feature: the sliders and
 * the mode toggle edit local draft state only, and exactly one packet leaves the device per
 * Apply tap, so the logcat observation window stays readable.
 *
 * Every label below is hardcoded English on purpose. This card is throwaway instrumentation that
 * only renders under [eu.darken.capod.BuildConfig.DEBUG]; routing its labels through the base
 * locale would push a dozen strings to Crowdin and have translators work through them for every
 * locale, for text no release build can ever show.
 */
@Composable
internal fun CustomEqDebugCard(
    device: PodDevice,
    enabled: Boolean,
    onApply: (AapSetting.CustomEq.Mode, Int, Int, Int) -> Unit = { _, _, _, _ -> },
) {
    val reported = device.customEq

    // AapOutboundController ear-gates every command except SetDeviceName and SetDynamicEndOfCharge:
    // with no pod in ear the write is queued and only flushed on the next in-ear event, so the
    // packet would surface minutes later at an unrelated moment and poison the logcat window this
    // card exists to produce.
    //
    // The controller reads the AAP EarDetection setting alone and does not gate while that setting
    // is absent, so this mirror has to gate on the same source: hasAapEarDetection makes
    // isEitherPodInEar return the AAP value without ever falling back to the BLE ear bits, which
    // phantom-report "in ear" for pods resting in the case.
    val wouldQueue = device.hasAapEarDetection && device.isEitherPodInEar != true

    var draftMode by remember(reported) {
        mutableStateOf(reported?.mode ?: AapSetting.CustomEq.Mode.RECOMMENDED)
    }
    var draftLow by remember(reported) { mutableIntStateOf(reported?.low ?: NEUTRAL_BAND) }
    var draftMid by remember(reported) { mutableIntStateOf(reported?.mid ?: NEUTRAL_BAND) }
    var draftHigh by remember(reported) { mutableIntStateOf(reported?.high ?: NEUTRAL_BAND) }

    SettingsSection(title = "Custom EQ") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Reported by device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (reported != null) {
                    bandsText(reported.mode, reported.low, reported.mid, reported.high)
                } else {
                    "Not reported"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Draft to send",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = bandsText(draftMode, draftLow, draftMid, draftHigh),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SegmentedSettingRow(
            icon = Icons.TwoTone.GraphicEq,
            title = "Mode",
            options = AapSetting.CustomEq.Mode.entries.map { it.label to it },
            selected = draftMode,
            onSelected = { draftMode = it },
            enabled = enabled,
        )

        BandSlider(
            title = "Low",
            value = draftLow,
            onValueChange = { draftLow = it },
            enabled = enabled,
        )
        BandSlider(
            title = "Mid",
            value = draftMid,
            onValueChange = { draftMid = it },
            enabled = enabled,
        )
        BandSlider(
            title = "High",
            value = draftHigh,
            onValueChange = { draftHigh = it },
            enabled = enabled,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (wouldQueue) {
                Text(
                    text = NOT_IN_EAR_NOTICE,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
            }
            Button(
                onClick = { onApply(draftMode, draftLow, draftMid, draftHigh) },
                enabled = enabled && !wouldQueue,
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun BandSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean,
) {
    SettingsSliderItem(
        icon = Icons.TwoTone.GraphicEq,
        title = title,
        value = value.toFloat(),
        // Draft only — dispatching from here (or from onValueChangeFinished) would flood the link
        // with intermediate tuples. The Apply button is the sole sender.
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..100f,
        steps = 99,
        enabled = enabled,
        valueLabel = { it.toInt().toString() },
    )
}

private const val NEUTRAL_BAND = 50

internal const val NOT_IN_EAR_NOTICE =
    "No pod in ear, a write would be queued instead of sent. Wear a pod before applying."

private fun bandsText(mode: AapSetting.CustomEq.Mode, low: Int, mid: Int, high: Int): String =
    "${mode.label} · low $low / mid $mid / high $high"

private val AapSetting.CustomEq.Mode.label: String
    get() = when (this) {
        AapSetting.CustomEq.Mode.RECOMMENDED -> "Recommended"
        AapSetting.CustomEq.Mode.CUSTOM -> "Custom"
    }

@Preview2
@Composable
private fun CustomEqDebugCardNotReportedPreview() = PreviewWrapper {
    CustomEqDebugCard(
        device = previewFullState(isPro = true).device!!,
        enabled = true,
    )
}

@Preview2
@Composable
private fun CustomEqDebugCardReportedPreview() = PreviewWrapper {
    val device = previewFullState(isPro = true).device!!
    CustomEqDebugCard(
        device = device.copy(
            aap = device.aap!!.withSetting(
                AapSetting.CustomEq::class,
                AapSetting.CustomEq(
                    mode = AapSetting.CustomEq.Mode.CUSTOM,
                    low = 60,
                    mid = 50,
                    high = 35,
                ),
            ),
        ),
        enabled = true,
    )
}
