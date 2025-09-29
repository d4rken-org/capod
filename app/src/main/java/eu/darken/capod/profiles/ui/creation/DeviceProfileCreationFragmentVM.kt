package eu.darken.capod.profiles.ui.creation

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.toHex
import eu.darken.capod.common.uix.ViewModel3
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

data class ProfileEditorState(
    val name: String = "",
    val selectedModel: PodDevice.Model? = null,
    val identityKeyHex: String? = null,
    val encryptionKeyHex: String? = null,
    val selectedDeviceAddress: String? = null,
    val minimumSignalQuality: Float = DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
)

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

    private val _currentState = MutableStateFlow(ProfileEditorState())
    private val _initialState = MutableStateFlow(ProfileEditorState())

    private val _nameError = MutableStateFlow<String?>(null)
    
    init {
        if (isEditMode && profileId != null) {
            loadProfile(profileId)
        } else {
            // Prefill with localizable default for new profiles
            val defaultName = context.getString(R.string.profiles_name_default)
            _currentState.value = _currentState.value.copy(name = defaultName)
            // Initial state remains empty for create mode
        }
    }

    val name: LiveData<String> = _currentState.map { it.name }.asLiveData()
    val selectedModel: LiveData<PodDevice.Model?> = _currentState.map { it.selectedModel }.asLiveData()
    val nameError: LiveData<String?> = _nameError.asLiveData()
    val identityKey: LiveData<IdentityResolvingKey?> = _currentState.map { 
        it.identityKeyHex?.fromHex()
    }.asLiveData()
    val encryptionKey: LiveData<ProximityEncryptionKey?> = _currentState.map { 
        it.encryptionKeyHex?.fromHex()
    }.asLiveData()
    val selectedDevice: LiveData<BluetoothDevice2?> = combine(
        _currentState,
        bluetoothManager.bondedDevices().catch { emit(emptySet()) }
    ) { state: ProfileEditorState, devices: Set<BluetoothDevice2> ->
        state.selectedDeviceAddress?.let { address ->
            devices.find { it.address == address }
        }
    }.asLiveData()
    val minimumSignalQuality: LiveData<Float> = _currentState.map { it.minimumSignalQuality }.asLiveData()

    val availableModels: List<PodDevice.Model> = PodDevice.Model.entries
    
    val bondedDevices: LiveData<List<BluetoothDevice2>> = bluetoothManager.bondedDevices()
        .catch { emit(emptySet()) }
        .map { it.toList() }
        .asLiveData()

    private val hasUnsavedChangesFlow = combine(
        _currentState, _initialState
    ) { current, initial ->
        if (isEditMode) {
            // For edit mode, check if current state differs from initial state
            current != initial
        } else {
            // For create mode, check if any meaningful values have been entered
            current.name.isNotBlank() ||
            current.selectedModel != null ||
            current.identityKeyHex != null ||
            current.encryptionKeyHex != null ||
            current.selectedDeviceAddress != null ||
            current.minimumSignalQuality != DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
        }
    }

    private val isFormValid = combine(
        _currentState,
        _nameError
    ) { state, nameError ->
        state.name.isNotBlank() && 
        state.selectedModel != null && 
        nameError == null
    }

    val canSave: LiveData<Boolean> = combine(
        isFormValid,
        hasUnsavedChangesFlow
    ) { valid, hasChanges ->
        valid && hasChanges
    }.asLiveData()

    fun hasUnsavedChanges(): Boolean {
        val current = _currentState.value
        val initial = _initialState.value
        
        return if (isEditMode) {
            // For edit mode, check if current state differs from initial state
            current != initial
        } else {
            // For create mode, check if any meaningful values have been entered
            current.name.isNotBlank() ||
            current.selectedModel != null ||
            current.identityKeyHex != null ||
            current.encryptionKeyHex != null ||
            current.selectedDeviceAddress != null ||
            current.minimumSignalQuality != DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
        }
    }

    fun updateName(name: String) {
        _currentState.value = _currentState.value.copy(name = name)
        _nameError.value = if (name.isBlank()) "Profile name is required" else null
    }

    fun updateModel(model: PodDevice.Model) {
        _currentState.value = _currentState.value.copy(selectedModel = model)
        log(TAG) { "Selected model: $model" }
    }

    fun updateIdentityKey(key: IdentityResolvingKey?) {
        _currentState.value = _currentState.value.copy(identityKeyHex = key?.toHex())
        log(TAG) { "Identity key updated: ${key != null}" }
    }

    fun updateEncryptionKey(key: ProximityEncryptionKey?) {
        _currentState.value = _currentState.value.copy(encryptionKeyHex = key?.toHex())
        log(TAG) { "Encryption key updated: ${key != null}" }
    }
    
    fun updateSelectedDevice(device: BluetoothDevice2?) {
        _currentState.value = _currentState.value.copy(selectedDeviceAddress = device?.address)
        log(TAG) { "Selected device updated: ${device?.name} (${device?.address})" }
    }
    
    fun updateMinimumSignalQuality(quality: Float) {
        _currentState.value = _currentState.value.copy(minimumSignalQuality = quality)
        log(TAG) { "Minimum signal quality updated: $quality" }
    }
    
    private fun loadProfile(profileId: String) {
        launch {
            try {
                val profiles = deviceProfilesRepo.currentProfiles()
                val profile = profiles.find { it.id == profileId }
                if (profile != null) {
                    // Create the loaded state
                    val loadedState = ProfileEditorState(
                        name = profile.label,
                        selectedModel = profile.model,
                        identityKeyHex = (profile as? AppleDeviceProfile)?.identityKey?.toHex(),
                        encryptionKeyHex = (profile as? AppleDeviceProfile)?.encryptionKey?.toHex(),
                        selectedDeviceAddress = (profile as? AppleDeviceProfile)?.address,
                        minimumSignalQuality = profile.minimumSignalQuality ?: DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY
                    )
                    
                    // Set both initial and current state atomically
                    _initialState.value = loadedState
                    _currentState.value = loadedState
                    
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
        
        val state = _currentState.value
        val name = state.name.trim()
        
        if (name.isBlank()) {
            _nameError.value = "Profile name is required"
            return
        }
        
        if (state.selectedModel == null) {
            log(TAG) { "No model selected" }
            return
        }
        
        
        launch {
            try {
                val profile = AppleDeviceProfile(
                    id = if (isEditMode) profileId!! else UUID.randomUUID().toString(),
                    label = name,
                    model = state.selectedModel,
                    minimumSignalQuality = state.minimumSignalQuality,
                    identityKey = state.identityKeyHex?.fromHex(),
                    encryptionKey = state.encryptionKeyHex?.fromHex(),
                    address = state.selectedDeviceAddress
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