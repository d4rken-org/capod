package eu.darken.capod.main.ui.settings.acks

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.common.settings.SettingsBaseItem
import eu.darken.capod.common.settings.SettingsCategoryHeader

@Composable
fun AcknowledgementsScreenHost(vm: AcknowledgementsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    AcknowledgementsScreen(
        onNavigateUp = { vm.navUp() },
        onOpenUrl = { url -> vm.openUrl(url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgementsScreen(
    onNavigateUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_acknowledgements_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsCategoryHeader(text = stringResource(R.string.general_thank_you_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.translators_thanks_title),
                    subtitle = stringResource(R.string.translators_thanks_description),
                    onClick = { onOpenUrl("https://crowdin.com/project/capod/activity-stream") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Max Patchs",
                    subtitle = "Thanks for the lovely icons.",
                    onClick = { onOpenUrl("https://twitter.com/maxpatchs") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "OpenPods project",
                    subtitle = "Pioneering AirPod support on Android",
                    onClick = { onOpenUrl("https://github.com/adolfintel/OpenPods") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "MagicPods project",
                    subtitle = "Pioneering AirPod support on Windows",
                    onClick = { onOpenUrl("https://github.com/steam3d/MagicPods-Windows") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "FuriousMAC",
                    subtitle = "Research on the continuity protocol",
                    onClick = { onOpenUrl("https://github.com/furiousMAC/continuity") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "crowdin.com",
                    subtitle = "For supporting translation of open-source projects",
                    onClick = { onOpenUrl("https://crowdin.com/") },
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_licenses_label))
            }
            item {
                SettingsBaseItem(
                    title = "Glide",
                    subtitle = "An image loading and caching library for Android focused on smooth scrolling. (Multiple licenses)",
                    onClick = { onOpenUrl("https://github.com/bumptech/glide") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Material Design Icons",
                    subtitle = "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)",
                    onClick = { onOpenUrl("https://github.com/Templarian/MaterialDesign") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Zwicon Icon Set",
                    subtitle = "Creative Commons Attribution 4.0 International",
                    onClick = { onOpenUrl("https://iconduck.com/sets/zwicon-icon-set") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Kotlin",
                    subtitle = "The Kotlin Programming Language. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/JetBrains/kotlin") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Dagger",
                    subtitle = "A fast dependency injector for Android and Java. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/google/dagger") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Moshi",
                    subtitle = "A modern JSON library for Kotlin and Java. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/square/moshi") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle = "Android Open Source Project (APACHE 2.0)",
                    onClick = { onOpenUrl("https://source.android.com/source/licenses.html") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle = "The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.",
                    onClick = { onOpenUrl("https://developer.android.com/distribute/tools/promote/brand.html") },
                )
            }
        }
    }
}
