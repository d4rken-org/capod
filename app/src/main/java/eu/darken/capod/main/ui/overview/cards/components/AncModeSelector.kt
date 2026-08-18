package eu.darken.capod.main.ui.overview.cards.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.components.icon
import eu.darken.capod.main.ui.components.shortLabelRes
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlin.math.abs
import kotlin.math.roundToInt

private val TrackShape = RoundedCornerShape(16.dp)
private val ThumbShape = RoundedCornerShape(12.dp)
private val TrackInset = 4.dp
private val TrackHeight = 48.dp
private val CollapsedSlotWidth = 52.dp

/**
 * Listening-mode picker: one thumb travelling across a filled track, naming only the mode it sits
 * on. Inactive modes stay icon-sized, which keeps the active label free of the width budget and
 * therefore readable in every locale.
 */
@Composable
fun AncModeSelector(
    modifier: Modifier = Modifier,
    currentMode: AapSetting.AncMode.Value,
    supportedModes: List<AapSetting.AncMode.Value>,
    onModeSelected: (AapSetting.AncMode.Value) -> Unit,
    pendingMode: AapSetting.AncMode.Value? = null,
    enabled: Boolean = true,
) {
    if (supportedModes.isEmpty()) return

    val currentIndex = supportedModes.indexOf(currentMode)
    // A requested mode can be missing from the list (e.g. one filtered out of the listening-mode
    // cycle), in which case the thumb stays on the mode the pods are actually in.
    val requestedIndex = supportedModes.indexOf(pendingMode ?: currentMode)
    val targetIndex = if (requestedIndex >= 0) requestedIndex else currentIndex
    val isPending = pendingMode != null && pendingMode != currentMode

    // With neither mode on the list there is nothing to highlight: park the thumb where it was and
    // fade it out rather than falsely marking the first slot as active.
    val lastKnownIndex = remember { mutableIntStateOf(targetIndex.coerceAtLeast(0)) }
    val thumbIndex = if (targetIndex >= 0) targetIndex else lastKnownIndex.intValue
    SideEffect { if (targetIndex >= 0) lastKnownIndex.intValue = targetIndex }

    val animatedIndex by animateFloatAsState(
        targetValue = thumbIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "ancThumbPosition",
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (targetIndex >= 0) 1f else 0f,
        animationSpec = tween(200),
        label = "ancThumbAlpha",
    )
    // While a mode change is in flight the thumb stays hollow and breathes; it only fills in solid
    // once the pods confirm the new mode.
    val pendingFill = if (isPending) {
        val pulse by rememberInfiniteTransition(label = "ancPending").animateFloat(
            initialValue = 0.10f,
            targetValue = 0.30f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ancPendingPulse",
        )
        pulse
    } else {
        0f
    }

    val accent = MaterialTheme.colorScheme.primary
    val activeContent = if (isPending) accent else MaterialTheme.colorScheme.onPrimary
    val idleContent = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
        shape = TrackShape,
        // Same tonal step as the battery panel above it, so the two read as siblings and the thumb
        // is the only thing in the card competing with the gauges for attention.
        tonalElevation = 4.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(TrackInset)
                .height(TrackHeight),
        ) {
            val slots = supportedModes.size
            val equalWidth = maxWidth / slots.toFloat()
            val collapsedWidth = minOf(CollapsedSlotWidth, equalWidth)
            val expandedWidth = maxWidth - collapsedWidth * (slots - 1)

            // The spring is underdamped and overshoots past the end slots; clamp before deriving any
            // geometry, otherwise the reveals stop summing to 1 and the slots no longer fill the row.
            val position = animatedIndex.coerceIn(0f, (slots - 1).toFloat())
            // Distance to the (fractional) thumb position drives slot width, label reveal and tint
            // alike, so geometry and content stay in lockstep for the whole travel.
            val reveals = supportedModes.indices.map { (1f - abs(it - position)).coerceIn(0f, 1f) }
            val widths = reveals.map { collapsedWidth + (expandedWidth - collapsedWidth) * it }
            // Everything left of the thumb is collapsed by definition, so its leading edge is just
            // the fractional position in collapsed-slot units. Summing the animating widths instead
            // would overshoot the target and crawl back.
            val thumbOffset = collapsedWidth * position

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(expandedWidth)
                    .fillMaxHeight()
                    .alpha(thumbAlpha)
                    .clip(ThumbShape)
                    .background(if (isPending) accent.copy(alpha = pendingFill) else accent)
                    .then(if (isPending) Modifier.border(1.5.dp, accent, ThumbShape) else Modifier),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .selectableGroup(),
            ) {
                supportedModes.forEachIndexed { index, mode ->
                    AncModeSlot(
                        mode = mode,
                        label = stringResource(mode.shortLabelRes()),
                        reveal = reveals[index],
                        width = widths[index],
                        contentColor = when {
                            // Mid-flight the still-active mode keeps an accent tint, so it stays
                            // readable what the pods are doing while the request is unconfirmed.
                            isPending && index == currentIndex -> accent
                            else -> lerp(idleContent, activeContent, reveals[index])
                        },
                        selected = index == targetIndex,
                        enabled = enabled,
                        onClick = { onModeSelected(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AncModeSlot(
    mode: AapSetting.AncMode.Value,
    label: String,
    reveal: Float,
    width: Dp,
    contentColor: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(ThumbShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // Named on the slot itself rather than on whichever child happens to be visible: the
            // label only exists once it has revealed, so deriving the name from the children would
            // leave the freshly selected mode unnamed for the length of the animation.
            .semantics { contentDescription = label }
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Icon(
                imageVector = mode.icon(),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(6.dp * reveal))
            RevealingLabel(text = label, reveal = reveal, color = contentColor)
        }
    }
}

/**
 * Label that wipes in from the left as [reveal] grows: it is measured at its natural width but
 * reports only a fraction of it, so the row around it grows continuously instead of jumping the
 * moment the text appears.
 */
@Composable
private fun RevealingLabel(
    text: String,
    reveal: Float,
    color: Color,
) {
    if (reveal <= 0f) return
    Box(modifier = Modifier.clipToBounds()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            // The slot already carries the accessible name; the text is decoration on top of it.
            modifier = Modifier
                .clearAndSetSemantics { }
                .alpha(reveal)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0))
                    layout((placeable.width * reveal).roundToInt(), placeable.height) {
                        // Relative, so RTL anchors the text at its start edge and wipes in from the
                        // right instead of exposing the tail of the word.
                        placeable.placeRelative(0, 0)
                    }
                },
        )
    }
}

private val PreviewModes = listOf(
    AapSetting.AncMode.Value.OFF,
    AapSetting.AncMode.Value.ON,
    AapSetting.AncMode.Value.TRANSPARENCY,
    AapSetting.AncMode.Value.ADAPTIVE,
)

@Preview2
@Composable
private fun AncModeSelectorPreview() = PreviewWrapper {
    AncModeSelector(
        currentMode = AapSetting.AncMode.Value.ON,
        supportedModes = PreviewModes,
        onModeSelected = {},
    )
}

@Preview2
@Composable
private fun AncModeSelectorPendingPreview() = PreviewWrapper {
    AncModeSelector(
        currentMode = AapSetting.AncMode.Value.ON,
        supportedModes = PreviewModes.dropLast(1),
        onModeSelected = {},
        pendingMode = AapSetting.AncMode.Value.TRANSPARENCY,
    )
}

@Preview2
@Composable
private fun AncModeSelectorDisabledPreview() = PreviewWrapper {
    AncModeSelector(
        currentMode = AapSetting.AncMode.Value.ADAPTIVE,
        supportedModes = PreviewModes,
        onModeSelected = {},
        enabled = false,
    )
}
