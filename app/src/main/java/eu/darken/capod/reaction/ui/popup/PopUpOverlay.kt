package eu.darken.capod.reaction.ui.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.monitor.core.PodDevice

@Composable
fun PopUpOverlay(
    device: PodDevice,
    onClose: () -> Unit,
    extraBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
        onClose = {},
        extraBottomPadding = 0.dp,
    )
}

@Preview2
@Composable
private fun PopUpOverlaySinglePodPreview() = PreviewWrapper {
    PopUpOverlay(
        device = MockPodDataProvider.singlePodMonitored(),
        onClose = {},
        extraBottomPadding = 16.dp,
    )
}
