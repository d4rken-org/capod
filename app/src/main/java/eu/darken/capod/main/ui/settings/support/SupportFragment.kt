package eu.darken.capod.main.ui.settings.support

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.debug.recording.ui.RecorderConsentDialog
import eu.darken.capod.common.observe2
import eu.darken.capod.common.uix.PreferenceFragment3
import eu.darken.capod.main.core.GeneralSettings
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class SupportFragment : PreferenceFragment3() {

    override val vm: SupportFragmentVM by viewModels()

    override val preferenceFile: Int = R.xml.preferences_support
    @Inject lateinit var generalSettings: GeneralSettings

    override val settings: GeneralSettings by lazy { generalSettings }

    @Inject lateinit var webpageTool: WebpageTool

    private val debugLogPref by lazy { findPreference<Preference>("support.debuglog")!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm.recorderState.observe2(this) { state ->
            debugLogPref.setIcon(
                if (state.isRecording) R.drawable.ic_cancel
                else R.drawable.ic_baseline_bug_report_24
            )
            debugLogPref.setTitle(
                if (state.isRecording) R.string.debug_debuglog_stop_action
                else R.string.debug_debuglog_record_action
            )
            debugLogPref.summary = when {
                state.isRecording -> state.currentLogPath?.path
                else -> getString(R.string.debug_debuglog_record_action)
            }

            debugLogPref.setOnPreferenceClickListener {
                if (state.isRecording) {
                    vm.stopDebugLog()
                } else {
                    RecorderConsentDialog(requireContext(), webpageTool).showDialog {
                        vm.startDebugLog()
                    }
                }
                true
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }
}