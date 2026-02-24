package eu.darken.capod.main.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import eu.darken.capod.common.compose.waitForState
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler

@Composable
fun OnboardingScreenHost(vm: OnboardingViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    OnboardingScreen(
        onPrivacyPolicy = { vm.openPrivacyPolicy() },
        onContinue = { vm.finishOnboarding() },
    )
}

@Composable
fun OnboardingScreen(
    onPrivacyPolicy: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(R.drawable.splash_graphic2),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.onboarding_body1),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_body2),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_body3),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.onboarding_body4),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))

            FilledTonalButton(onClick = onPrivacyPolicy) {
                Text(text = stringResource(R.string.settings_privacy_policy_label))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                Text(text = stringResource(R.string.general_continue_action))
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Preview2
@Composable
private fun OnboardingScreenPreview() = PreviewWrapper {
    OnboardingScreen(onPrivacyPolicy = {}, onContinue = {})
}
