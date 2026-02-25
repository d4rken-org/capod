package eu.darken.capod.screenshots

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.capod.R
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.main.ui.widget.WidgetConfigurationScreen
import eu.darken.capod.main.ui.widget.WidgetConfigurationViewModel
import eu.darken.capod.main.ui.widget.WidgetTheme
import eu.darken.capod.common.compose.preview.MOCK_NOW
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.main.ui.overview.OverviewScreen
import eu.darken.capod.main.ui.overview.OverviewViewModel
import eu.darken.capod.main.ui.settings.SettingsScreen
import eu.darken.capod.main.ui.settings.SettingsViewModel
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.ui.DeviceManagerScreen
import eu.darken.capod.profiles.ui.DeviceManagerViewModel
import eu.darken.capod.profiles.ui.creation.DeviceProfileCreationScreen
import eu.darken.capod.profiles.ui.creation.DeviceProfileCreationViewModel
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.reaction.ui.ReactionSettingsScreen
import eu.darken.capod.reaction.ui.ReactionSettingsViewModel

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

@Composable
internal fun DashboardContent() = PreviewWrapper {
    OverviewScreen(
        state = OverviewViewModel.State(
            now = MOCK_NOW,
            permissions = emptySet(),
            devices = listOf(
                MockPodDataProvider.airPodsProMixed(),
                MockPodDataProvider.airPodsMax(),
                MockPodDataProvider.unknownDevice(),
            ),
            isDebugMode = false,
            isBluetoothEnabled = true,
            profiles = listOf(
                MockPodDataProvider.profile("My AirPods Pro", PodDevice.Model.AIRPODS_PRO2),
                MockPodDataProvider.profile("AirPods Max", PodDevice.Model.AIRPODS_MAX),
            ),
            upgradeInfo = MockPodDataProvider.gplayInfo(isPro = true),
            showUnmatchedDevices = false,
        ),
        onRequestPermission = {},
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
                MockPodDataProvider.profile("Work AirPods", PodDevice.Model.AIRPODS_PRO2),
                MockPodDataProvider.profile("AirPods Max", PodDevice.Model.AIRPODS_MAX),
                MockPodDataProvider.profile("Gym Beats", PodDevice.Model.POWERBEATS_PRO),
            ),
        ),
        onBack = {},
        onAddDevice = {},
        onEditProfile = {},
    )
}

@Composable
internal fun AddProfileContent() = PreviewWrapper {
    DeviceProfileCreationScreen(
        state = DeviceProfileCreationViewModel.State(
            isEditMode = false,
            name = "",
            nameError = null,
            selectedModel = null,
            availableModels = PodDevice.Model.entries.filter { it != PodDevice.Model.UNKNOWN },
            identityKey = null,
            encryptionKey = null,
            selectedDevice = null,
            bondedDevices = emptyList(),
            minimumSignalQuality = 0.15f,
            canSave = false,
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
internal fun SettingsIndexContent() = PreviewWrapper {
    SettingsScreen(
        state = SettingsViewModel.State(sponsorUrl = "https://example.com"),
        onNavigateUp = {},
        onGeneralSettings = {},
        onDeviceManager = {},
        onReactions = {},
        onSupport = {},
        onChangelog = {},
        onHelpTranslate = {},
        onAcknowledgements = {},
        onPrivacyPolicy = {},
        onSponsor = {},
    )
}

@Composable
internal fun ReactionSettingsContent() = PreviewWrapper {
    ReactionSettingsScreen(
        state = ReactionSettingsViewModel.State(
            isPro = true,
            onePodMode = false,
            autoPlay = true,
            autoPause = true,
            autoConnect = false,
            autoConnectCondition = AutoConnectCondition.WHEN_SEEN,
            showPopUpOnCaseOpen = true,
            showPopUpOnConnection = false,
        ),
        onNavigateUp = {},
        onOnePodModeChanged = {},
        onAutoPlayChanged = {},
        onAutoPauseChanged = {},
        onAutoConnectChanged = {},
        onAutoConnectConditionSelected = {},
        onShowPopUpOnCaseOpenChanged = {},
        onShowPopUpOnConnectionChanged = {},
    )
}

@Composable
internal fun WidgetConfigurationContent() = PreviewWrapper {
    val profiles = listOf(
        MockPodDataProvider.profile("My AirPods Pro", PodDevice.Model.AIRPODS_PRO2),
        MockPodDataProvider.profile("AirPods Max", PodDevice.Model.AIRPODS_MAX),
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
            WidgetDualCompact()
        }
    }
}

@Composable
private fun WidgetDualCompact() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetPodRow(
                icon = R.drawable.device_airpods_pro2_left,
                percent = 85,
                charging = false,
                inEar = true,
            )
            WidgetPodRow(
                icon = R.drawable.device_airpods_pro2_right,
                percent = 92,
                charging = true,
                inEar = false,
            )
            WidgetCaseRow(
                icon = R.drawable.device_airpods_pro2_case,
                percent = 100,
                charging = false,
            )
            Text(
                text = "My AirPods Pro",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WidgetPodRow(
    @DrawableRes icon: Int,
    percent: Int,
    charging: Boolean,
    inEar: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "$percent%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (charging) {
            Image(
                painter = painterResource(R.drawable.ic_baseline_power_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        if (inEar) {
            Image(
                painter = painterResource(R.drawable.ic_baseline_hearing_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun WidgetCaseRow(
    @DrawableRes icon: Int,
    percent: Int,
    charging: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "$percent%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (charging) {
            Image(
                painter = painterResource(R.drawable.ic_baseline_power_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(name = "1 - Dashboard Light", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewDashboardLight() = DashboardContent()

@Preview(name = "2 - Dashboard Dark", locale = "en", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true)
@Composable
private fun PreviewDashboardDark() = DashboardContent()

@Preview(name = "3 - Device Profiles", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewDeviceProfiles() = DeviceProfilesContent()

@Preview(name = "4 - Add Profile", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewAddProfile() = AddProfileContent()

@Preview(name = "5 - Settings", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewSettingsIndex() = SettingsIndexContent()

@Preview(name = "6 - Reaction Settings", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewReactionSettings() = ReactionSettingsContent()

@Preview(name = "7 - Homescreen Widget", device = DS, showSystemUi = true)
@Composable
private fun PreviewHomescreenWidget() = HomescreenWidgetContent()

@Preview(name = "7 - Widget Configuration", locale = "en", device = DS, showSystemUi = true)
@Composable
private fun PreviewWidgetConfiguration() = WidgetConfigurationContent()
