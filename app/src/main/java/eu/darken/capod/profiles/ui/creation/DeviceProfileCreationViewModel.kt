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
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.toHex
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityEncryptionKey
import eu.darken.capod.profiles.core.AddressAlreadyClaimedException
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.ProfileId
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

private data class ProfileEditorState(
    val name: String = "",
    val selectedModel: PodModel? = null,
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
    private val _profileIdFlow = MutableStateFlow<ProfileId?>(null)

    val showUnsavedChangesEvent = SingleEventFlow<Unit>()
    val showDeleteConfirmationEvent = SingleEventFlow<Unit>()

    fun initialize(profileId: String?) {
        if (initialized && this.profileId == profileId) return
        initialized = true

        this.profileId = profileId
        this._profileIdFlow.value = profileId
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

    data class BondedDeviceItem(
        val device: BluetoothDevice2,
        val claimedByProfile: String?,
    )

    private val bondedDeviceItemsFlow = combine(
        bluetoothManager.bondedDevices().catch { emit(emptySet()) },
        deviceProfilesRepo.profiles,
        _profileIdFlow,
    ) { bonded, profiles, currentProfileId ->
        val claimedAddresses = profiles
            .filter { it.id != currentProfileId }
            .mapNotNull { profile -> profile.address?.let { it.uppercase() to profile.label } }
            .toMap()

        bonded
            .map { device ->
                BondedDeviceItem(
                    device = device,
                    claimedByProfile = claimedAddresses[device.address.uppercase()],
                )
            }
            .sortedWith(compareBy({ it.claimedByProfile != null }, { it.device.name ?: "" }, { it.device.address }))
    }

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
        bondedDeviceItemsFlow,
        hasUnsavedChangesFlow,
        isFormValid,
    ) { editorState, nameErr, bondedItems, hasChanges, formValid ->
        val selectedDevice = editorState.selectedDeviceAddress?.let { address ->
            bondedItems.find { it.device.address.equals(address, ignoreCase = true) }?.device
        }
        State(
            isEditMode = isEditMode,
            name = editorState.name,
            nameError = nameErr,
            selectedModel = editorState.selectedModel,
            availableModels = PodModel.entries,
            identityKey = editorState.identityKeyHex?.fromHex(),
            encryptionKey = editorState.encryptionKeyHex?.fromHex(),
            selectedDevice = selectedDevice,
            bondedDeviceItems = bondedItems,
            minimumSignalQuality = editorState.minimumSignalQuality,
            canSave = formValid && hasChanges,
        )
    }.asLiveState()

    data class State(
        val isEditMode: Boolean,
        val name: String,
        val nameError: String?,
        val selectedModel: PodModel?,
        val availableModels: List<PodModel>,
        val identityKey: IdentityResolvingKey?,
        val encryptionKey: ProximityEncryptionKey?,
        val selectedDevice: BluetoothDevice2?,
        val bondedDeviceItems: List<BondedDeviceItem>,
        val minimumSignalQuality: Float,
        val canSave: Boolean,
    )

    fun updateName(name: String) {
        _currentState.value = _currentState.value.copy(name = name)
        _nameError.value = if (name.isBlank()) context.getString(R.string.profiles_name_empty_error) else null
    }

    fun updateModel(model: PodModel) {
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
                if (isEditMode) {
                    // Read-modify-write so reaction toggles (and any other fields this editor
                    // doesn't know about) survive a rename or key-refresh.
                    deviceProfilesRepo.updateAppleProfile(profileId!!) { existing ->
                        existing.copy(
                            label = name,
                            model = editorState.selectedModel,
                            minimumSignalQuality = editorState.minimumSignalQuality,
                            identityKey = editorState.identityKeyHex?.fromHex(),
                            encryptionKey = editorState.encryptionKeyHex?.fromHex(),
                            address = editorState.selectedDeviceAddress,
                        )
                    }
                    log(TAG) { "Profile updated: $profileId" }
                } else {
                    val profile = AppleDeviceProfile(
                        id = UUID.randomUUID().toString(),
                        label = name,
                        model = editorState.selectedModel,
                        minimumSignalQuality = editorState.minimumSignalQuality,
                        identityKey = editorState.identityKeyHex?.fromHex(),
                        encryptionKey = editorState.encryptionKeyHex?.fromHex(),
                        address = editorState.selectedDeviceAddress,
                    )
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
