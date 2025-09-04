package eu.darken.capod.profiles.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DeviceProfileCreationFragmentVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val profileId: String? = handle.get<String>("profileId")
    val isEditMode: Boolean = profileId != null

    private val _name = MutableStateFlow("")
    private val _selectedModel = MutableStateFlow<PodDevice.Model?>(null)
    private val _identityKey = MutableStateFlow<IdentityResolvingKey?>(null)
    private val _encryptionKey = MutableStateFlow<ProximityEncryptionKey?>(null)
    private val _selectedDevice = MutableStateFlow<BluetoothDevice2?>(null)
    private val _minimumSignalQuality = MutableStateFlow(0.20f)

    private val _nameError = MutableStateFlow<String?>(null)
    
    init {
        if (isEditMode && profileId != null) {
            loadProfile(profileId)
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

    private val isFormValid = combine(
        _name,
        _selectedModel,
        _nameError
    ) { name, model, nameError ->
        name.isNotBlank() && 
        model != null && 
        nameError == null
    }

    val canSave: LiveData<Boolean> = isFormValid.asLiveData()

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
                    _name.value = profile.label
                    _selectedModel.value = profile.model
                    _minimumSignalQuality.value = profile.minimumSignalQuality ?: 0.20f
                    
                    if (profile is AppleDeviceProfile) {
                        _identityKey.value = profile.identityKey
                        _encryptionKey.value = profile.encryptionKey
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
                    encryptionKey = _encryptionKey.value
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
        popBackStack()
    }

    private fun popBackStack() {
        navEvents.postValue(null)
    }

    companion object {
        private val TAG = logTag("DeviceProfileCreation", "ViewModel")
    }
}