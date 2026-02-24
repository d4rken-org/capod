package eu.darken.capod.profiles.ui.creation

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.shareLatest
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.toHex
import eu.darken.capod.common.uix.ViewModel4
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
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

private data class ProfileEditorState(
    val name: String = "",
    val selectedModel: PodDevice.Model? = null,
    val identityKeyHex: String? = null,
    val encryptionKeyHex: String? = null,
    val selectedDeviceAddress: String? = null,
    val minimumSignalQuality: Float = DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY,
)

@HiltViewModel
class DeviceProfileCreationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    private var profileId: ProfileId? = null
    private var isEditMode: Boolean = false
    private var initialized = false

    private val _currentState = MutableStateFlow(ProfileEditorState())
    private val _initialState = MutableStateFlow(ProfileEditorState())
    private val _nameError = MutableStateFlow<String?>(null)

    val showUnsavedChangesEvent = SingleEventFlow<Unit>()
    val showDeleteConfirmationEvent = SingleEventFlow<Unit>()

    fun initialize(profileId: String?) {
        if (initialized && this.profileId == profileId) return
        initialized = true

        this.profileId = profileId
        this.isEditMode = profileId != null

        if (isEditMode && profileId != null) {
            loadProfile(profileId)
        } else {
            val defaultName = context.getString(R.string.profiles_name_default)
            val defaultState = ProfileEditorState(name = defaultName)
            _initialState.value = defaultState
            _currentState.value = defaultState
        }
    }

    private val bondedDevicesFlow = bluetoothManager.bondedDevices()
        .catch { emit(emptySet()) }
        .map { it.toList() }

    private val hasUnsavedChangesFlow = combine(
        _currentState, _initialState
    ) { current, initial ->
        current != initial
    }

    private val isFormValid = combine(
        _currentState,
        _nameError,
    ) { editorState, nameErr ->
        editorState.name.isNotBlank() &&
            editorState.selectedModel != null &&
            nameErr == null
    }

    val state = combine(
        _currentState,
        _nameError,
        bondedDevicesFlow,
        hasUnsavedChangesFlow,
        isFormValid,
    ) { editorState, nameErr, bonded, hasChanges, formValid ->
        val selectedDevice = editorState.selectedDeviceAddress?.let { address ->
            bonded.find { it.address == address }
        }
        State(
            isEditMode = isEditMode,
            name = editorState.name,
            nameError = nameErr,
            selectedModel = editorState.selectedModel,
            availableModels = PodDevice.Model.entries,
            identityKey = editorState.identityKeyHex?.fromHex(),
            encryptionKey = editorState.encryptionKeyHex?.fromHex(),
            selectedDevice = selectedDevice,
            bondedDevices = bonded,
            minimumSignalQuality = editorState.minimumSignalQuality,
            canSave = formValid && hasChanges,
        )
    }.shareLatest(scope = vmScope)

    data class State(
        val isEditMode: Boolean,
        val name: String,
        val nameError: String?,
        val selectedModel: PodDevice.Model?,
        val availableModels: List<PodDevice.Model>,
        val identityKey: IdentityResolvingKey?,
        val encryptionKey: ProximityEncryptionKey?,
        val selectedDevice: BluetoothDevice2?,
        val bondedDevices: List<BluetoothDevice2>,
        val minimumSignalQuality: Float,
        val canSave: Boolean,
    )

    fun updateName(name: String) {
        _currentState.value = _currentState.value.copy(name = name)
        _nameError.value = if (name.isBlank()) context.getString(R.string.profiles_name_empty_error) else null
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

    fun hasUnsavedChanges(): Boolean = _currentState.value != _initialState.value

    private fun exitScreen() {
        initialized = false
        navUp()
    }

    fun onBackPressed() {
        log(TAG) { "onBackPressed()" }
        if (hasUnsavedChanges()) {
            showUnsavedChangesEvent.tryEmit(Unit)
        } else {
            exitScreen()
        }
    }

    fun saveProfile() {
        log(TAG) { "saveProfile()" }
        val editorState = _currentState.value
        val name = editorState.name.trim()

        if (name.isBlank()) {
            _nameError.value = context.getString(R.string.profiles_name_empty_error)
            return
        }

        if (editorState.selectedModel == null) {
            log(TAG) { "No model selected" }
            return
        }

        launch {
            try {
                val profile = AppleDeviceProfile(
                    id = if (isEditMode) profileId!! else UUID.randomUUID().toString(),
                    label = name,
                    model = editorState.selectedModel,
                    minimumSignalQuality = editorState.minimumSignalQuality,
                    identityKey = editorState.identityKeyHex?.fromHex(),
                    encryptionKey = editorState.encryptionKeyHex?.fromHex(),
                    address = editorState.selectedDeviceAddress,
                )

                if (isEditMode) {
                    deviceProfilesRepo.updateProfile(profile)
                    log(TAG) { "Profile updated: $profile" }
                } else {
                    deviceProfilesRepo.addProfile(profile)
                    log(TAG) { "Profile created: $profile" }
                }
                exitScreen()
            } catch (e: Exception) {
                log(TAG) { "Failed to save profile: $e" }
                errorEvents.emitBlocking(e)
            }
        }
    }

    fun requestDeleteProfile() {
        log(TAG) { "requestDeleteProfile()" }
        showDeleteConfirmationEvent.tryEmit(Unit)
    }

    fun deleteProfile() {
        val id = profileId
        if (isEditMode && id != null) {
            launch {
                try {
                    deviceProfilesRepo.removeProfile(id)
                    log(TAG) { "Profile deleted: $id" }
                    exitScreen()
                } catch (e: Exception) {
                    log(TAG) { "Failed to delete profile: $e" }
                    errorEvents.emitBlocking(e)
                }
            }
        }
    }

    fun discardChanges() {
        log(TAG) { "discardChanges()" }
        exitScreen()
    }

    fun openKeyGuide() {
        webpageTool.open("https://github.com/d4rken-org/capod/wiki/airpod-Keys")
    }

    private fun loadProfile(profileId: String) {
        launch {
            try {
                val profiles = deviceProfilesRepo.currentProfiles()
                val profile = profiles.find { it.id == profileId }
                if (profile != null) {
                    val loadedState = ProfileEditorState(
                        name = profile.label,
                        selectedModel = profile.model,
                        identityKeyHex = (profile as? AppleDeviceProfile)?.identityKey?.toHex(),
                        encryptionKeyHex = (profile as? AppleDeviceProfile)?.encryptionKey?.toHex(),
                        selectedDeviceAddress = (profile as? AppleDeviceProfile)?.address,
                        minimumSignalQuality = profile.minimumSignalQuality ?: DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY,
                    )
                    _initialState.value = loadedState
                    _currentState.value = loadedState
                    log(TAG) { "Profile loaded: ${profile.label}" }
                } else {
                    log(TAG) { "Profile not found: $profileId" }
                    errorEvents.emitBlocking(IllegalArgumentException("Profile not found"))
                }
            } catch (e: Exception) {
                log(TAG) { "Failed to load profile: $e" }
                errorEvents.emitBlocking(e)
            }
        }
    }

    companion object {
        private val TAG = logTag("DeviceProfileCreation", "ViewModel")
    }
}
