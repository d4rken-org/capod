package eu.darken.capod.main.ui.settings.general

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothDevice2


class DeviceSelectionDialogFactory(private val context: Context) {

    fun create(
        devices: List<BluetoothDevice2>,
        current: BluetoothDevice2?,
        callback: (BluetoothDevice2?) -> Unit
    ): AlertDialog = MaterialAlertDialogBuilder(context).apply {
        setTitle(R.string.settings_maindevice_address_label)

        val pairing = devices
            .map { (it.name ?: "?") to it.address }
            .plus(context.getString(R.string.settings_maindevice_address_none) to "")

        setSingleChoiceItems(
            pairing.map { it.first }.toTypedArray(),
            pairing.indexOfFirst { it.second == current?.address }
        ) { dialog, which ->
            val selected = devices.firstOrNull { it.address == pairing[which].second }
            callback(selected)
            dialog.dismiss()
        }

    }.create()
}