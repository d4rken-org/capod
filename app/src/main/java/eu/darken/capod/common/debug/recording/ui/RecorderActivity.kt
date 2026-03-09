package eu.darken.capod.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.compose.ConfirmationDialog
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.core.themeState
import javax.inject.Inject

private sealed interface RecorderDialog {
    data object SentConfirm : RecorderDialog
    data object DeleteConfirm : RecorderDialog
}

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderActivityVM by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_SESSION_ID) == null && intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsStateWithLifecycle(initialValue = generalSettings.currentThemeState)
            CapodTheme(state = themeState) {
                val backgroundColor = MaterialTheme.colorScheme.background
                val useDarkIcons = backgroundColor.luminance() > 0.5f
                SideEffect {
                    window.decorView.setBackgroundColor(backgroundColor.toArgb())
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = useDarkIcons
                    insetsController.isAppearanceLightNavigationBars = useDarkIcons
                }

                var hasShared by remember { mutableStateOf(false) }
                var dialog by remember { mutableStateOf<RecorderDialog?>(null) }

                LaunchedEffect(Unit) {
                    vm.events.collect { event ->
                        when (event) {
                            is RecorderActivityVM.Event.ShareIntent -> {
                                hasShared = true
                                startActivity(event.intent)
                            }
                            is RecorderActivityVM.Event.Finish -> finish()
                        }
                    }
                }

                LifecycleResumeEffect(hasShared) {
                    if (hasShared) {
                        dialog = RecorderDialog.SentConfirm
                        hasShared = false
                    }
                    onPauseOrDispose {}
                }

                when (dialog) {
                    is RecorderDialog.SentConfirm -> {
                        ConfirmationDialog(
                            title = stringResource(R.string.support_debuglog_sent_title),
                            message = stringResource(R.string.support_debuglog_sent_message),
                            confirmLabel = stringResource(R.string.general_done_action),
                            dismissLabel = stringResource(R.string.general_cancel_action),
                            onConfirm = {
                                dialog = null
                                vm.discard()
                            },
                            onDismiss = { dialog = null },
                        )
                    }

                    is RecorderDialog.DeleteConfirm -> {
                        ConfirmationDialog(
                            title = stringResource(R.string.support_debuglog_session_delete_title),
                            message = stringResource(R.string.support_debuglog_session_delete_message),
                            confirmLabel = stringResource(R.string.profiles_delete_action),
                            dismissLabel = stringResource(R.string.general_cancel_action),
                            onConfirm = {
                                dialog = null
                                vm.discard()
                            },
                            onDismiss = { dialog = null },
                        )
                    }

                    null -> {}
                }

                val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onKeep = { vm.keep() },
                        onDiscard = { dialog = RecorderDialog.DeleteConfirm },
                        onPrivacyPolicy = { vm.goPrivacyPolicy() },
                    )
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_SESSION_ID = "sessionId"
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, sessionId: String, legacyPath: String? = null): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_SESSION_ID, sessionId)
            if (legacyPath != null) intent.putExtra(RECORD_PATH, legacyPath)
            return intent
        }
    }
}
