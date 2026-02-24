package eu.darken.capod.profiles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.DeviceProfile

@Composable
fun DeviceManagerScreenHost(vm: DeviceManagerViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        DeviceManagerScreen(
            state = it,
            onBack = { vm.navUp() },
            onAddDevice = { vm.onAddDevice() },
            onEditProfile = { profile -> vm.onEditProfile(profile) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagerScreen(
    state: DeviceManagerViewModel.State,
    onBack: () -> Unit,
    onAddDevice: () -> Unit,
    onEditProfile: (DeviceProfile) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_devices_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(
                    imageVector = Icons.TwoTone.Add,
                    contentDescription = stringResource(R.string.profiles_add_action),
                )
            }
        },
    ) { innerPadding ->
        if (state.profiles.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAddDevice = onAddDevice,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(
                    items = state.profiles,
                    key = { it.id },
                ) { profile ->
                    ProfileRow(
                        profile = profile,
                        onClick = { onEditProfile(profile) },
                    )
                }

                if (state.profiles.size >= 2) {
                    item {
                        PriorityHint()
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DeviceManagerScreenWithProfilesPreview() = PreviewWrapper {
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

@Preview2
@Composable
private fun DeviceManagerScreenEmptyPreview() = PreviewWrapper {
    DeviceManagerScreen(
        state = DeviceManagerViewModel.State(profiles = emptyList()),
        onBack = {},
        onAddDevice = {},
        onEditProfile = {},
    )
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onAddDevice: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profiles_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profiles_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onAddDevice) {
                    Icon(
                        imageVector = Icons.TwoTone.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.profiles_add_action))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: DeviceProfile,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(profile.model.iconRes),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = profile.model.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PriorityHint() {
    Text(
        text = stringResource(R.string.profiles_priority_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
