package eu.darken.capod.profiles.ui.creation

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.R
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DeviceProfileCreationFragmentVM @Inject constructor(
    @ApplicationContext private val context: Context,
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val profileId: String? = handle.get<ProfileId>("profileId")
    val isEditMode: Boolean = profileId != null

    private val _name = MutableStateFlow("")
    private val _selectedModel = MutableStateFlow<PodDevice.Model?>(null)
    private val _identityKey = MutableStateFlow<IdentityResolvingKey?>(null)
    private val _encryptionKey = MutableStateFlow<ProximityEncryptionKey?>(null)
    private val _selectedDevice = MutableStateFlow<BluetoothDevice2?>(null)
    private val _minimumSignalQuality = MutableStateFlow(DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY)

    // Track initial values for unsaved changes detection
    private val initialName = MutableStateFlow("")
    private val initialSelectedModel = MutableStateFlow<PodDevice.Model?>(null)
    private val initialIdentityKey = MutableStateFlow<IdentityResolvingKey?>(null)
    private val initialEncryptionKey = MutableStateFlow<ProximityEncryptionKey?>(null)
    private val initialSelectedDevice = MutableStateFlow<BluetoothDevice2?>(null)
    private val initialMinimumSignalQuality = MutableStateFlow(DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY)

    private val _nameError = MutableStateFlow<String?>(null)
    
    init {
        if (isEditMode && profileId != null) {
            loadProfile(profileId)
        } else {
            // Prefill with localizable default for new profiles
            _name.value = context.getString(R.string.profiles_name_default)
        }
    }

    val name: LiveData<String> = _name.asLiveData()
    val selectedModel: LiveData<PodDevice.Model?> = _selectedModel.asLiveData()
    val nameError: LiveData<String?> = _nameError.asLiveData()
    val identityKey: LiveData<IdentityResolvingKey?> = _identityKey.asLiveData()
    val encryptionKey: LiveData<ProximityEncryptionKey?> = _encryptionKey.asLiveData()
    val selectedDevice: LiveData<BluetoothDevice2?> = _selectedDevice.asLiveData()
    val minimumSignalQuality: LiveData<Float> = _minimumSignalQuality.asLiveData()

    val availableModels: List<PodDevice.Model> = PodDevice.Model.entries
    
    val bondedDevices: LiveData<List<BluetoothDevice2>> = bluetoothManager.bondedDevices()
        .catch { emit(emptySet()) }
        .map { it.toList() }
        .asLiveData()

    private val hasUnsavedChangesFlow = combine(
        _name, _selectedModel, _identityKey, _encryptionKey, _selectedDevice, _minimumSignalQuality,
        initialName, initialSelectedModel, initialIdentityKey, initialEncryptionKey, initialSelectedDevice, initialMinimumSignalQuality
    ) { flows ->
        val currentName = flows[0] as String
        val currentModel = flows[1] as PodDevice.Model?
        val currentIdentityKey = flows[2] as IdentityResolvingKey?
        val currentEncryptionKey = flows[3] as ProximityEncryptionKey?
        val currentDevice = flows[4] as BluetoothDevice2?
        val currentSignalQuality = flows[5] as Float
        
        val initName = flows[6] as String
        val initModel = flows[7] as PodDevice.Model?
        val initIdentityKey = flows[8] as IdentityResolvingKey?
        val initEncryptionKey = flows[9] as ProximityEncryptionKey?
        val initDevice = flows[10] as BluetoothDevice2?
        val initSignalQuality = flows[11] as Float
        
        if (isEditMode) {
            // For edit mode, check if any current values differ from initial
            currentName != initName ||
            currentModel != initModel ||
            currentIdentityKey != initIdentityKey ||
            currentEncryptionKey != initEncryptionKey ||
            currentDevice?.address != initDevice?.address ||
            currentSignalQuality != initSignalQuality
        } else {
            // For create mode, check if any values have been entered
            currentName.isNotBlank() ||
            currentModel != null ||
            currentIdentityKey != null ||
            currentEncryptionKey != null ||
            currentDevice != null ||
            currentSignalQuality != DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
        }
    }

    private val isFormValid = combine(
        _name,
        _selectedModel,
        _nameError
    ) { name, model, nameError ->
        name.isNotBlank() && 
        model != null && 
        nameError == null
    }

    val canSave: LiveData<Boolean> = combine(
        isFormValid,
        hasUnsavedChangesFlow
    ) { valid, hasChanges ->
        valid && hasChanges
    }.asLiveData()

    fun hasUnsavedChanges(): Boolean {
        return if (isEditMode) {
            // For edit mode, check if any current values differ from initial
            _name.value != initialName.value ||
            _selectedModel.value != initialSelectedModel.value ||
            _identityKey.value != initialIdentityKey.value ||
            _encryptionKey.value != initialEncryptionKey.value ||
            _selectedDevice.value?.address != initialSelectedDevice.value?.address ||
            _minimumSignalQuality.value != initialMinimumSignalQuality.value
        } else {
            // For create mode, check if any values have been entered
            _name.value.isNotBlank() ||
            _selectedModel.value != null ||
            _identityKey.value != null ||
            _encryptionKey.value != null ||
            _selectedDevice.value != null ||
            _minimumSignalQuality.value != DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
        }
    }

    fun updateName(name: String) {
        _name.value = name
        _nameError.value = if (name.isBlank()) "Profile name is required" else null
    }

    fun updateModel(model: PodDevice.Model) {
        _selectedModel.value = model
        log(TAG) { "Selected model: $model" }
    }

    fun updateIdentityKey(key: IdentityResolvingKey?) {
        _identityKey.value = key
        log(TAG) { "Identity key updated: ${key != null}" }
    }

    fun updateEncryptionKey(key: ProximityEncryptionKey?) {
        _encryptionKey.value = key
        log(TAG) { "Encryption key updated: ${key != null}" }
    }
    
    fun updateSelectedDevice(device: BluetoothDevice2?) {
        _selectedDevice.value = device
        log(TAG) { "Selected device updated: ${device?.name} (${device?.address})" }
    }
    
    fun updateMinimumSignalQuality(quality: Float) {
        _minimumSignalQuality.value = quality
        log(TAG) { "Minimum signal quality updated: $quality" }
    }
    
    private fun loadProfile(profileId: String) {
        launch {
            try {
                val profiles = deviceProfilesRepo.profiles.first()
                val profile = profiles.find { it.id == profileId }
                if (profile != null) {
                    // Set current values
                    _name.value = profile.label
                    _selectedModel.value = profile.model
                    _minimumSignalQuality.value = profile.minimumSignalQuality ?: DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
                    
                    if (profile is AppleDeviceProfile) {
                        _identityKey.value = profile.identityKey
                        _encryptionKey.value = profile.encryptionKey
                        
                        // Restore selected device if address is available
                        profile.address?.let { address ->
                            launch {
                                try {
                                    val bondedDevices = bluetoothManager.bondedDevices().first()
                                    val matchingDevice = bondedDevices.find { it.address == address }
                                    _selectedDevice.value = matchingDevice
                                    initialSelectedDevice.value = matchingDevice
                                    log(TAG) { "Restored selected device: ${matchingDevice?.name} ($address)" }
                                } catch (e: Exception) {
                                    log(TAG) { "Failed to restore selected device: $e" }
                                }
                            }
                        }
                    }
                    
                    // Set initial values for change tracking
                    initialName.value = profile.label
                    initialSelectedModel.value = profile.model
                    initialMinimumSignalQuality.value = profile.minimumSignalQuality ?: DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
                    
                    if (profile is AppleDeviceProfile) {
                        initialIdentityKey.value = profile.identityKey
                        initialEncryptionKey.value = profile.encryptionKey
                    }
                    
                    log(TAG) { "Profile loaded: ${profile.label}" }
                } else {
                    log(TAG) { "Profile not found: $profileId" }
                    errorEvents.postValue(IllegalArgumentException("Profile not found"))
                }
            } catch (e: Exception) {
                log(TAG) { "Failed to load profile: $e" }
                errorEvents.postValue(e)
            }
        }
    }


    fun saveProfile() {
        log(TAG) { "saveProfile()" }
        
        val name = _name.value.trim()
        val model = _selectedModel.value
        
        if (name.isBlank()) {
            _nameError.value = "Profile name is required"
            return
        }
        
        if (model == null) {
            log(TAG) { "No model selected" }
            return
        }
        
        
        launch {
            try {
                val profile = AppleDeviceProfile(
                    id = if (isEditMode) profileId!! else UUID.randomUUID().toString(),
                    label = name,
                    model = model,
                    minimumSignalQuality = _minimumSignalQuality.value,
                    identityKey = _identityKey.value,
                    encryptionKey = _encryptionKey.value,
                    address = _selectedDevice.value?.address
                )
                
                if (isEditMode) {
                    deviceProfilesRepo.updateProfile(profile)
                    log(TAG) { "Profile updated: $profile" }
                } else {
                    deviceProfilesRepo.addProfile(profile)
                    log(TAG) { "Profile created: $profile" }
                }
                popBackStack()
            } catch (e: Exception) {
                log(TAG) { "Failed to save profile: $e" }
                errorEvents.postValue(e)
            }
        }
    }

    fun deleteProfile() {
        if (isEditMode && profileId != null) {
            launch {
                try {
                    deviceProfilesRepo.removeProfile(profileId)
                    log(TAG) { "Profile deleted: $profileId" }
                    popBackStack()
                } catch (e: Exception) {
                    log(TAG) { "Failed to delete profile: $e" }
                    errorEvents.postValue(e)
                }
            }
        }
    }

    fun onBackPressed() {
        log(TAG) { "onBackPressed()" }
        if (hasUnsavedChanges()) {
            // Let the fragment handle the confirmation dialog
            // The fragment will call either discardChanges() or saveProfile()
        } else {
            popBackStack()
        }
    }
    
    fun discardChanges() {
        log(TAG) { "discardChanges()" }
        popBackStack()
    }

    private fun popBackStack() {
        navEvents.postValue(null)
    }

    companion object {
        private val TAG = logTag("DeviceProfileCreation", "ViewModel")
    }
}