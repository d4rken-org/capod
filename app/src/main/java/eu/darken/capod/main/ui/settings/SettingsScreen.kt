package eu.darken.capod.main.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Book
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SupportAgent
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material.icons.twotone.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.PrivacyPolicy
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader

@Composable
fun SettingsScreenHost(vm: SettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onGeneralSettings = { vm.navTo(Nav.Settings.General) },
            onDeviceManager = { vm.navTo(Nav.Main.DeviceManager) },
            onReactions = { vm.navTo(Nav.Settings.Reactions) },
            onSupport = { vm.navTo(Nav.Settings.Support) },
            onChangelog = { vm.openUrl("https://capod.darken.eu/changelog") },
            onHelpTranslate = { vm.openUrl("https://crowdin.com/project/capod") },
            onAcknowledgements = { vm.navTo(Nav.Settings.Acknowledgements) },
            onPrivacyPolicy = { vm.openUrl(PrivacyPolicy.URL) },
            onSponsor = { url -> vm.openUrl(url) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onGeneralSettings: () -> Unit,
    onDeviceManager: () -> Unit,
    onReactions: () -> Unit,
    onSupport: () -> Unit,
    onChangelog: () -> Unit,
    onHelpTranslate: () -> Unit,
    onAcknowledgements: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onSponsor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_label))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    val sponsorUrl = state.sponsorUrl
                    if (sponsorUrl != null) {
                        IconButton(onClick = { onSponsor(sponsorUrl) }) {
                            Icon(
                                imageVector = Icons.TwoTone.Favorite,
                                contentDescription = "Sponsor development",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_general_label),
                    subtitle = stringResource(R.string.settings_general_description),
                    icon = Icons.TwoTone.Settings,
                    onClick = onGeneralSettings,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_devices_label),
                    subtitle = stringResource(R.string.settings_devices_description),
                    icon = Icons.TwoTone.DevicesOther,
                    onClick = onDeviceManager,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_reaction_label),
                    subtitle = stringResource(R.string.settings_reaction_description),
                    icon = Icons.TwoTone.Widgets,
                    onClick = onReactions,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_support_label),
                    subtitle = stringResource(R.string.settings_support_description),
                    icon = Icons.TwoTone.SupportAgent,
                    onClick = onSupport,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.changelog_label),
                    subtitle = BuildConfigWrap.VERSION_DESCRIPTION,
                    iconPainter = painterResource(R.drawable.ic_changelog_onsurface),
                    onClick = onChangelog,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.help_translate_label),
                    subtitle = stringResource(R.string.help_translate_description),
                    icon = Icons.TwoTone.Translate,
                    onClick = onHelpTranslate,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_acknowledgements_label),
                    subtitle = stringResource(R.string.general_thank_you_label),
                    icon = Icons.TwoTone.Favorite,
                    onClick = onAcknowledgements,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_privacy_policy_label),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    icon = Icons.TwoTone.Book,
                    onClick = onPrivacyPolicy,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsScreenPreview() = PreviewWrapper {
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
