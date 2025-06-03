package eu.darken.capod.main.ui.settings.general

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.darken.capod.R
import eu.darken.capod.common.UIConverter

class AirPodKeyInputDialog(private val context: Context) {
    fun create(
        mode: Mode,
        current: String,
        onKey: (String) -> Unit,
        onGuide: () -> Unit,
    ): AlertDialog {
        val inputEditText = TextInputEditText(context).apply {
            setText(current)
        }
        val inputLayout =
            TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                hint = context.getString(
                    R.string.general_example_label,
                    "FE-D0-1C-54-11-81-BC-BC-87-D2-C4-3F-31-64-5F-EE"
                )
                addView(inputEditText)
                val padding = UIConverter.convertDpToPixels(context, 24f)
                setPadding(padding, padding / 2, padding, 0)
            }

        val dialog = MaterialAlertDialogBuilder(context).apply {
            setView(inputLayout)
            when (mode) {
                Mode.IRK -> {
                    setTitle(R.string.settings_maindevice_identitykey_label)
                    setMessage(R.string.settings_maindevice_identitykey_explanation)
                }

                Mode.ENC -> {
                    setTitle(R.string.settings_maindevice_encryptionkey_label)
                    setMessage(R.string.settings_maindevice_encryptionkey_explanation)
                }
            }

            setPositiveButton(R.string.general_save_action) { _, _ -> onKey(inputEditText.text.toString()) }
            setNegativeButton(R.string.general_cancel_action) { _, _ -> }
            setNeutralButton(R.string.general_guide_action) { _, _ -> onGuide() }
        }.create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            val keyRegex = Regex("^([0-9A-F]{2}-){15}[0-9A-F]{2}$")
            fun validateInput() {
                val isValid = inputEditText.text.toString().matches(keyRegex)
                saveButton.isEnabled = isValid || inputEditText.length() == 0
            }
            inputEditText.doOnTextChanged { _, _, _, _ -> validateInput() }
            validateInput()
        }
        return dialog
    }

    enum class Mode {
        IRK,
        ENC,
        ;
    }
}