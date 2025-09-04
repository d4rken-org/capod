package eu.darken.capod.devices.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.toHex
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.DeviceProfileCreationFragmentBinding
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject

@AndroidEntryPoint
class DeviceProfileCreationFragment : Fragment3(R.layout.device_profile_creation_fragment) {

    override val vm: DeviceProfileCreationFragmentVM by viewModels()
    override val ui: DeviceProfileCreationFragmentBinding by viewBinding()
    
    @Inject lateinit var webpageTool: WebpageTool

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.toolbar, top = true)
        }

        ui.apply {
            toolbar.setNavigationOnClickListener { vm.onBackPressed() }
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save -> {
                        vm.saveProfile()
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmation()
                        true
                    }
                    else -> false
                }
            }
            
            // Show delete button only in edit mode
            toolbar.menu.findItem(R.id.action_delete)?.isVisible = vm.isEditMode
            
            nameInput.doOnTextChanged { text, _, _, _ ->
                vm.updateName(text?.toString() ?: "")
            }
            
            signalQualitySlider.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
                vm.updateMinimumSignalQuality(value / 100f)
            }
            
            identityKeyInput.doOnTextChanged { text, _, _, _ ->
                val keyText = text?.toString() ?: ""
                vm.updateIdentityKey(keyText.fromHex().takeIf { it.isNotEmpty() })
            }
            
            encryptionKeyInput.doOnTextChanged { text, _, _, _ ->
                val keyText = text?.toString() ?: ""
                vm.updateEncryptionKey(keyText.fromHex().takeIf { it.isNotEmpty() })
            }
            
            identityKeyGuideButton.setOnClickListener {
                webpageTool.open("https://github.com/d4rken-org/capod/wiki/airpod-Keys")
            }
            
            encryptionKeyGuideButton.setOnClickListener {
                webpageTool.open("https://github.com/d4rken-org/capod/wiki/airpod-Keys")
            }
            
            // Set hints using same format as AirPodKeyInputDialog
            val exampleKey = "FE-D0-1C-54-11-81-BC-BC-87-D2-C4-3F-31-64-5F-EE"
            identityKeyInputLayout.hint = getString(R.string.general_example_label, exampleKey)
            encryptionKeyInputLayout.hint = getString(R.string.general_example_label, exampleKey)


            // Setup model dropdown
            val modelAdapter = ModelArrayAdapter(requireContext(), vm.availableModels)
            modelInput.setAdapter(modelAdapter)
            modelInput.setOnItemClickListener { _, _, position, _ ->
                val selectedModel = vm.availableModels[position]
                vm.updateModel(selectedModel)
            }
        }

        vm.bondedDevices.observe2(ui) { devices ->
            val deviceAdapter = DeviceArrayAdapter(requireContext(), devices)
            deviceInput.setAdapter(deviceAdapter)
            deviceInput.setOnItemClickListener { _, _, position, _ ->
                val selectedDevice = devices[position]
                vm.updateSelectedDevice(selectedDevice)
            }
        }


        vm.name.observe2(ui) { name ->
            if (nameInput.text?.toString() != name) {
                nameInput.setText(name)
            }
        }

        vm.selectedModel.observe2(ui) { model ->
            model?.let {
                if (modelInput.text?.toString() != it.label) {
                    modelInput.setText(it.label, false)
                }
            }
        }


        vm.nameError.observe2(ui) { error ->
            nameInputLayout.error = error
        }


        vm.canSave.observe2(ui) { canSave ->
            toolbar.menu.findItem(R.id.action_save)?.let { saveItem ->
                saveItem.isEnabled = canSave
                // Visual feedback for disabled state
                val alpha = if (canSave) 255 else 128
                saveItem.icon?.alpha = alpha
            }
        }
        
        vm.identityKey.observe2(ui) { key ->
            val keyText = key?.toHex() ?: ""
            if (identityKeyInput.text?.toString() != keyText) {
                identityKeyInput.setText(keyText)
            }
        }
        
        vm.encryptionKey.observe2(ui) { key ->
            val keyText = key?.toHex() ?: ""
            if (encryptionKeyInput.text?.toString() != keyText) {
                encryptionKeyInput.setText(keyText)
            }
        }
        
        vm.selectedDevice.observe2(ui) { device ->
            device?.let {
                val deviceText = "${it.name ?: "Unknown Device"} (${it.address})"
                if (deviceInput.text?.toString() != deviceText) {
                    deviceInput.setText(deviceText, false)
                }
            }
        }
        
        vm.minimumSignalQuality.observe2(ui) { quality ->
            val percentage = (quality * 100).toInt()
            signalQualityStatus.text = "$percentage%"
            if (signalQualitySlider.value != quality * 100f) {
                signalQualitySlider.value = quality * 100f
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }
    
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_profiles_delete_title)
            .setMessage(R.string.device_profiles_delete_message)
            .setPositiveButton(R.string.device_profiles_delete_action) { _, _ ->
                vm.deleteProfile()
            }
            .setNegativeButton(R.string.general_cancel_action, null)
            .show()
    }
    
    private class ModelArrayAdapter(
        context: Context,
        models: List<PodDevice.Model>
    ) : ArrayAdapter<PodDevice.Model>(context, R.layout.model_dropdown_item, models) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }
        
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }
        
        private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
            val model = getItem(position)!!
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.model_dropdown_item, parent, false)
            
            val iconView = view.findViewById<ImageView>(R.id.model_icon)
            val nameView = view.findViewById<TextView>(R.id.model_name)
            
            iconView.setImageResource(model.iconRes)
            nameView.text = model.label
            
            return view
        }
    }
    
    private class DeviceArrayAdapter(
        context: Context,
        devices: List<BluetoothDevice2>
    ) : ArrayAdapter<BluetoothDevice2>(context, R.layout.paired_device_dropdown_item, devices) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }
        
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }
        
        private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
            val device = getItem(position)!!
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.paired_device_dropdown_item, parent, false)
            
            val nameView = view.findViewById<TextView>(R.id.device_name)
            val addressView = view.findViewById<TextView>(R.id.device_address)
            
            nameView.text = device.name ?: "Unknown Device"
            addressView.text = device.address
            
            return view
        }
    }
}