package eu.darken.capod.common.debug.recording.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.error.asErrorDialogBuilder
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.databinding.DebugRecordingActivityBinding

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private lateinit var ui: DebugRecordingActivityBinding
    private val vm: RecorderActivityVM by viewModels()
    private val logFileAdapter = LogFileAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        ui = DebugRecordingActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, right = systemBars.right)
            insets
        }

        ui.logFilesList.apply {
            layoutManager = LinearLayoutManager(this@RecorderActivity)
            adapter = logFileAdapter
        }

        vm.state.observe2 { state ->
            ui.loadingIndicator.isVisible = state.isWorking
            ui.actionShare.isEnabled = !state.isWorking
            ui.shareLoading.isVisible = state.isWorking

            ui.sessionPath.text = state.logDir?.path ?: ""

            val fileCount = state.logEntries.size
            val compressedText = if (state.compressedSize >= 0) {
                "ZIP: ${Formatter.formatShortFileSize(this, state.compressedSize)}"
            } else {
                "..."
            }
            ui.logFilesCaption.text = resources.getQuantityString(
                eu.darken.capod.R.plurals.debug_debuglog_screen_log_files_ready,
                fileCount,
                fileCount,
                compressedText,
            )
            ui.fileCountBadge.text = fileCount.toString()

            logFileAdapter.submitList(state.logEntries)
        }

        vm.errorEvents.observe2 {
            it.asErrorDialogBuilder(this).show()
        }

        ui.privacyPolicy.setOnClickListener { vm.goPrivacyPolicy() }
        ui.actionShare.setOnClickListener { vm.share() }
        ui.actionKeep.setOnClickListener { vm.keep() }
        ui.actionDiscard.setOnClickListener { vm.discard() }

        vm.shareEvent.observe2 { startActivity(it) }
        vm.finishEvent.observe2 { finish() }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, path: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_PATH, path)
            return intent
        }
    }
}
