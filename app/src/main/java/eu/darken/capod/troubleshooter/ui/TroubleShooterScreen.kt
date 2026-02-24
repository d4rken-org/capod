package eu.darken.capod.troubleshooter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.capod.R
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler

@Composable
fun TroubleShooterScreenHost(vm: TroubleShooterViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        TroubleShooterScreen(
            state = it,
            onStart = { vm.troubleShootBle() },
            onRetry = { vm.troubleShootBle() },
            onNavigateUp = { vm.navUp() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleShooterScreen(
    state: TroubleShooterViewModel.State,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.troubleshooter_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    when (val bleState = state.bleState) {
                        is TroubleShooterViewModel.BleState.Intro -> {
                            IntroContent(onStart = onStart)
                        }

                        is TroubleShooterViewModel.BleState.Working -> {
                            WorkingContent(state = bleState)
                        }

                        is TroubleShooterViewModel.BleState.Result -> {
                            ResultContent(state = bleState, onRetry = onRetry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit) {
    Text(
        text = stringResource(R.string.troubleshooter_ble_intro_title),
        style = MaterialTheme.typography.titleMedium,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(R.string.troubleshooter_ble_intro_body1),
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.troubleshooter_ble_intro_start_action))
    }
}

@Composable
private fun WorkingContent(state: TroubleShooterViewModel.BleState.Working) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = stringResource(R.string.troubleshooter_ble_process_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.troubleshooter_ble_process_subtile),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = state.allSteps.mapIndexed { index, step -> "#$index: $step" }.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ResultContent(
    state: TroubleShooterViewModel.BleState.Result,
    onRetry: () -> Unit,
) {
    when (state) {
        is TroubleShooterViewModel.BleState.Result.Success -> {
            Text(
                text = stringResource(R.string.troubleshooter_ble_result_success_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.troubleshooter_ble_result_success_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is TroubleShooterViewModel.BleState.Result.Failure -> {
            Text(
                text = stringResource(R.string.troubleshooter_ble_result_failure_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (state.failureType) {
                    TroubleShooterViewModel.BleState.Result.Failure.Type.PHONE ->
                        stringResource(R.string.troubleshooter_ble_result_failure_phone_body)

                    TroubleShooterViewModel.BleState.Result.Failure.Type.HEADPHONES ->
                        stringResource(R.string.troubleshooter_ble_result_failure_phone_headphones)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.general_check_action))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = state.history.mapIndexed { index, step -> "#$index: $step" }.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
    )
}
