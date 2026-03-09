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
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.core.themeState
import javax.inject.Inject

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

                var showSentConfirm by remember { mutableStateOf(false) }

                LifecycleResumeEffect(hasShared) {
                    if (hasShared) {
                        showSentConfirm = true
                        hasShared = false
                    }
                    onPauseOrDispose {}
                }

                if (showSentConfirm) {
                    LaunchedEffect(Unit) {
                        MaterialAlertDialogBuilder(this@RecorderActivity).apply {
                            setTitle(R.string.support_debuglog_sent_title)
                            setMessage(R.string.support_debuglog_sent_message)
                            setPositiveButton(R.string.general_done_action) { _, _ ->
                                showSentConfirm = false
                                vm.discard()
                            }
                            setNegativeButton(R.string.general_cancel_action) { _, _ ->
                                showSentConfirm = false
                            }
                            setOnCancelListener { showSentConfirm = false }
                        }.show()
                    }
                }

                var showDeleteConfirm by remember { mutableStateOf(false) }

                if (showDeleteConfirm) {
                    LaunchedEffect(Unit) {
                        MaterialAlertDialogBuilder(this@RecorderActivity).apply {
                            setTitle(R.string.support_debuglog_session_delete_title)
                            setMessage(R.string.support_debuglog_session_delete_message)
                            setPositiveButton(R.string.profiles_delete_action) { _, _ ->
                                showDeleteConfirm = false
                                vm.discard()
                            }
                            setNegativeButton(R.string.general_cancel_action) { _, _ ->
                                showDeleteConfirm = false
                            }
                            setOnCancelListener { showDeleteConfirm = false }
                        }.show()
                    }
                }

                val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
                state?.let {
                    RecorderScreen(
                        state = it,
                        onShare = { vm.share() },
                        onKeep = { vm.keep() },
                        onDiscard = { showDeleteConfirm = true },
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
