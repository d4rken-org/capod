package eu.darken.capod.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.main.ui.overview.OverviewScreen
import eu.darken.capod.main.ui.overview.OverviewViewModel
import eu.darken.capod.main.ui.widget.ComposeWidgetPreview
import eu.darken.capod.main.ui.widget.WidgetConfigurationScreen
import eu.darken.capod.main.ui.widget.WidgetConfigurationViewModel
import eu.darken.capod.main.ui.widget.WidgetRenderState
import eu.darken.capod.main.ui.widget.WidgetTheme
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.ui.DeviceManagerScreen
import eu.darken.capod.profiles.ui.DeviceManagerViewModel
import eu.darken.capod.profiles.ui.creation.DeviceProfileCreationScreen
import eu.darken.capod.profiles.ui.creation.DeviceProfileCreationViewModel
import eu.darken.capod.main.ui.devicesettings.DeviceSettingsScreen
import eu.darken.capod.main.ui.devicesettings.DeviceSettingsViewModel
import eu.darken.capod.reaction.ui.popup.PopUpContent as PopUpCard

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

@Composable
internal fun DashboardContent(showAap: Boolean = false) = PreviewWrapper {
    val devices = if (showAap) {
        listOf(
            MockPodDataProvider.dualPodMonitoredWithAap(),
            MockPodDataProvider.singlePodMonitoredWithAap(),
            MockPodDataProvider.unknownMonitored(),
        )
    } else {
        listOf(
            MockPodDataProvider.dualPodMonitoredMixed(),
            MockPodDataProvider.singlePodMonitored(),
            MockPodDataProvider.unknownMonitored(),
        )
    }
    OverviewScreen(
        state = OverviewViewModel.State(
            now = MOCK_NOW,
            permissions = emptySet(),
            devices = devices,
            isDebug = false,
            isBluetoothEnabled = true,
            profiles = listOf(
                MockPodDataProvider.profile("My AirPods Pro", PodModel.AIRPODS_PRO2),
                MockPodDataProvider.profile("AirPods Max", PodModel.AIRPODS_MAX),
            ),
            upgradeInfo = MockPodDataProvider.gplayInfo(isPro = true),
            showUnmatchedDevices = false,
        ),
        onRequestPermission = {},
        onBluetoothSettings = {},
        onManageDevices = {},
        onSettings = {},
        onUpgrade = {},
        onToggleUnmatched = {},
    )
}

@Composable
internal fun DeviceProfilesContent() = PreviewWrapper {
    DeviceManagerScreen(
        state = DeviceManagerViewModel.State(
            profiles = listOf(
                MockPodDataProvider.profile("Work AirPods", PodModel.AIRPODS_PRO2),
                MockPodDataProvider.profile("AirPods Max", PodModel.AIRPODS_MAX),
                MockPodDataProvider.profile("Gym Beats", PodModel.POWERBEATS_PRO),
            ),
        ),
        onBack = {},
        onAddDevice = {},
        onEditProfile = {},
    )
}

@Composable
internal fun AddProfileContent() = PreviewWrapper {
    val pairedAirPods = BluetoothDevice2(
        address = "AA:BB:CC:DD:EE:FF",
        name = "AirPods Pro",
        seenFirstAt = MOCK_NOW,
    )
    val bondedItems = listOf(
        DeviceProfileCreationViewModel.BondedDeviceItem(
            device = pairedAirPods,
            claimedByProfile = null,
        ),
        DeviceProfileCreationViewModel.BondedDeviceItem(
            device = BluetoothDevice2(
                address = "11:22:33:44:55:66",
                name = "Living Room TV",
                seenFirstAt = MOCK_NOW,
            ),
            claimedByProfile = null,
        ),
    )
    DeviceProfileCreationScreen(
        state = DeviceProfileCreationViewModel.State(
            isEditMode = false,
            name = "My AirPods Pro",
            nameError = null,
            selectedModel = PodModel.AIRPODS_PRO2,
            availableModels = PodModel.entries.filter { it != PodModel.UNKNOWN },
            identityKey = null,
            encryptionKey = null,
            selectedDevice = pairedAirPods,
            bondedDeviceItems = bondedItems,
            minimumSignalQuality = 0.15f,
            canSave = true,
        ),
        onBack = {},
        onSave = {},
        onDelete = {},
        onNameChange = {},
        onModelChange = {},
        onDeviceChange = {},
        onIdentityKeyChange = {},
        onEncryptionKeyChange = {},
        onSignalQualityChange = {},
        onKeyGuide = {},
    )
}

@Composable
internal fun DeviceSettingsReactionsContent() = PreviewWrapper {
    DeviceSettingsScreen(
        state = DeviceSettingsViewModel.State(
            device = MockPodDataProvider.dualPodMonitoredWithReactions(),
            now = MOCK_NOW,
            isPro = true,
            isNudgeAvailable = true,
            isClassicallyConnected = true,
        ),
        onNavigateUp = {},
    )
}

@Composable
internal fun WidgetConfigurationContent() = PreviewWrapper {
    val profiles = listOf(
        MockPodDataProvider.profile("My AirPods Pro", PodModel.AIRPODS_PRO2),
        MockPodDataProvider.profile("AirPods Max", PodModel.AIRPODS_MAX),
    )
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            profiles = profiles,
            selectedProfile = profiles.first().id,
            isPro = true,
            theme = WidgetTheme.DEFAULT,
            activePreset = WidgetTheme.Preset.MATERIAL_YOU,
            isCustomMode = false,
        ),
        onSelectProfile = {},
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}

@Composable
internal fun CasePopUpContent() {
    CapodTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF0D47A1),
                            Color(0xFF006064),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            PopUpCard(
                device = MockPodDataProvider.dualPodMonitoredMixed(),
                onClose = {},
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
internal fun HomescreenWidgetContent() {
    CapodTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF0D47A1),
                            Color(0xFF006064),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            ComposeWidgetPreview(
                state = WidgetRenderState.previewDualPod(
                    bgColor = MaterialTheme.colorScheme.surface.toArgb(),
                    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                    iconColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                ),
            )
        }
    }
}

@Preview(name = "1 - Dashboard Light", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewDashboardLight() = DashboardContent()

@Preview(name = "2 - Dashboard Dark", locale = "en", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true)
@Composable
private fun PreviewDashboardDark() = DashboardContent(showAap = true)

@Preview(name = "3 - Case Pop-up", device = DS, showSystemUi = true)
@Composable
private fun PreviewCasePopUp() = CasePopUpContent()

@Preview(name = "4 - Widget Configuration", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewWidgetConfiguration() = WidgetConfigurationContent()

@Preview(name = "5 - Device Profiles", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewDeviceProfiles() = DeviceProfilesContent()

@Preview(name = "6 - Add Profile", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewAddProfile() = AddProfileContent()

@Preview(name = "7 - Device Settings Reactions", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewDeviceSettingsReactions() = DeviceSettingsReactionsContent()

@Preview(name = "8 - Homescreen Widget", device = DS, showSystemUi = true)
@Composable
private fun PreviewHomescreenWidget() = HomescreenWidgetContent()
