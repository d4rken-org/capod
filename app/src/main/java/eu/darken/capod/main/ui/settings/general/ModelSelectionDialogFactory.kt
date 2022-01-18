package eu.darken.capod.main.ui.settings.general

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R
import eu.darken.capod.pods.core.PodDevice


class ModelSelectionDialogFactory constructor(private val context: Context) {

    fun create(
        models: List<PodDevice.Model>,
        current: PodDevice.Model,
        callback: (PodDevice.Model) -> Unit
    ): AlertDialog {
        return MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.settings_maindevice_model_label)

            val pairing = models
                .map { it.label to it }

            setSingleChoiceItems(
                pairing.map { it.first }.toTypedArray(),
                pairing.indexOfFirst { it.second == current },
                DialogInterface.OnClickListener { dialog, which ->
                    val selected = pairing[which].second
                    callback(selected)
                    dialog.dismiss()
                }
            )

        }.create()
    }
}