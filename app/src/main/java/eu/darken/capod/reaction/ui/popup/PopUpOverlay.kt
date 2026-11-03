package eu.darken.capod.reaction.ui.popup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Single owner of the card's vertical offset and alpha.
 *
 * The offset is kept as a fraction of [hiddenOffsetPx] (0 = rest, 1 = hidden), so a layout change
 * while hidden or animating moves the hidden position along with the measured size.
 */
@Stable
class PopUpOverlayState(atRest: Boolean = false) {

    private val hiddenFraction = Animatable(if (atRest) 0f else 1f)
    private val alphaAnimatable = Animatable(if (atRest) 1f else 0f)

    /** Measured host height including its bottom padding and insets. */
    var hiddenOffsetPx by mutableFloatStateOf(0f)
        internal set

    val isMeasured: Boolean get() = hiddenOffsetPx > 0f

    val offsetPx: Float get() = hiddenFraction.value * hiddenOffsetPx

    val alpha: Float get() = alphaAnimatable.value

    internal suspend fun animateToRest() = animateTo(
        fraction = 0f,
        alpha = 1f,
        durationMs = ENTER_DURATION_MS,
        easing = LinearOutSlowInEasing,
    )

    internal suspend fun animateToHidden() = animateTo(
        fraction = 1f,
        alpha = 0f,
        durationMs = EXIT_DURATION_MS,
        easing = FastOutLinearInEasing,
    )

    private suspend fun animateTo(fraction: Float, alpha: Float, durationMs: Int, easing: Easing) {
        coroutineScope {
            launch { hiddenFraction.animateTo(fraction, tween(durationMs, easing = easing)) }
            launch { alphaAnimatable.animateTo(alpha, tween(durationMs, easing = easing)) }
        }
    }

    companion object {
        const val ENTER_DURATION_MS = 300
        const val EXIT_DURATION_MS = 220
    }
}

@Composable
fun rememberPopUpOverlayState(): PopUpOverlayState = remember { PopUpOverlayState() }

@Composable
fun PopUpOverlay(
    device: PodDevice,
    target: PopUpTarget,
    onClose: () -> Unit,
    onExitFinished: (token: Long) -> Unit,
    extraBottomPadding: Dp,
    modifier: Modifier = Modifier,
    state: PopUpOverlayState = rememberPopUpOverlayState(),
) {
    val currentOnExitFinished by rememberUpdatedState(onExitFinished)
    // Entering needs the measured height, otherwise the slide would start from the rest position.
    val ready = !target.visible || state.isMeasured

    LaunchedEffect(target, ready) {
        if (!ready) return@LaunchedEffect
        if (target.visible) {
            state.animateToRest()
        } else {
            state.animateToHidden()
            currentOnExitFinished(target.token)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { state.hiddenOffsetPx = it.height.toFloat() }
            .graphicsLayer {
                translationY = state.offsetPx
                alpha = state.alpha
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(bottom = 8.dp + extraBottomPadding),
    ) {
        PopUpContent(device = device, onClose = onClose)
    }
}

@Preview2
@Composable
private fun PopUpOverlayDualPodPreview() = PreviewWrapper {
    PopUpOverlay(
        device = MockPodDataProvider.dualPodMonitoredMixed(),
        target = PopUpTarget(visible = true, token = 0),
        onClose = {},
        onExitFinished = {},
        extraBottomPadding = 0.dp,
        state = remember { PopUpOverlayState(atRest = true) },
    )
}

@Preview2
@Composable
private fun PopUpOverlaySinglePodPreview() = PreviewWrapper {
    PopUpOverlay(
        device = MockPodDataProvider.singlePodMonitored(),
        target = PopUpTarget(visible = true, token = 0),
        onClose = {},
        onExitFinished = {},
        extraBottomPadding = 16.dp,
        state = remember { PopUpOverlayState(atRest = true) },
    )
}
